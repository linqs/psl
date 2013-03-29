package edu.umd.cs.psl.util.datasplitter.splitstep;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;

import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class PredicateRoundRobinSplitStep extends PredicateSplitStep {

	public PredicateRoundRobinSplitStep(StandardPredicate target, int numFolds,
			int groupBy) {
		super(target, numFolds, groupBy);
	}

	@Override
	protected void insertIntoPartitions(Collection<Set <GroundAtom>> groups, List<Inserter> inserters, Random random) {
		// insert nodes into partitions
		int j = 0;
		for (Set<GroundAtom> group : groups) {
			for (GroundAtom atom : group)
				inserters.get(j % numFolds).insertValue(atom.getValue(), (Object []) atom.getArguments());
			j++;
		}
	}

}
