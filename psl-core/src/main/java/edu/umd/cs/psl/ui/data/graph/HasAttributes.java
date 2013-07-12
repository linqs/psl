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
package edu.umd.cs.psl.ui.data.graph;

import java.util.HashMap;
import java.util.Map;

public abstract class HasAttributes {

	private final Map<String,Object> attributes;
	
	public HasAttributes(boolean hasAttributes) {
		if (hasAttributes) attributes = new HashMap<String,Object>();
		else attributes = null;
	}

	public void setAttribute(String attname, Object att) {
		if (attributes.containsKey(attname)) throw new AssertionError("Attribute has already been set!");
		else attributes.put(attname, att);
	}
	
	public<O> O getAttribute(String attname, Class<O> clazz) {
		return clazz.cast(getAttribute(attname));
	}
	
	public Object getAttribute(String attname) {
		Object obj = attributes.get(attname);
		if (obj==null) throw new AssertionError("Attribute has not been set!");
		return obj;
	}

	
}
