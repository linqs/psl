/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.umd.cs.psl.database.RDBMS;

import edu.umd.cs.psl.database.UniqueID;


public class RDBMSUniqueStringID implements UniqueID {

	private final String id;
	
	public RDBMSUniqueStringID(String _id) {
		id = _id;
	}
	
	public String getID() {
		return id;
	}
	
	@Override
	public Object getDBID() {
		return id;
	}

	@Override
	public String getName() {
		return id;
	}
	
	@Override
	public int hashCode() {
		return id.hashCode();
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		return id.equals(((RDBMSUniqueStringID)oth).id);  
	}

	@Override
	public int compareTo(UniqueID o) {
		assert o instanceof RDBMSUniqueStringID;
		return id.compareTo(((RDBMSUniqueStringID)o).id);
	}
}
