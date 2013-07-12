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

import java.util.*;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;


public class Entity<ET extends EntityType, RT extends RelationType> extends HasAttributes {

	private final int id;
	private final ET type;
	
	
	private final SetMultimap<RT,Relation<ET,RT>> relations;
	
	Entity(int _id, ET _type) {
		super(_type.hasAttributes());
		id = _id;
		type = _type;
		relations = HashMultimap.create();
	}

	public int getId() {
		return id;
	}

	public ET getType() {
		return type;
	}
	
	public int getDegree() {
		return relations.size();
	}
	
	public boolean addRelation(Relation<ET,RT> rel) {
		return relations.put(rel.getType(), rel);
	}
	
	public Set<Relation<ET,RT>> getRelations(RT relType) {
		return relations.get(relType);
	}
	
	public Iterable<Relation<ET,RT>> getRelations(RT relType, final Subgraph<ET,RT> subgraph) {
		return Iterables.filter(relations.get(relType), new Predicate<Relation<ET,RT>>() {
			@Override
			public boolean apply(Relation<ET, RT> rel) {
				return subgraph.containsRelation(rel);
			}
			
		});
	}
		
	public Collection<Relation<ET,RT>> getAllRelations() {
		return relations.values();
	}
	
	public Iterable<Relation<ET,RT>> getAllRelations(final Subgraph<ET,RT> subgraph) {
		return Iterables.filter(relations.values(), new Predicate<Relation<ET,RT>>() {
			@Override
			public boolean apply(Relation<ET, RT> rel) {
				return subgraph.containsRelation(rel);
			}
			
		});
	}
	
	public boolean hasType(ET _type) {
		return type.equals(_type);
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(id).append(type).toHashCode();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object oth) {
		if (this==oth) return true;
		if (oth==null || !getClass().isInstance(oth)) return false;
		Entity<ET,RT> e = (Entity<ET,RT>)oth;
		return id==e.id && type.equals(e.type);
	}
	
}
