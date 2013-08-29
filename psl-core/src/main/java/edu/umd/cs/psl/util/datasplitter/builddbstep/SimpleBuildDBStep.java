/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.util.datasplitter.builddbstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * A simple class for the {@link BuildDBStep} interface that creates a list of {@link DBDefinition}s
 * with closed predicates specified by the user and a read partition to each collection of partitions in the passed list. 
 **/

public class SimpleBuildDBStep implements BuildDBStep {

	private Set<StandardPredicate> toClose;
	public SimpleBuildDBStep(Set<StandardPredicate> toClose){
		this.toClose = toClose;
	}
	
	@Override
	/**
	 * Creates a list of {@link DBDefinition} objects. 
	 * Each DBDefinition has a new write partition returned from the {@link DataStore} associated with the passed database.
	 * The closed predicates in the DBDefinition are taken from those specified in the constructor to the class
	 * Each DBDefinition has a read partition that corresponds to an element of the passed List of Partition Collections.
	 * 
	 **/
	public List<DBDefinition> getDatabaseDefinitions(Database inputDB,
			List<Collection<Partition>> partitionList) {
		List<DBDefinition> dbDefs = new ArrayList<DBDefinition>();
		for(Collection<Partition> pL : partitionList){
			Partition wrPartition = inputDB.getDataStore().getNewPartition();
			dbDefs.add(new DBDefinition(wrPartition, toClose, (Partition[]) pL.toArray()));
		}
		return dbDefs;
	}
	
}
