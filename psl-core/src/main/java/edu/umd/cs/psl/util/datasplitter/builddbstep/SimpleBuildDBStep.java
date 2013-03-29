/**
 * 
 */
package edu.umd.cs.psl.util.datasplitter.builddbstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * @author jay
 *
 */
public class SimpleBuildDBStep implements BuildDBStep {

	private Set<StandardPredicate> toClose;
	public SimpleBuildDBStep(Set<StandardPredicate> toClose){
		this.toClose = toClose;
	}
	
	@Override
	public List<DBDefinition> getDatabaseDefinitions(Database inputDB,
			List<Collection<Partition>> partitionList) {
		List<DBDefinition> dbDefs = new ArrayList();
		for(Collection<Partition> pL : partitionList){
			Partition wrPartition = inputDB.getDataStore().getNextPartition();
			dbDefs.add(new DBDefinition(wrPartition, toClose, pL.toArray()));
		}
		return dbDefs;
	}
	
}
