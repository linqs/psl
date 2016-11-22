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
package org.linqs.psl.model.term;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;

/**
 * A unique identifier for a distinct entity.
 * <p>
 * {@link DataStore DataStores} and {@link Database Databases} use UniqueIDs
 * to identify distinct entities for performance reasons. They should be used
 * instead of {@link Attribute Attributes} when the {@link Constant} is expected
 * to appear in multiple {@link GroundAtom GroundAtoms}.
 * <p>
 * For example, people in a social network should probably be represented as
 * UniqueIDs, but their properties, such as names or ages, should probably
 * be Attributes.
 * 
 * @see DataStore#getUniqueID(Object)
 * @see Database#getUniqueID(Object)
 */
public interface UniqueID extends Constant {

	/**
	 * Returns a human-friendly String representation of this UniqueID.
	 * <p>
	 * Although it should be unique, this is not required.
	 * 
	 * @return a human-friendly identifier
	 */
	@Override
	public String toString();
	
	/**
	 * @return the unique identifier used by a {@link DataStore} implementation
	 */
	public Object getInternalID();
	
}
