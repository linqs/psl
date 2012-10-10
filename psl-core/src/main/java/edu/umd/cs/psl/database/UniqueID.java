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
package edu.umd.cs.psl.database;

import edu.umd.cs.psl.model.argument.Entity;

/**
 * A unique identifier suitable for identifying {@link Entity Entities} in a
 * {@link DataStore}.
 */
public interface UniqueID extends Comparable<UniqueID> {

	/**
	 * Returns a human-friendly String representation of this UniqueID.
	 * <p>
	 * Although it should be unique, this is not required.
	 * 
	 * @return a human-friendly identifier
	 */
	public String getName();
	
	/**
	 * @return the unique identifier used by a {@link DataStore} implementation
	 */
	public Object getInternalID();
	
	@Override
	public int hashCode();
	
	@Override
	public boolean equals(Object oth);
	
}
