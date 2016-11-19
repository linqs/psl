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
package org.linqs.psl.util.graph.partition;

import java.util.Collection;
import java.util.List;

import org.linqs.psl.util.graph.Graph;
import org.linqs.psl.util.graph.Node;
import org.linqs.psl.util.graph.weight.RelationshipWeighter;

public interface Partitioner {
	/**
	 * Define the size of the partition, i.e. the number of blocks to produce
	 * 
	 * @param size Size of the partition
	 */
	public void setSize(int size);
	
	/**
	 * Returns the current size of produced partitions
	 * @return Size of partitions
	 */
	public int getSize();
	
	/**
	 * Partition the set of nodes into the specified number of blocks aiming to minimize the edge
	 * cut as specified by the RelationshipWeighter.
	 * 
	 * To specify that an edge should not be cut, its weight should be Double.POSITIVE_INFINITY. In such
	 * cases the method may produce an IllegalArgumentException if no partition could be found that satisfies
	 * the no-cut condition.
	 * 
	 * @param nodes Iterable over a set of nodes. It is assumed, that no node occurs multiple times during iteration.
	 * @param rweight RelationshipWeighter defining the strength of relationships taken into account by the partitioning algorithm.
	 * @return A partition of the nodes
	 */
	public List<List<Node>> partition(Graph g, Iterable<? extends Node> nodes, RelationshipWeighter rweight);
	
	/**
	 * Partition the set of nodes into the specified number of blocks aiming to minimize the edge
	 * cut as specified by the RelationshipWeighter.
	 * 
	 * Compared to {@link #partition(Iterable, RelationshipWeighter)}, this method expects that the user has initialized
	 * the container holding the partition, given the user more choice over what containers to use. Also, it returns
	 * the edge cut of the produced partition.
	 * 
	 * To specify that an edge should not be cut, its weight should be Double.POSITIVE_INFINITY. In such
	 * cases the method may produce an IllegalArgumentException if no partition could be found that satisfies
	 * the no-cut condition.
	 * 
	 * @param nodes Iterable over a set of nodes. It is assumed, that no node occurs multiple times during iteration.
	 * @param rweight RelationshipWeighter defining the strength of relationships taken into account by the partitioning algorithm.
	 * @param partition Initialized partition container
	 * @return The edge cut of the produced partition
	 */
	public double partition(Graph g, Iterable<? extends Node> nodes, RelationshipWeighter rweight, 
			List<? extends Collection<Node>> partition);
}
