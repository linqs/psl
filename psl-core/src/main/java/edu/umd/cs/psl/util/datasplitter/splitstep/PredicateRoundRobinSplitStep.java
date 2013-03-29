package edu.umd.cs.psl.util.datasplitter.splitstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.util.datasplitter.SplitStep;

public class PredicateRoundRobinSplitStep implements SplitStep {
	private StandardPredicate target;
	private int numFolds;
	private int groupBy;
	private Map<GroundTerm, Set<GroundAtom>> groups;
	
	public PredicateRoundRobinSplitStep(StandardPredicate target, int numFolds, int groupBy) {
		this.target = target;
		this.numFolds = numFolds;
		this.groupBy = groupBy;
		groups = new HashMap<GroundTerm, Set<GroundAtom>>();
	}
	
	@Override
	public List<Collection<Partition>> getSplits(Database inputDB, Random random) {
		
		List<Collection<Partition>> splits = new ArrayList<Collection<Partition>>();
		
		Set<GroundAtom> allAtoms = Queries.getAllAtoms(inputDB, target);
		
		for (GroundAtom atom : allAtoms) {
			GroundTerm key = atom.getArguments()[groupBy];
			if (groups.get(key) == null) {
				groups.put(key, new TreeSet<GroundAtom>());
			}
			groups.get(key).add(atom);
		}
		
		List<Partition> allPartitions = new ArrayList<Partition>();
		List<Inserter> inserters = new ArrayList<Inserter>();
		for (int i = 0; i < numFolds; i++) {
			Partition nextPartition =new Partition(i+10000); 
			allPartitions.add(nextPartition);
			// allPartitions.add(inputDB.getDataStore().getNextPartition());
			inserters.add(inputDB.getDataStore().getInserter(target, nextPartition));
			
		}
		
		// start to do the round robin
		int j = 0;
		for (GroundTerm node : groups.keySet()) {
			for (GroundAtom atom : groups.get(node))
				inserters.get(j % numFolds).insertValue(atom.getValue(), (Object []) atom.getArguments());
			j++;
		}
		
		
		for (int i = 0; i < numFolds; i++) {
			Set<Partition> partitions = new TreeSet<Partition>();
			for (j = 0; j < numFolds; j++) 
				if (j != i)
					partitions.add(allPartitions.get(j));
			splits.add(partitions);
		}
		
		return null;
	}

}
