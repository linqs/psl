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

import edu.umd.cs.psl.database.UniqueID;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;

public class ArgumentFactory {

	private final Map<ArgumentType,Map<UniqueID,Entity>> cache;
	
	public ArgumentFactory() {
		cache = new HashMap<ArgumentType,Map<UniqueID,Entity>>();
	}
	
//	public Entity getEntity(UniqueID id) {
//		return getEntity(id,ArgumentTypes.Entity);
//	}

	
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
	
	public static Attribute getAttribute(String s) {
		return new TextAttribute(s);
	}
	
	public static Attribute getAttribute(int i) {
		return new NumberAttribute(i);
	}

	public static Attribute getAttribute(double d) {
		return new NumberAttribute(d);
	}
	
	public static Attribute getAttribute(Number n) {
		return new NumberAttribute(n.doubleValue());
	}
	
	public static Entity getNonCachedEntity(UniqueID id, ArgumentType t) {
		return new Entity(id,t);
	}
	
}
