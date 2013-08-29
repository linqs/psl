package edu.umd.cs.psl.util.datasplitter.splitstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class QueryUniformSplitStep implements SplitStep {
	private Iterable<QueryAtom> targets;
	private int numFolds;
	private Variable groupBy;

	/**
	 * Constructs a SplitStep that defines all instances with a set of QueryAtoms and groups by variables
	 * @param targets Collection of queries defining the instances. Each returned atom from the query is an instance
	 * @param numFolds number of folds to split into
	 * @param groupBy variable that all instances should be grouped by
	 */
	public QueryUniformSplitStep(Iterable<QueryAtom> targets, int numFolds, Variable groupBy) {
		this.targets= targets;
		this.numFolds = numFolds;
		this.groupBy = groupBy;
	}

	@Override
	public List<Collection<Partition>> getSplits(Database inputDB, Random random) {
		Map<GroundTerm, Set<GroundAtom>> groupMap = new HashMap<GroundTerm, Set<GroundAtom>>();		
		Collection<Set<GroundAtom>> groups;

		List<Collection<Partition>> splits = new ArrayList<Collection<Partition>>();

		for (QueryAtom query : targets) {
			DatabaseQuery dbQuery = new DatabaseQuery(query);
			ResultList results = inputDB.executeQuery(dbQuery);
			Predicate predicate = query.getPredicate();
			int groupIndex = dbQuery.getVariableIndex(groupBy);

			for (int i = 0; i < results.size(); i++) {
				GroundAtom atom = inputDB.getAtom(predicate, results.get(i));

				// group atoms
				GroundTerm key = atom.getArguments()[groupIndex];
				if (groupMap.get(key) == null) {
					groupMap.put(key, new TreeSet<GroundAtom>());
				}
				groupMap.get(key).add(atom);
			}
		}
		groups = groupMap.values();


		List<Partition> allPartitions = new ArrayList<Partition>();
		for (int i = 0; i < numFolds; i++) {
			Partition nextPartition = inputDB.getDataStore().getNewPartition(); 
			allPartitions.add(nextPartition);
		}

		Map<Predicate, List<Inserter>> inserters = new HashMap<Predicate, List<Inserter>>();
		for (QueryAtom query : targets) {
			if (inserters.containsKey(query.getPredicate()))
				continue;

			List<Inserter> predicateInserters = new ArrayList<Inserter>(numFolds);
			for (int i = 0; i < numFolds; i++) 
				predicateInserters.add(inputDB.getDataStore().getInserter((StandardPredicate) query.getPredicate(), allPartitions.get(i)));
			inserters.put(query.getPredicate(), predicateInserters);	
		}

		insertIntoPartitions(groups, inserters, random); 

		for (int i = 0; i < numFolds; i++) {
			Set<Partition> partitions = new TreeSet<Partition>();
			for (int j = 0; j < numFolds; j++) 
				if (j != i)
					partitions.add(allPartitions.get(j));
			splits.add(partitions);
		}

		return splits;
	}


	private void insertIntoPartitions(Collection<Set<GroundAtom>> groups, 
			Map<Predicate, List<Inserter>> inserters, Random random) {

		ArrayList<Set<GroundAtom>> groupList = new ArrayList<Set<GroundAtom>>(groups.size());
		groupList.addAll(groups);
		Collections.shuffle(groupList, random);

		int j = 0;
		for (Set<GroundAtom> group : groupList) {
			for (GroundAtom atom : group)
				inserters.get((StandardPredicate) atom.getPredicate()).get(j % numFolds).insertValue(
						atom.getValue(), (Object []) atom.getArguments());
			j++;
		}
	}

}
