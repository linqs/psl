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
package edu.umd.cs.psl.model.argument;

import java.util.Set;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;

/**
 * An interface for aggregates of entities.
 * 
 * NOTE: PSL does not yet support this functionality. This class lays the groundwork for a future release.
 * 
 * @author
 *
 */
public interface EntitySet extends GroundTerm {

	public void excludeEntity(Entity e);
	
	public void includeEntity(Entity e);
	
	public int getCardinality();
	
	public Set<Entity> getEntities(DatabaseAtomStoreQuery db);
	
}
