package edu.umd.cs.psl.util.datasplitter.splitstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.util.database.Queries;

public class PredicateUniformSplitStep implements SplitStep {
	private static final int NO_GROUP = -1;

	private StandardPredicate target;
	private int numFolds;
	private int groupBy;

	/**
	 * Constructor for splitting by GroundTerms
	 * @param target Predicate whose groundings to split
	 * @param numFolds
	 * @param groupBy index of node argument in groundings
	 */
	public PredicateUniformSplitStep(StandardPredicate target, int numFolds, int groupBy) {
		this.target = target;
		this.numFolds = numFolds;
		this.groupBy = groupBy;
	}

	/**
	 * Constructor for splitting by GroundAtoms. Does not group atoms, instead treats each 
	 * atom as its own group
	 * @param target Predicate whose groundings to split on
	 * @param numFolds 
	 */
	public PredicateUniformSplitStep(StandardPredicate target, int numFolds) {
		this(target, numFolds, NO_GROUP);
	}

	@Override
	public List<Collection<Partition>> getSplits(Database inputDB, Random random) {
		Map<GroundTerm, Set<GroundAtom>> groupMap = new HashMap<GroundTerm, Set<GroundAtom>>();
		Collection<Set<GroundAtom>> groups;
		List<Collection<Partition>> splits = new ArrayList<Collection<Partition>>();

		Set<GroundAtom> allAtoms = Queries.getAllAtoms(inputDB, target);

		if (groupBy == NO_GROUP) {
			groups = new ArrayList<Set<GroundAtom>>(allAtoms.size());
			for (GroundAtom atom : allAtoms) {
				Set<GroundAtom> group = new HashSet<GroundAtom>();
				group.add(atom);
				groups.add(group);
			}
		} else {
			// group atoms
			for (GroundAtom atom : allAtoms) {
				GroundTerm key = atom.getArguments()[groupBy];
				if (groupMap.get(key) == null) {
					groupMap.put(key, new TreeSet<GroundAtom>());
				}
				groupMap.get(key).add(atom);
			}
			groups = groupMap.values();
		}		

		List<Partition> allPartitions = new ArrayList<Partition>();
		List<Inserter> inserters = new ArrayList<Inserter>();
		for (int i = 0; i < numFolds; i++) {
			Partition nextPartition = inputDB.getDataStore().getNewPartition(); 
			allPartitions.add(nextPartition);
			inserters.add(inputDB.getDataStore().getInserter(target, nextPartition));
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
			List<Inserter> inserters, Random random) {
		
		ArrayList<Set<GroundAtom>> groupList = new ArrayList<Set<GroundAtom>>(groups.size());
		groupList.addAll(groups);
		Collections.shuffle(groupList, random);
		
		int j = 0;
		for (Set<GroundAtom> group : groupList) {
			for (GroundAtom atom : group)
				inserters.get(j % numFolds).insertValue(atom.getValue(), (Object []) atom.getArguments());
			j++;
		}
	}

}
