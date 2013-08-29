package edu.umd.cs.psl.util.datasplitter.builddbstep;

import java.util.List;
import java.util.Collection;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;

public interface BuildDBStep {
	List<DBDefinition> getDatabaseDefinitions(Database inputDB, List<Collection<Partition>> partitionList );
}
