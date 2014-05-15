package edu.umd.cs.psl.database.rdbms.streaming;

import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.Stream;

public class RDBMSStream implements Stream {
		private final int id;
		private final String name;
		private final RDBMSStreamingDataStore ds;
		private final HashSet<Partition> partitions;
		//private final String tablePrefix;
		
		public RDBMSStream(int id, String name, RDBMSStreamingDataStore ds) {
			Preconditions.checkArgument(id>=0);
			this.id=id;
			this.name = name;
			this.ds = ds;
			this.partitions = new HashSet<Partition>();
		}
		
		
		public int getID() {
			return id;
		}
		
		public String getName(){
			return name;
		}
		
		@Override
		public String toString() {
			return "Stream["+name+"]";
		}
		
		@Override
		public int hashCode() {
			return id+211;
		}
		
		@Override
		public boolean equals(Object oth) {
			if (oth==this) return true;
			if (oth==null || !(oth instanceof RDBMSStream)) return false;
			return id == ((RDBMSStream)oth).id;  
		}

		public boolean addPartition(Partition p){
			boolean success = ds.addPartitionToStream(this, p);
			if(success) { partitions.add(p); }
			return true;
		}
		
		public boolean addPartitions(Set<Partition> pSet){
			boolean success = true;
			for (Partition p : pSet){
				success &= ds.addPartitionToStream(this, p);
				if(!success){ break; }
				else { partitions.add(p); }
			}
			return success;
		}
		
		@Override
		public Set<Partition> getPartitions() {
			return ds.getPartitions(this);
		}
		
		public void close(){
			ds.closeStream(this);
		}
		
		
	}
