package edu.umd.cs.psl.database.rdbms;

import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.Stream;

public class RDBMSStream implements Stream {
		private final int id;
		private final String name;
		private final HashSet<Partition> partitions;
		//private final String tablePrefix;
		
		public RDBMSStream(int id, String name) {
			Preconditions.checkArgument(id>=0);
			this.id=id;
			this.name = name;
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

		public void addPartition(Partition p){
			partitions.add(p);
		}
		
		public void addPartitions(Set<Partition> pSet){
			partitions.addAll(pSet);
		}
		@Override
		public Set<Partition> getPartitions() {
			return partitions;
		}
		
	}
