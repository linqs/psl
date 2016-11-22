/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Partition;
import org.linqs.psl.model.atom.GroundAtom;

import com.google.common.base.Preconditions;

/**
 * Identifier for a subset of {@link GroundAtom GroundAtoms} in a {@link DataStore}.
 */
public class RDBMSPartition implements Partition {

	private final int id;
	private final String name;
	
	/**
	 * Sole constructor.
	 * 
	 * @param id  non-negative identifier
	 */
	public RDBMSPartition(int id, String name) {
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
		return "Partition["+name+"]";
	}
	
	@Override
	public int hashCode() {
		return id+211;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(oth instanceof RDBMSPartition)) return false;
		return id == ((RDBMSPartition)oth).id;  
	}
	
}
