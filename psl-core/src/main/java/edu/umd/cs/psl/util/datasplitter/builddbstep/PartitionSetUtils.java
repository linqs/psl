package edu.umd.cs.psl.util.datasplitter.builddbstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.database.Partition;

public class PartitionSetUtils {
	
	public static List<Partition> invertPartitions(Collection<Partition> partitions, Set<Partition> allPartitions){
		List<Partition> invertedPartition = new ArrayList<Partition>();
		for(Partition p: allPartitions){
			if(!partitions.contains(p)){
				invertedPartition.add(p);
			}
		}
		return invertedPartition;
	}
		
	
	public static Set<Partition> collectSets(List<Collection<Partition>> partitionList){
		Set<Partition> allPartitions = new HashSet<Partition>();
		for(Collection<Partition> pL : partitionList){
			allPartitions.addAll(pL);
		}
		return allPartitions;
	}
}
