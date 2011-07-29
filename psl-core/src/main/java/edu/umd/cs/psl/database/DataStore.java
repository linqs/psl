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

import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.loading.Updater;
import edu.umd.cs.psl.model.predicate.Predicate;

public interface DataStore {

	public void registerPredicate(Predicate predicate, List<String> argnames, PredicateDBType type);
	
	public void registerPredicate(Predicate predicate, List<String> argnames, PredicateDBType type, DataFormat[] formats);
	
	public Database getDatabase(Partition writeID, Set<Predicate> toclose, Partition... partitionIDs);
	
	public Database getDatabase(Partition writeID, Partition... partitionIDs);

	public Inserter getInserter(Predicate predicate, Partition partID);
	
	public Updater getUpdater(Predicate predicate, Partition partID);
	
	public int deletePartition(Partition partID);
	
	public void close();
	
	
}
