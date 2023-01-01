/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
public abstract class SearchFringe<T extends Collection<CandidateSearchNode>> {
    private static final Logger log = LoggerFactory.getLogger(SearchFringe.class);

    protected double bestPessimisticCost;
    protected T fringe;

    protected SearchFringe(T fringe) {
        this.fringe = fringe;
        bestPessimisticCost = Double.MAX_VALUE;
    }

    public int size() {
        return fringe.size();
    }

    public void clear() {
        fringe.clear();
        bestPessimisticCost = Double.MAX_VALUE;
    }

    /**
     * Observe the a new pessimistic cost and remember if it is the best.
     */
    public void newPessimisticCost(double pessimisticCost) {
        if (pessimisticCost < bestPessimisticCost) {
            bestPessimisticCost = pessimisticCost;
        }
    }

    /**
     * Add a node to the fringe.
     */
    public void push(CandidateSearchNode node) {
        if (node == null) {
            return;
        }

        pushInternal(node);
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

    public static class BFSSearchFringe extends SearchFringe<LinkedList<CandidateSearchNode>> {
        public BFSSearchFringe() {
            super(new LinkedList<CandidateSearchNode>());
        }

        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            fringe.addLast(node);
            return true;
        }

        @Override
        public CandidateSearchNode pop() {
            return fringe.removeFirst();
        }
    }


    public static class DFSSearchFringe extends SearchFringe<LinkedList<CandidateSearchNode>> {
        public DFSSearchFringe() {
            super(new LinkedList<CandidateSearchNode>());
        }

        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            fringe.addFirst(node);
            return true;
        }

        @Override
        public CandidateSearchNode pop() {
            return fringe.removeFirst();
        }
    }

    public static class UCSSearchFringe extends SearchFringe<PriorityQueue<CandidateSearchNode>> {
        public UCSSearchFringe() {
            // Use a priority queue (with PriorityQueue's default size of 11.
            super(new PriorityQueue<CandidateSearchNode>(11));
        }

        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            fringe.add(node);
            return true;
        }

        @Override
        public CandidateSearchNode pop() {
            return fringe.poll();
        }
    }

    public static class BoundedUCSSearchFringe extends UCSSearchFringe {
        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            if (node.optimisticCost > bestPessimisticCost) {
                return false;
            }

            return super.pushInternal(node);
        }
    }

    public static class BoundedDFSSearchFringe extends DFSSearchFringe {
        @Override
        protected boolean pushInternal(CandidateSearchNode node) {
            if (node.optimisticCost > bestPessimisticCost) {
                return false;
            }

            return super.pushInternal(node);
        }
    }
}
