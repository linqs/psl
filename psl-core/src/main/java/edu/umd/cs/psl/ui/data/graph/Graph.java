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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

import edu.umd.cs.psl.ui.data.file.util.DelimitedObjectConstructor;
import edu.umd.cs.psl.ui.data.file.util.LoadDelimitedData;

public class Graph<ET extends EntityType, RT extends RelationType> {

	private final Map<ET,Map<Integer,Entity<ET,RT>>> entities;
	
	public Graph() {
		entities = new HashMap<ET,Map<Integer,Entity<ET,RT>>>();
	}
	
	public int getNoEntities(ET type) {
		if (!entities.containsKey(type)) return 0;
		else return entities.get(type).size();
	}
	
	public Iterator<Entity<ET,RT>> getEntities(ET type) {
		if (!entities.containsKey(type)) return Iterators.emptyIterator();
		final Iterator<Entity<ET,RT>> iter = entities.get(type).values().iterator();
		return new Iterator<Entity<ET,RT>>() {

			Entity<ET,RT> current = null;
			
			@Override
			public boolean hasNext() {
				return iter.hasNext();
			}

			@Override
			public Entity<ET, RT> next() {
				current= iter.next();
				return current;
			}

			@Override
			public void remove() {
				if (current.getDegree()>0) throw new IllegalArgumentException("Cannot delete connected entity!");
				else iter.remove();
			}
			
		};
	}
	
	
	public Entity<ET,RT> getEntity(int id, ET type) {
		Map<Integer,Entity<ET,RT>> map = entities.get(type);
		if (map==null) return null;
		Entity<ET,RT> e = map.get(id);
		if (e!=null && !e.hasType(type)) throw new AssertionError("Entity does not have the expected type!");
		return e;
	}
	
	public boolean deleteEntity(Entity<ET,RT> e) {
		if (e.getDegree()>0) throw new IllegalArgumentException("Cannot delete connected entity!");
		Map<Integer,Entity<ET,RT>> map = entities.get(e.getType());
		if (map==null) return false;
		if (!map.containsKey(e.getId())) return false;
		map.remove(e.getId());
		return true;
	}
	
	public Entity<ET,RT> getorCreateEntity(int id, ET type) {
		Entity<ET,RT> e = getEntity(id,type);
		if (e==null) {
			e = createEntity(id,type);
		}
		return e;
	}
	
	public Entity<ET,RT> createEntity(int id, ET type) {
		Map<Integer,Entity<ET,RT>> map = entities.get(type);
		if (map==null) {
			map = new HashMap<Integer,Entity<ET,RT>>();
			entities.put(type, map);
		}
		if (map.containsKey(id)) throw new AssertionError("Entity already exists!");
		Entity<ET,RT> e = new Entity<ET,RT>(id,type);
		map.put(id, e);
		return e;
	}
	
	public void loadEntityAttributes(String file, final ET type, final String[] attNames, final boolean createEntity) {
		loadEntityAttributes(file,type,attNames,DelimitedObjectConstructor.NoFilter,createEntity);
	}
	
	public void loadEntityAttributes(String file, final ET type, final String[] attNames, final DelimitedObjectConstructor.Filter filter, final boolean createEntity) {
		DelimitedObjectConstructor<Object> loader = new DelimitedObjectConstructor<Object>() {
			@Override
			public Object create(String[] data) {
				if (!filter.include(data)) return null;
				
				int id = Integer.parseInt(data[0]);
				Entity<ET,RT> e = getEntity(id,type);
				if (e==null) {
					if (createEntity) e = createEntity(id,type);
					else return null;
				}
				//Load attributes
				for (int a=0;a<attNames.length;a++) {
					if (attNames[a]!=null) {
						e.setAttribute(attNames[a], data[a+1]);
					}
				}
				return null;
			}
			@Override
			public int length() {return attNames.length+1;}
			
		};
		LoadDelimitedData.loadTabData(file, loader);
	}
	
	public void loadRelationship(String file, final RT relType, final ET[] types, final boolean[] createEntity) {
		loadRelationship(file,new String[0],relType,types,createEntity);
	}
	
	public void loadRelationship(String file, final String[] attNames, final RT relType, final ET[] types, final boolean[] createEntity) {
		loadRelationship(file,attNames,relType,types,DelimitedObjectConstructor.NoFilter,createEntity);
	}
	
	public void loadRelationship(String file, final String[] attNames, final RT relType, final ET[] types, final DelimitedObjectConstructor.Filter filter, final boolean[] createEntity) {
		DelimitedObjectConstructor<Object> loader = new DelimitedObjectConstructor<Object>() {
			@Override
			public Object create(String[] data) {
				if (!filter.include(data)) return null;
				List<Entity<ET,RT>> entities = new ArrayList<Entity<ET,RT>>(types.length);
				for (int i=0;i<types.length;i++) {
					ET type = types[i];
					if (type!=null) {
						int id = Integer.parseInt(data[i]);
						Entity<ET,RT> e = getEntity(id,type);
						if (e==null) {
							if (createEntity[i]) e = createEntity(id,type);
							else return null;
						}
						entities.add(e);
					}
				}

				if (entities.size()!=2) throw new AssertionError("Currently, only binary relations are supported!");
				Relation<ET,RT> rel = new BinaryRelation<ET,RT>(relType,entities.get(0),entities.get(1));
				
				//Load attributes
				for (int a=0;a<attNames.length;a++) {
					if (attNames[a]!=null) {
						rel.setAttribute(attNames[a], data[a+types.length]);
					}
				}
				
				//Add relation
				for (Entity<ET,RT> e : entities) {
					e.addRelation(rel);
				}
				return null;
			}
			@Override
			public int length() {
				return types.length+attNames.length;
			}
			
		};
		LoadDelimitedData.loadTabData(file, loader);
	}
	
	public List<Subgraph<ET,RT>> splitGraphRandom(int numsplits, ET splitType) {
		List<Subgraph<ET,RT>> splits = new ArrayList<Subgraph<ET,RT>>(numsplits);
		List<Set<Entity<ET,RT>>> starts = new ArrayList<Set<Entity<ET,RT>>>(numsplits);
		for (int i=0;i<numsplits;i++) {
			starts.add(new HashSet<Entity<ET,RT>>());
		}
		
		if (!entities.containsKey(splitType)) throw new IllegalArgumentException("There are no entities of given type!");
		for (Entity<ET,RT> e : entities.get(splitType).values()) {
			int cont = (int)Math.floor(Math.random()*numsplits);
			starts.get(cont).add(e);
		}
		
		//Grow splits
		for (int i=0;i<numsplits;i++) {
			Set<Entity<ET,RT>> excluded = new HashSet<Entity<ET,RT>>();
			for (int j=0;j<numsplits;j++) {
				if (j!=i) {
					excluded.addAll(starts.get(j));
				}
			}
			splits.add(growSplit(starts.get(i),excluded, new KeepGrowing(),1.0));
		}
		
		return splits;	
	}
	
	
	public List<Subgraph<ET,RT>> splitGraphSnowball(int numsplits, ET splitType, int splitSize, double exploreProbability) {
		List<Subgraph<ET,RT>> splits = new ArrayList<Subgraph<ET,RT>>(numsplits);
		Set<Entity<ET,RT>> remaining = new HashSet<Entity<ET,RT>>();
		Set<Entity<ET,RT>> excluded = new HashSet<Entity<ET,RT>>();
		
		if (!entities.containsKey(splitType)) throw new IllegalArgumentException("There are no entities of given type!");
		remaining.addAll(entities.get(splitType).values());
		
		//Grow splits
		for (int i=0;i<numsplits;i++) {
			Set<Entity<ET,RT>> seed = new HashSet<Entity<ET,RT>>();
			GrowCondition gc = new SizeLimit(splitType,splitSize);
			Subgraph<ET,RT> sample;
			do {
				int pos = (int)Math.floor(Math.random()*remaining.size());
				seed.add(Iterables.get(remaining, pos));
				sample = growSplit(seed,excluded,new SizeLimit(splitType,splitSize),exploreProbability);
			} while (gc.continueGrowing(sample));
			//Update sets
			remaining.removeAll(sample.getEntities(splitType));
			excluded.addAll(sample.getEntities(splitType));
			splits.add(sample);
		}
		
		return splits;	
	}
	
	private Subgraph<ET,RT> growSplit(Set<Entity<ET,RT>> start, Set<Entity<ET,RT>> excluded, GrowCondition growcondition, double exploreProbability) {
		Subgraph<ET,RT> subgraph = new Subgraph<ET,RT>();
		Queue<Entity<ET,RT>> queue = new LinkedList<Entity<ET,RT>>();
		//Initialize
		queue.addAll(start);
		while (!queue.isEmpty() && growcondition.continueGrowing(subgraph)) {
			Entity<ET,RT> entity = queue.poll();
			if (subgraph.containsEntity(entity)) continue; //We have already visited this entity
			subgraph.addEntity(entity);
			for (Relation<ET,RT> relation : entity.getAllRelations()) {
				boolean hasNewEntity = false;
				boolean isExcluded = false;
				for (int i=0;i<relation.getArity();i++) {
					Entity<ET,RT> ngh = relation.get(i);
					if (ngh.equals(entity)) continue;
					if (excluded.contains(ngh)) {
						isExcluded = true;
						break;
					}
					if (!subgraph.containsEntity(ngh)) hasNewEntity = true;
				}
				if (!isExcluded && !hasNewEntity) subgraph.addRelation(relation);
				else if (!isExcluded && hasNewEntity) {
					for (int i=0;i<relation.getArity();i++) {
						Entity<ET,RT> ngh = relation.get(i);
						if (!ngh.equals(entity) && !subgraph.containsEntity(ngh)) {
							if (Math.random()<exploreProbability) queue.add(ngh);
						}
					}
				}
			}
		}
		return subgraph;
	}
	
	private abstract class GrowCondition {
		abstract boolean continueGrowing(Subgraph<ET,RT> subgraph);
	}
	
	private class KeepGrowing extends GrowCondition {
		@Override
		boolean continueGrowing(Subgraph<ET, RT> subgraph) {
			return true;
		}
	}
	
	private class SizeLimit extends GrowCondition {
		private final ET type;
		private final int size;
		public SizeLimit(ET t, int s) { type = t; size = s;}
		@Override
		boolean continueGrowing(Subgraph<ET, RT> subgraph) {
			return subgraph.getEntities(type).size()<size;
		}
	}
}
