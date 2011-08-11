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
package edu.umd.cs.psl.model.argument.type;

import com.google.common.base.Preconditions;

/**
 * An implementation of {@link ArgumentType} specifically for entities.
 * 
 * @author
 *
 */
public class EntityArgumentType implements ArgumentType {

	private final String name;
	
	public EntityArgumentType(String name) {
		Preconditions.checkArgument(name!=null && name.length()>0, "Illegal type name provided");
		this.name=name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isAttribute() {
		return false;
	}

	@Override
	public boolean isEntity() {
		return true;
	}

	@Override
	public boolean isSubTypeOf(ArgumentType t) {
		return this.equals(t);
	}
	
	@Override
	public boolean equals(Object oth) {
		if (this==oth) return true;
		else if (!getClass().isInstance(oth)) return false;
		return name.equals(((EntityArgumentType)oth).name);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	
}
