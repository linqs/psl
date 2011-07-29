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
package edu.umd.cs.psl.database.partition;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.Partition;

public class PartitionID implements Partition {

	private final int id;
	
	public PartitionID(int id) {
		Preconditions.checkArgument(id>=0);
		this.id=id;
	}

	@Override
	public int getID() {
		return id;
	}

	@Override
	public String getName() {
		return "Partition["+id+"]";
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	@Override
	public int hashCode() {
		return id+211;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		return id == ((PartitionID)oth).id;  
	}
	
	
}
