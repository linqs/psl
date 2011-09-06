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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.psl.optimizer.conic.program.graph.Graph;
import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.umiacs.dogma.diskgraph.configuration.InMemoryGraphDatabase;
import edu.umd.umiacs.dogma.diskgraph.core.EdgeCategory;
import edu.umd.umiacs.dogma.diskgraph.core.GraphDatabase;

public class COSIGraph implements Graph {
	protected GraphDatabase graph;
	protected Map<String, Map<Object, Set<Node>>> index;
	
	public COSIGraph() {
		graph = InMemoryGraphDatabase.open();
		index = new HashMap<String, Map<Object,Set<Node>>>();
	}

	@Override
	public Node createNode() {
		return COSINode.wrap(this, graph.startTransaction().createNode());
	}

	@Override
	public void createPropertyType(String name, Class<?> type) {
		graph.startTransaction().createEdgeType().category(EdgeCategory.Simple).withName(name).dataType(type).makePropertyType();
	}

	@Override
	public void createRelationshipType(String name) {
		graph.startTransaction().createEdgeType().withName(name).makeRelationshipType();
	}

	@Override
	public Set<Node> getNodesByAttribute(String propertyType, Object attribute) {
		Class<?> dataType = graph.startTransaction().getPropertyType(propertyType).getDataType();
		if (dataType.equals(attribute.getClass())) {
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
	
	public COSINodeTraversal getTraversal() {
		return new COSINodeTraversal(this);
	}
	
	public COSIPartitioning getPartitioning() {
		return new COSIPartitioning(this);
	}
	
	GraphDatabase getGraphDatabase() {
		return graph;
	}
	
	void notifyPropertyCreated(COSINode n, COSIProperty p) {
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
	
	void notifyPropertyDeleted(COSINode n, COSIProperty p) {
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
