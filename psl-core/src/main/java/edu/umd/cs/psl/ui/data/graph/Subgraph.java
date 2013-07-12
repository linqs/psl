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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

public class Subgraph<ET extends EntityType, RT extends RelationType> {

	private final SetMultimap<ET,Entity<ET,RT>> entities;
	private final SetMultimap<RT,Relation<ET,RT>> relations;
	
	public Subgraph() {
		entities = HashMultimap.create();
		relations = HashMultimap.create();
	}
	
	void addEntity(Entity<ET,RT> entity) {
		entities.put(entity.getType(), entity);
	}
	
	public boolean containsEntity(Entity<ET,RT> entity) {
		return entities.containsEntry(entity.getType(), entity);
	}
	
	public int size() {
		return entities.size();
	}
	
	void addRelation(Relation<ET,RT> rel) {
		relations.put(rel.getType(), rel);
	}
	
	boolean containsRelation(Relation<ET,RT> rel) {
		return relations.containsEntry(rel.getType(), rel);
	}
	
	public Set<Entity<ET,RT>> getEntities(ET type) {
		return entities.get(type);
	}
	
	public Set<Relation<ET,RT>> getRelations(RT type) {
		return relations.get(type);
	}
	
	Collection<Entity<ET,RT>> getAllEntities() {
		return entities.values();
	}
	
}
