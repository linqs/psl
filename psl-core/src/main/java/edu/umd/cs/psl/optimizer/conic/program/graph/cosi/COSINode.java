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
package edu.umd.cs.psl.optimizer.conic.program.graph.cosi;

import java.util.Iterator;

import edu.umd.cs.psl.optimizer.conic.program.graph.Edge;
import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.cs.psl.optimizer.conic.program.graph.Property;
import edu.umd.cs.psl.optimizer.conic.program.graph.Relationship;
import edu.umd.umiacs.dogma.diskgraph.core.Direction;

public class COSINode implements Node {
	COSIGraph graph;
	edu.umd.umiacs.dogma.diskgraph.core.Node node;
	
	COSINode(COSIGraph g, edu.umd.umiacs.dogma.diskgraph.core.Node n) {
		graph = g;
		node = n;
	}

	@Override
	public Property createProperty(String type, Object attribute) {
		COSIProperty p = new COSIProperty(graph, node.createProperty(type, attribute));
		graph.notifyPropertyCreated(this, p);
		return p;
	}

	@Override
	public Relationship createRelationship(String type, Node n) {
		return new COSIRelationship(graph, node.createRelationship(type, ((COSINode) n).node));
	}

	@Override
	public Object getAttribute(String type) {
		return node.getAttribute(type);
	}

	@Override
	public <O> O getAttribute(String type, Class<O> c) {
		return node.getAttribute(type, c);
	}

	@Override
	public Iterator<Edge> getEdgeIterator() {
		return getEdges().iterator();
	}

	@Override
	public Iterable<Edge> getEdges() {
		return new IterableProxy<Edge>(graph, node.getEdges());
	}

	@Override
	public int getNoEdges() {
		return node.getNoEdges();
	}

	@Override
	public int getNoProperties() {
		return node.getNoProperties();
	}

	@Override
	public int getNoRelationships() {
		return node.getNoRelationships();
	}

	@Override
	public Iterable<Property> getProperties() {
		return new IterableProxy<Property>(graph, node.getProperties());
	}

	@Override
	public Iterable<Property> getProperties(String type) {
		return new IterableProxy<Property>(graph, node.getProperties(type));
	}

	@Override
	public Iterator<Property> getPropertyIterator() {
		return getProperties().iterator();
	}

	@Override
	public Iterator<Property> getPropertyIterator(String type) {
		return getProperties(type).iterator();
	}

	@Override
	public Iterable<Relationship> getRelationships() {
		return new IterableProxy<Relationship>(graph, node.getRelationships(Direction.Both));
	}

	@Override
	public Iterable<Relationship> getRelationships(String type) {
		return new IterableProxy<Relationship>(graph, node.getRelationships(type, Direction.Both));
	}

	@Override
	public Iterator<Relationship> getRelationshipIterator() {
		return getRelationships().iterator();
	}

	@Override
	public Iterator<Relationship> getRelationshipIterator(String type) {
		return getRelationships(type).iterator();
	}

	@Override
	public void delete() {
		node.delete();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o instanceof COSINode)
			return node.equals(((COSINode) o).node);
		else
			return false;
	}
	
	@Override
	public int hashCode() {
		return node.hashCode();
	}
	
	static COSINode wrap(COSIGraph g, edu.umd.umiacs.dogma.diskgraph.core.Node n) {
		if (n instanceof edu.umd.umiacs.dogma.diskgraph.core.Property)
			return new COSIProperty(g, (edu.umd.umiacs.dogma.diskgraph.core.Property) n);
		else if (n instanceof edu.umd.umiacs.dogma.diskgraph.core.Relationship)
			return new COSIRelationship(g, (edu.umd.umiacs.dogma.diskgraph.core.Relationship) n);
		else
			return new COSINode(g, n);
	}
}
