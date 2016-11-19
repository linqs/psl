/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.util.graph.memory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.linqs.psl.util.graph.Graph;
import org.linqs.psl.util.graph.Node;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class MemoryGraph implements Graph {
	
	final private Map<String, Map<Object, Set<Node>>> index;
	
	final private Set<Node> nodes;
	
	final private BiMap<String, Integer> propertyTypes;
	final private BiMap<String, Integer> relationshipTypes;
	
	final private Map<Integer, Class<?>> propertyClasses;
	
	private long uidCounter;
	
	public MemoryGraph() {
		index = new HashMap<String, Map<Object,Set<Node>>>();
		nodes = new HashSet<Node>();
		propertyTypes = HashBiMap.create();
		relationshipTypes = HashBiMap.create();
		propertyClasses = new HashMap<Integer, Class<?>>();
		uidCounter = 0;
	}

	@Override
	public Node createNode() {
		Node node = new MemoryNode(this);
		nodes.add(node);
		return node;
	}

	@Override
	public void createPropertyType(String name, Class<?> type) {
		Integer uid = propertyTypes.get(name);
		if (uid == null) {
			uid = (int) getUID();
			propertyTypes.put(name, uid);
			propertyClasses.put(uid, type);
		}
		else if (!propertyClasses.get(uid).equals(type))
			throw new IllegalArgumentException("Property type already exists with different data type: " + propertyClasses.get(uid));
	}

	@Override
	public void createRelationshipType(String name) {
		if (relationshipTypes.get(name) == null) {
			Integer uid = (int) getUID();
			relationshipTypes.put(name, uid);
		}
	}

	@Override
	public Iterable<? extends Node> getNodeSnapshot() {
		return new HashSet<Node>(nodes);
	}

	@Override
	public Set<Node> getNodeSnapshotByAttribute(String propertyType, Object attribute) {
		Integer pt = propertyTypes.get(propertyType);
		if (pt != null) {
			if (propertyClasses.get(pt).equals(attribute.getClass())) {
				if (Boolean.class.isInstance(attribute) || Enum.class.isInstance(attribute)) {
					Map<Object, Set<Node>> propertyIndex = index.get(propertyType);
					Set<Node> nodes = null;
			
					if (propertyIndex != null && propertyIndex.get(attribute) != null)
						nodes = new HashSet<Node>(propertyIndex.get(attribute));
					
					if (nodes == null)
						nodes = new HashSet<Node>();
					
					return nodes;
				}
				else
					throw new IllegalArgumentException("getNodesByAttribute only " +
							"allowed for boolean and enum attributes.");
			}
			else
				throw new IllegalArgumentException("Attribute "
						+ attribute+ " is not a valid value for property " + propertyType);
		}
		else
			throw new IllegalArgumentException("Unknown property type.");
	}

	long getUID() {
		return uidCounter++;
	}
	
	Integer getPropertyType(String type) {
		Integer pt = propertyTypes.get(type); 
		if (pt != null)
			return pt;
		else
			throw new IllegalArgumentException("Unknown property type.");
	}
	
	Integer getRelationshipType(String type) {
		Integer rt = relationshipTypes.get(type); 
		if (rt != null)
			return rt;
		else
			throw new IllegalArgumentException("Unknown relationship type.");
	}
	
	String getPropertyTypeName(Integer pt) {
		return propertyTypes.inverse().get(pt);
	}
	
	String getRelationshipTypeName(Integer rt) {
		return relationshipTypes.inverse().get(rt);
	}
	
	Class<?> getPropertyClass(Integer pt) {
		return propertyClasses.get(pt);
	}
	
	void notifyNodeDeleted(MemoryNode n) {
		nodes.remove(n);
	}
	
	void notifyPropertyCreated(MemoryNode n, MemoryProperty p) {
		Object attribute = p.getAttribute();
		if (attribute instanceof Enum<?> || attribute instanceof Boolean) {
			Map<Object, Set<Node>> propertyIndex = index.get(p.getPropertyType());
			Set<Node> nodes;
			if (propertyIndex == null) {
				propertyIndex = new HashMap<Object, Set<Node>>();
				index.put(p.getPropertyType(), propertyIndex);
			}
			nodes = propertyIndex.get(attribute);
			
			if (nodes == null) {
				nodes = new HashSet<Node>();
				propertyIndex.put(attribute, nodes);
			}
			
			nodes.add(n);
		}
	}
	
	void notifyPropertyDeleted(MemoryNode n, MemoryProperty p) {
		Collection<MemoryProperty> properties = n.getProperties(p.getPropertyType());
		int count = 0;
		for (MemoryProperty prop : properties)
			if (prop.getAttribute().equals(p.getAttribute()))
				count++;
		
		if (count == 1) {
			Map<Object, Set<Node>> propertyIndex = index.get(p.getPropertyType());
			Set<Node> nodes;
			
			if (propertyIndex != null) {
				nodes = propertyIndex.get(p.getAttribute());
				if (nodes != null) {
					nodes.remove(n);
				}
			}
		}
	}
}
