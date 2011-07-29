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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import edu.umd.cs.psl.optimizer.conic.program.graph.Node;
import edu.umd.cs.psl.optimizer.conic.program.graph.Traversal;
import edu.umd.cs.psl.optimizer.conic.program.graph.TraversalEvaluator;
import edu.umd.umiacs.dogma.diskgraph.core.Relationship;
import edu.umd.umiacs.dogma.diskgraph.traversal.AbstractTraversal;

public class COSINodeTraversal extends AbstractTraversal<COSINodeTraversal> implements
		Traversal {
	private COSIGraph graph;
	private TraversalEvaluatorProxy eval;
	
	COSINodeTraversal(COSIGraph g) {
		setParent(this);
		graph = g;
	}

	@Override
	public COSINodeTraversal addRelationshipType(String t) {
		addRelationshipType(graph.getGraphDatabase().startTransaction().getRelationshipType(t));
		return this;
	}

	@Override
	public COSINodeTraversal setEvaluator(TraversalEvaluator e) {
		eval = new TraversalEvaluatorProxy(e);
		return this;
	}

	@Override
	public Iterator<Node> traverse(Node seed) {
		if (seed instanceof COSINode) {
			Set<edu.umd.umiacs.dogma.diskgraph.core.Node> seeds
				= new HashSet<edu.umd.umiacs.dogma.diskgraph.core.Node>();
			seeds.add(((COSINode) seed).node);
			return new IteratorProxy<Node>(graph, traverse(seeds, eval));	
		}
		else
			throw new IllegalArgumentException("The node does not belong to the same " +
					"graph as this traversal.");
	}
	
	private class TraversalEvaluatorProxy implements edu.umd.umiacs.dogma.diskgraph.traversal.TraversalEvaluator {
		private TraversalEvaluator eval;
		
		private TraversalEvaluatorProxy(TraversalEvaluator e) {
			eval = e;
		}
		
		@Override
		public boolean nextNode(edu.umd.umiacs.dogma.diskgraph.core.Node n) {
			return eval.nextNode(new COSINode(graph, n));
		}

		@Override
		public void nextRelationship(Relationship r) {
			eval.nextRelationship(new COSIRelationship(graph, r));
		}
	}

}
