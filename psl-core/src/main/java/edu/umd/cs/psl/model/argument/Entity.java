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

import edu.umd.cs.psl.database.UniqueID;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;

/**
 * This class represents domain objects which are predicate arguments. Domain Object are identified by their
 * name and type. Domain objects must be created through the static create() methods to ensure
 * that each domain object has only one memory representation (for efficiency reasons).
 * 
 * Note that that we allow the existence of two distinct domain objects with the same name as long as they
 * have different types. 
 * 
 * Domain Objects are created in namespaces to avoid name collisions.
 * @author Matthias Broecheler
 *
 */
public class Entity implements GroundTerm {

	private final UniqueID id;
	private final ArgumentType type;
	
	/**
	 * Constructs an entity given a {@link UniqueID}.
	 * 
	 * @param _id A unique ID
	 */
	Entity(UniqueID _id) {
		this(_id,ArgumentTypes.Entity);
	}
	
	/**
	 * Constructs an entity given a {@link UniqueID} and {@link ArgumentType}.
	 * 
	 * @param _id A unique ID
	 * @param t An argument type
	 */
	Entity(UniqueID _id, ArgumentType t) {
		if (!t.isEntity()) throw new IllegalArgumentException("Type must be compatible with entity, but was given: " + t);
		id = _id;
		type = t;
	}
	
	/**
	 * Returns the name.
	 * 
	 * @return The entity name
	 */
	@Override
	public String toString() {
		return id.getName();
	}
	
	/**
	 * Returns the name.
	 * 
	 * @return The entity name
	 */
	public String getName() {
		return id.getName();
	}
	
	/**
	 * Returns the unique ID.
	 * 
	 * @return The unique ID
	 */
	public UniqueID getID() {
		return id;
	}
	
	/**
	 * Returns TRUE, as an entity is ground.
	 * 
	 * @return TRUE
	 */
	@Override
	public boolean isGround() {
		return true;
	}
	
	/**
	 * Returns the hash code.
	 * 
	 * @return The integer hash code
	 */
	@Override
	public int hashCode() {
		return id.hashCode()*113 + type.hashCode();
	}
	
	/**
	 * Determines equality with another object.
	 * 
	 * @return true if equal; false otherwise 
	 */
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		Entity other = (Entity)oth;
		return id.equals(other.id) && type.equals(other.type);  
	}

	/**
	 * Returns the argument type.
	 * 
	 * @return The argument type
	 */
	@Override
	public ArgumentType getType() {
		return type;
	}
	
	/**
	 * Returns a new entity, given a {@link UniqueID}.
	 * 
	 * @param id A unique ID
	 * @return A new entity
	 */
	public static Entity getEntity(UniqueID id) {
		return new Entity(id);
	}
	
}
