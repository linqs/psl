/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.grounding.collective;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * Defines the strategy for exploring the qurey candidate generation space.
 */
public abstract class SearchFringe {
    private static final Logger log = LoggerFactory.getLogger(SearchFringe.class);

    protected PriorityQueue<CandidateSearchNode> savedOrderedNodes;
    protected Collection<CandidateSearchNode> fringe;

    protected SearchFringe(Collection<CandidateSearchNode> fringe) {
        savedOrderedNodes = new PriorityQueue<CandidateSearchNode>();
        this.fringe = fringe;
    }

    public int size() {
        return fringe.size();
    }

    public int savedSize() {
        return savedOrderedNodes.size();
    }

    public void clear() {
        savedOrderedNodes.clear();
        fringe.clear();
    }

    /**
     * Add a node to the fringe.
     * The node will be checked if it is the best one right away because the search through
     * the candidate space may be time/cost bounded,
     * and we want to check for the best node as soon as we pay the estimation cost (SQL EXPLAIN).
     */
    public void push(CandidateSearchNode node) {
        if (node == null) {
            return;
        }

        if (pushInternal(node)) {
            savedOrderedNodes.add(node);
        }
    }

    public CandidateSearchNode getBestNode() {
        return savedOrderedNodes.peek();
    }

    public CandidateSearchNode popBestNode() {
        return savedOrderedNodes.poll();
    }

    /**
     * Actually add the node to the fringe.
     * @return true is the node was accepted, false otherwise.
     */
    protected abstract boolean pushInternal(CandidateSearchNode node);

    /**
     * Get the next node for exploration.
     */
    public abstract CandidateSearchNode pop();

    public static class BFSSearchFringe extends SearchFringe {
        public BFSSearchFringe() {
            super(new LinkedList<CandidateSearchNode>());
        }

        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            ((LinkedList<CandidateSearchNode>)fringe).addLast(node);
            return true;
        }

        @Override
        public CandidateSearchNode pop() {
            return ((LinkedList<CandidateSearchNode>)fringe).removeFirst();
        }
    }


    public static class DFSSearchFringe extends SearchFringe {
        public DFSSearchFringe() {
            super(new LinkedList<CandidateSearchNode>());
        }

        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            ((LinkedList<CandidateSearchNode>)fringe).addFirst(node);
            return true;
        }

        @Override
        public CandidateSearchNode pop() {
            return ((LinkedList<CandidateSearchNode>)fringe).removeFirst();
        }
    }

    public static class UCSSearchFringe extends SearchFringe {
        public UCSSearchFringe() {
            // Use a priority queue (with PriorityQueue's default size of 11.
            super(new PriorityQueue<CandidateSearchNode>(11));
        }

        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            ((PriorityQueue<CandidateSearchNode>)fringe).add(node);
            return true;
        }

        @Override
        public CandidateSearchNode pop() {
            return ((PriorityQueue<CandidateSearchNode>)fringe).poll();
        }
    }

    public static class BoundedSearchFringe extends UCSSearchFringe {
        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            if (savedOrderedNodes.size() > 0 && node.optimisticCost > getBestNode().pessimisticCost) {
                return false;
            }

            return super.pushInternal(node);
        }
    }
}
