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

public abstract class Relation<ET extends EntityType, RT extends RelationType> extends HasAttributes{

	protected final RT type;
	
	
	public Relation(RT _type) {
		super(_type.hasAttributes());
		type = _type;
	}

	public RT getType() {
		return type;
	}
		
	public boolean isSoft() {
		return type.isSoft();
	}


	
	public abstract Entity<ET,RT> get(int pos);
	
	public abstract int getArity();
	
	@Override
	public int hashCode() {
		return type.hashCode()*119;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object oth) {
		if (this==oth) return true;
		if (oth==null || !getClass().isInstance(oth)) return false;
		Relation<ET,RT> r = (Relation<ET,RT>)oth;
		return type.equals(r.type);
	}

	public boolean hasType(RT reltype) {
		return type.equals(reltype);
	}
	
}
