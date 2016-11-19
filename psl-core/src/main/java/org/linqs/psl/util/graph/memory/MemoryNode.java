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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.commons.collections15.multimap.MultiHashMap;
import org.linqs.psl.util.graph.Edge;
import org.linqs.psl.util.graph.Node;
import org.linqs.psl.util.graph.Property;
import org.linqs.psl.util.graph.Relationship;

public class MemoryNode implements Node {
	
	final MemoryGraph graph;
	final long uid;
	final MultiHashMap<Integer, MemoryProperty> properties;
	final MultiHashMap<Integer, MemoryRelationship> relationships;
	
	MemoryNode(MemoryGraph g) {
		graph = g;
		uid = g.getUID();
		properties = new MultiHashMap<Integer, MemoryProperty>(1);
		relationships = new MultiHashMap<Integer, MemoryRelationship>(1);
	}

	@Override
	public Property createProperty(String type, Object attribute) {
		Integer pt = graph.getPropertyType(type);
		if (pt != null) {
			Class<?> clazz = graph.getPropertyClass(pt);
			if (clazz.isInstance(attribute)) {
				MemoryProperty p = new MemoryProperty(graph, pt, this, attribute);
				properties.put(pt, p);
				graph.notifyPropertyCreated(this, p);
				return p;
			}
			else
				throw new IllegalArgumentException("Attribute is not of the correct type.");
		}
		else
			throw new IllegalArgumentException("Unknown property type.");
	}

	@Override
	public Relationship createRelationship(String type, Node n) {
		Integer rt = graph.getRelationshipType(type);
		if (rt != null) {
			if (n instanceof MemoryNode) {
				MemoryNode mn = (MemoryNode) n;
				if (graph.equals(mn.graph)) {
					MemoryRelationship r = new MemoryRelationship(graph, rt, this, mn);
					relationships.put(rt, r);
					mn.relationships.put(rt, r);
					return r;
				}
				else
					throw new IllegalArgumentException("Nodes do not belong to same graph.");
			}
			else
				throw new IllegalArgumentException("Nodes do not belong to same graph.");
		}
		else
			throw new IllegalArgumentException("Unknown relationship type.");
	}

	@Override
	public Object getAttribute(String type) {
		Collection<MemoryProperty> properties = getProperties(type);
		if (properties.size() == 0)
			return null;
		else if (properties.size() == 1)
			return properties.iterator().next().getAttribute();
		else
			throw new IllegalArgumentException("Node contains multiple properties of the specified type.");
	}

	@Override
	public <O> O getAttribute(String type, Class<O> c) {
		return c.cast(getAttribute(type));
	}

	@Override
	public Iterator<? extends Edge> getEdgeIterator() {
		return getEdges().iterator();
	}

	@Override
	public Collection<? extends Edge> getEdges() {
		Collection<MemoryEdge> edges = new HashSet<MemoryEdge>(getNoEdges());
		edges.addAll(getProperties());
		edges.addAll(getRelationships());
		return edges;
	}

	@Override
	public int getNoEdges() {
		return getNoProperties() + getNoRelationships();
	}

	@Override
	public int getNoProperties() {
		return properties.totalSize();
	}

	@Override
	public int getNoRelationships() {
		return relationships.totalSize();
	}

	@Override
	public Iterator<MemoryProperty> getPropertyIterator() {
		return getProperties().iterator();
	}

	@Override
	public Collection<MemoryProperty> getProperties() {
		return new ArrayList<MemoryProperty>(properties.values());
	}

	@Override
	public Iterator<MemoryProperty> getPropertyIterator(String type) {
		return getProperties(type).iterator();
	}

	@Override
	public Collection<MemoryProperty> getProperties(String type) {
		Collection<MemoryProperty> p = properties.get(graph.getPropertyType(type));
		return (p != null) ? new ArrayList<MemoryProperty>(p) : new ArrayList<MemoryProperty>(0);
	}

	@Override
	public Iterator<MemoryRelationship> getRelationshipIterator() {
		return getRelationships().iterator();
	}

	@Override
	public Collection<MemoryRelationship> getRelationships() {
		return new ArrayList<MemoryRelationship>(relationships.values());
	}

	@Override
	public Iterator<MemoryRelationship> getRelationshipIterator(String type) {
		return getRelationships(type).iterator();
	}

	@Override
	public Collection<MemoryRelationship> getRelationships(String type) {
		Collection<MemoryRelationship> r = relationships.get(graph.getRelationshipType(type));
		return (r != null) ? new ArrayList<MemoryRelationship>(r) : new ArrayList<MemoryRelationship>(0);
	}
	
	void notifyPropertyDeleted(MemoryProperty p) {
		properties.remove(graph.getPropertyType(p.getPropertyType()), p);
	}
	
	void notifyRelationshipDeleted(MemoryRelationship r) {
		relationships.remove(graph.getRelationshipType(r.getRelationshipType()), r);
	}

	@Override
	public void delete() {
		for (MemoryProperty p : getProperties())
			p.delete();
		for (MemoryRelationship r : getRelationships())
			r.delete();
		graph.notifyNodeDeleted(this);
	}

}
