package edu.umd.cs.psl.util.datasplitter.builddbstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.util.database.Queries;

/**
 * This class uses a Map of {@link QueryAtom} objects and corresponding
 * {@link Variable} substitution map (similar to {@link DatabasePopulator}) to
 * generate the atoms corresponding to inference targets. Instead of using the
 * {@link GroundTerm} arguments to the QueryAtom, ground terms associated with
 * each GroundAtom are generated from the supplied {@link Partition}
 * collections.
 * 
 * @author jay
 * 
 */
public class QueryAtomsBuildDBStep implements BuildDBStep {

	protected Set<StandardPredicate> toClose = null;
	protected Map<QueryAtom, Map<Variable, Set<GroundTerm>>> queryAtoms = null;

	/**
	 * 
	 * @param toClose
	 *            the set of predicates that will be closed for the experimental
	 *            task
	 * @param queryAtoms
	 *            a map, where each entry is a {@link QueryAtom} and
	 *            substitution map, corresponding to {@link Variable}s in the
	 *            QueryAtom. Non-variable terms will be ignored.
	 */
	public QueryAtomsBuildDBStep(Set<StandardPredicate> toClose,
			Map<QueryAtom, Map<Variable, Set<GroundTerm>>> queryAtoms) {
		this.toClose = toClose;
		this.queryAtoms = queryAtoms;

	}

	/*
	 * Step 0: Get inverse partition set and generate a Database for each Step
	 * 1: Deal with queryAtoms - Iterate over Map, for each QueryAtom:
	 * 
	 * 
	 * /* A helper function for populating all predicates associated with a
	 * QueryAtom into the Database. - Extract predicate - Identify Variable
	 * positions - Query for all instances of that Predicate in the inverse
	 * partition database - For each instance: - create a QueryAtom - substitute
	 * non-Variable terms in qAtom with GroundTerms from the query GroundAtoms -
	 * Use DatabasePopulator to populate variables in QueryAtom
	 */
	protected void addQueryAtomGroundings(Database inverseDB,
			Database outputDB, QueryAtom qAtom,
			Map<Variable, Set<GroundTerm>> substitutions) {
		DatabasePopulator dbPop = new DatabasePopulator(outputDB);

		Predicate qPredicate = qAtom.getPredicate();
		Term[] qTerms = qAtom.getArguments();
		int[] varArgs = new int[qTerms.length];
		for (int i = 0; i < qTerms.length; i++) {
			varArgs[i] = 0;
			if (qTerms[i] instanceof Variable) {
				varArgs[i] = 1;
			}
		}
		for (GroundAtom grAtom : Queries.getAllAtoms(inverseDB, qPredicate)) {
			for (int i = 0; i < qTerms.length; i++) {
				if (varArgs[i] == 0) {
					qTerms[i] = grAtom.getArguments()[i];
				}
				dbPop.populate(new QueryAtom(qPredicate, qTerms), substitutions);
			}
		}
	}

	@Override
	/**
	 * Produces a list of {@link DBDefinition} objects, twice the length of the input list of Partition-Collections.
	 * The elements of the list alternate between "Task" DBDefinitions for running an experiment and "Truth" DBDefinitions for evaluation 
	 * The data included in these DBDefinitions will correspond to the arguments supplied in the constructor. 
	 * 
	 * The Truth database will be the inverse of a Partition-collection relative to all Partitions found in the List of Partition-Collections
	 * 
	 * The Task database will include the Predicates specified via QueryAtoms passed to the constructor. 
	 * Variable arguments to each QueryAtom will be substituted using the substitution map provided to the constructor, while the ground terms in 
	 * the QueryAtom will correspond to GroundAtoms found in the Truth Database.  Thus, targets in the "Task" DBDefinition will correspond to all 
	 * instances of each QueryAtom predicate in the truth data, with non-ground (Variable) terms substituted using the provided Map.
	 */
	public List<DBDefinition> getDatabaseDefinitions(Database inputDB,
			List<Collection<Partition>> partitionList) {

		//generate union of all Partitions
		Set<Partition> allPartitions = PartitionSetUtils
				.collectSets(partitionList);
		List<DBDefinition> retList = new ArrayList<DBDefinition>();
		DataStore dStore = inputDB.getDataStore();
		for (Collection<Partition> pL : partitionList) {
			//generate the inverse of this Collection of Partitions, which we assume will contain all "truth" data
			List<Partition> inversePartitions = PartitionSetUtils.invertPartitions(pL, allPartitions);

			//prepare databases for the truth data and the task data 
			Partition truthWrPartition = dStore.getNewPartition();
			Partition taskWrPartition = dStore.getNewPartition();
			Database truthDB = dStore.getDatabase(truthWrPartition,
					(Partition[]) inversePartitions.toArray());
			Database taskDB = dStore.getDatabase(taskWrPartition, toClose,
					(Partition[]) pL.toArray());
			
			//iterate over all QueryAtom, substitution pairs specified to the constructor, performing query/substitution
			for (Entry<QueryAtom, Map<Variable, Set<GroundTerm>>> e : queryAtoms.entrySet()) {
				addQueryAtomGroundings(truthDB, taskDB, e.getKey(),
						e.getValue());
			}

			//close DBs
			truthDB.close();
			taskDB.close();

			//create DBDefinitions for Task/Truth and add to return object
			retList.add(new DBDefinition(taskWrPartition, toClose,
					(Partition[]) pL.toArray()));
			retList.add(new DBDefinition(truthWrPartition, null,
					(Partition[]) inversePartitions.toArray()));
		}

		return retList;

	}
}
