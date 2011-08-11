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

import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.psl.database.UniqueID;
import edu.umd.cs.psl.model.argument.type.ArgumentType;

/**
 * Factory for arguments to a predicate.
 * Entities are created as singletons. 
 *
 * @author 
 *
 */
public class ArgumentFactory {

	private final Map<ArgumentType,Map<UniqueID,Entity>> cache;
	
	/**
	 * Default constructor
	 */
	public ArgumentFactory() {
		cache = new HashMap<ArgumentType,Map<UniqueID,Entity>>();
	}

	/**
	 * Returns an {@link Entity}, given a {@link UniqueID} and {@link ArgumentType}.
	 * 
	 * @param id A unique ID
	 * @param t An argument type
	 * @returns An entity with the given unique ID and argument type
	 */
	public Entity getEntity(UniqueID id, ArgumentType t) {
		Map<UniqueID,Entity> map = cache.get(t);
		if (map==null) {
			map = new HashMap<UniqueID,Entity>();
			cache.put(t, map);
		}
		Entity e = map.get(id);
		if (e==null) {
			e = getNonCachedEntity(id,t);
			map.put(id,e);
		}
		return e;
	}
	
	/**
	 * Returns a {@link TextAttribute}, given a string.
	 * 
	 * @param s A string
	 * @return A textual attribute
	 */
	public static Attribute getAttribute(String s) {
		return new TextAttribute(s);
	}
	
	/**
	 * Returns a {@link NumberAttribute}, given an integer value.
	 * 
	 * @param i An integer value
	 * @return A numerical attribute
	 */
	public static Attribute getAttribute(int i) {
		return new NumberAttribute(i);
	}

	/**
	 * Returns a {@link NumberAttribute}, given a double.
	 * 
	 * @param d A double
	 * @return A numerical attribute
	 */
	public static Attribute getAttribute(double d) {
		return new NumberAttribute(d);
	}
	
	/**
	 * Returns a {@link NumberAttribute}, given a {@link Number}.
	 * 
	 * @param n A number
	 * @return A numerical attribute
	 */
	public static Attribute getAttribute(Number n) {
		return new NumberAttribute(n.doubleValue());
	}
	
	/**
	 * Returns a non-cached {@link Entity}, given a {@link UniqueID} and {@link ArgumentType}.
	 * 
	 * @param id A unique ID
	 * @param t An argument type
	 * @returns An entity with the given unique ID and argument type
	 */
	public static Entity getNonCachedEntity(UniqueID id, ArgumentType t) {
		return new Entity(id,t);
	}
	
}
