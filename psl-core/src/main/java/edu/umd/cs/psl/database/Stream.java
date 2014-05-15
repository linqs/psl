package edu.umd.cs.psl.database;

import java.util.Set;

public interface Stream {
	public int getID();
	public String getName();
	public Set<Partition> getPartitions();
	public boolean addPartition(Partition p);
	public boolean addPartitions(Set<Partition> pSet);
	public String toString();
}
