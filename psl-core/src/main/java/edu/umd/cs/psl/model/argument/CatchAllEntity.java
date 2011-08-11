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

import java.util.*;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.argument.type.ArgumentType;

/**
 * A catch-all entity is an aggregate entity, representing multiple entities.
 * 
 * Certain entities may be less important than others. For such entities, one may wish to aggregate them
 * under one predicate argument. Though this functionality is not yet supported in PSL, this class
 * lays the groundwork for a future release.
 * 
 * @author 
 *
 */
public class CatchAllEntity implements EntitySet {

	private Set<Entity> excludedEntities;
	private final ArgumentType type;
	private int cardinality;
	
	public CatchAllEntity(ArgumentType t, int card) {
		Preconditions.checkArgument(t.isEntity());
		Preconditions.checkArgument(card>=0);
		cardinality=card;
		type = t;
		excludedEntities = new HashSet<Entity>();
	}
	
	@Override
	public void excludeEntity(Entity e) {
		Preconditions.checkArgument(e.getType().isSubTypeOf(type));
		if (!excludedEntities.add(e)) throw new IllegalArgumentException("Entity has already been excluded!");
		cardinality--;
		assert cardinality>=0;
	}
	
	@Override
	public void includeEntity(Entity e) {
		if (excludedEntities.remove(e))
			cardinality++;
		assert cardinality>=0;
	}
	
	@Override
	public int getCardinality() {
		return cardinality;
	}
	
	@Override
	public ArgumentType getType() {
		return type;
	}

	@Override
	public boolean isGround() {
		return true;
	}

	@Override
	public Set<Entity> getEntities(DatabaseAtomStoreQuery db) {
		Set<Entity> result = db.getEntities(type);
		result.removeAll(excludedEntities);
		return result;
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("*-{");
		for (Entity e : excludedEntities) {
			s.append(e).append(" ");
		}
		s.append("}");
		return s.toString();
	}
	
	
	@Override
	public int hashCode() {
		return excludedEntities.hashCode()+7889*type.hashCode();
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		CatchAllEntity other = (CatchAllEntity)oth;
		return type.equals(other.type) && excludedEntities.equals(other.excludedEntities);
	}
	
}
