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
package edu.umd.cs.psl.optimizer.conic.program.graph;

import java.util.Iterator;

public interface Node extends Entity {
	
	public Property createProperty(String type, Object attribute);
	
	public Relationship createRelationship(String type, Node n);
	 
	public Object getAttribute(String type);
	 
	public <O> O getAttribute(String type, Class<O> c);

	public Iterator<Edge> getEdgeIterator();
	 
	public Iterable<Edge> getEdges();

	public int getNoEdges();
	 
	public int getNoProperties();
	 
	public int getNoRelationships();
	 
	public Iterator<Property> getPropertyIterator();

	public Iterable<Property> getProperties();
	 
	public Iterator<Property> getPropertyIterator(String type);
	 
	public Iterable<Property> getProperties(String type);
	 
	public Iterator<Relationship> getRelationshipIterator();

	public Iterable<Relationship> getRelationships();
	 
	public Iterator<Relationship> getRelationshipIterator(String type);
	 
	public Iterable<Relationship> getRelationships(String type);
}
