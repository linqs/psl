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
import java.util.List;

import org.linqs.psl.util.graph.Node;
import org.linqs.psl.util.graph.Property;
import org.linqs.psl.util.graph.Relationship;

public class MemoryProperty extends MemoryEdge implements Property {
	
	final private Object attribute;
	final private Integer propertyType;

	MemoryProperty(MemoryGraph g, Integer pt, MemoryNode n, Object a) {
		super(g, n);
		attribute = a;
		propertyType = pt;
	}
	
	@Override
	public Property createProperty(String type, Object attribute) {	
		throw new UnsupportedOperationException("Properties cannot have properties.");
	}

	@Override
	public Relationship createRelationship(String type, Node n) {
		throw new UnsupportedOperationException("Properties cannot participate in relationships.");
	}

	@Override
	public boolean isProperty() {
		return true;
	}

	@Override
	public boolean isRelationship() {
		return false;
	}

	@Override
	public Object getAttribute() {
		return attribute;
	}

	@Override
	public <O> O getAttribute(Class<O> clazz) {
		return clazz.cast(attribute);
	}

	@Override
	public String getPropertyType() {
		return graph.getPropertyTypeName(propertyType);
	}

	@Override
	public Collection<? extends Node> getNodes() {
		List<MemoryNode> nodes = new ArrayList<MemoryNode>(1);
		nodes.add(startNode);
		return nodes;
	}

	@Override
	public boolean isIncidentOn(Node n) {
		return startNode.equals(n);
	}
	
	@Override
	public void delete() {
		graph.notifyPropertyDeleted(startNode, this);
		startNode.notifyPropertyDeleted(this);
		super.delete();
	}

}
