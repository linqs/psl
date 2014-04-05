/**
 * 
 */
package edu.umd.cs.psl.application.activation;

import java.util.Set;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.IngestModel;
import edu.umd.cs.psl.model.LinkModel;

/**
 * @author jay
 *
 */
public interface Activator {
	void ingest(DataStore ds, Partition p, IngestModel m);

	void link(DataStore ds, Set<Partition> inPartitions, LinkModel m);

	GroundKernelStore ground(DataStore ds, Partition groundPartition);



}
