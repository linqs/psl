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
package org.linqs.psl.util.graph.partition.hierarchical;

import java.util.concurrent.atomic.AtomicInteger;

import org.linqs.psl.util.graph.Node;

class SuperNode implements Comparable<SuperNode> {

	private static final int initialChildrenSize = 4;
	private static AtomicInteger idCounter = new AtomicInteger(0);
	
	
	private final int id;
	private final Node supernode;
	private double weight;
	private int noChildren;
	
	private Node[] children;
	
	SuperNode(Node supern) {
		supernode = supern;
		id = idCounter.incrementAndGet();
		weight = 0.0;
		noChildren=0;
		children = new Node[initialChildrenSize];
	}
	
//	void setSuperNode(Node n) {
//		supernode = n;
//	}
	
	@Override
	public int hashCode() {
		return id*47 + 111;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (this==oth) return true;
		else if (!getClass().isInstance(oth)) return false;
		return id==((SuperNode)oth).id;
	}
	
	Node getRepresentationNode() {
		return supernode;
	}
	
	double getWeight() {
		return weight;
	}
	
	int getNoChildren() {
		return noChildren;
	}
	
	void addChild(Node n, double w) {
		weight+=w;
		if (noChildren==children.length) { //Expand
			Node[] newchildren = new Node[children.length*2];
			System.arraycopy(children, 0, newchildren, 0, noChildren);
			children=newchildren;
		}
		children[noChildren]=n;
		noChildren++;
	}
	
	Node getChild(int pos) {
		if (pos<0 || pos>=noChildren) throw new ArrayIndexOutOfBoundsException("Out of bounds: " + pos);
		return children[pos];
	}

	@Override
	public int compareTo(SuperNode o) {
		return id-o.id;
	}
	
}
