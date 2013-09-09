package edu.umd.cs.psl.database.rdbms;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.Stream;

public class RDBMSStream implements Stream{
		private final int id;
		private final String name;
		//private final String tablePrefix;
		
		public RDBMSStream(int id, String name) {
			Preconditions.checkArgument(id>=0);
			this.id=id;
			this.name = name;
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
		
	}
