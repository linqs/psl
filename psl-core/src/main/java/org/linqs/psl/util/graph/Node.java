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
package org.linqs.psl.util.graph;

import java.util.Iterator;

public interface Node {
	
	public Property createProperty(String type, Object attribute);
	
	public Relationship createRelationship(String type, Node n);
	 
	public Object getAttribute(String type);
	 
	public <O> O getAttribute(String type, Class<O> c);

	public Iterator<? extends Edge> getEdgeIterator();
	 
	public Iterable<? extends Edge> getEdges();

	public int getNoEdges();
	 
	public int getNoProperties();
	 
	public int getNoRelationships();
	 
	public Iterator<? extends Property> getPropertyIterator();

	public Iterable<? extends Property> getProperties();
	 
	public Iterator<? extends Property> getPropertyIterator(String type);
	 
	public Iterable<? extends Property> getProperties(String type);
	 
	public Iterator<? extends Relationship> getRelationshipIterator();

	public Iterable<? extends Relationship> getRelationships();
	 
	public Iterator<? extends Relationship> getRelationshipIterator(String type);
	 
	public Iterable<? extends Relationship> getRelationships(String type);
	
	public void delete();
}
