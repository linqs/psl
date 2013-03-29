package edu.umd.cs.psl.util.datasplitter.splitstep;

import java.util.Collection;
import java.util.List;
import java.util.Random;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;

/**
 * 
 * @author Bert Huang bert@cs.umd.edu
 *
 */
public interface SplitStep {

	List<Collection<Partition>> getSplits(Database inputDB, Random random);
	
}
