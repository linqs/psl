/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.util.datasplitter.closurestep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.util.database.Queries;

/**
 * Performs link closure.
 * 
 * TODO: describe various forms of link closure.
 * 
 * @author blondon
 *
 */
public class LinkClosure implements ClosureStep {
	
	private final Predicate nodePred;
	private final Predicate linkPred;
	private final boolean internal;
	
	public LinkClosure(Predicate nodePred, Predicate linkPred, boolean internal) {
		this.nodePred = nodePred;
		this.linkPred = linkPred;
		this.internal = internal;
		
		/* For now, we're only dealing with edges (i.e., binary predicates), not hyperedges. */
		if (linkPred.getArity() != 2)
			throw new IllegalArgumentException("LinkClosure only works with binary predicates.");
	}
	
	@Override
	public void doClosure(Database inputDB, List<Collection<Partition>> partitionList) {
		/* Perform closure for each partition-set in partitionList. */
		for (Collection<Partition> partSet : partitionList) {
			/* Create a new DB for the given partition. */
			DataStore data = inputDB.getDataStore();
			Partition writePart = data.getNewPartition();
			Partition[] readParts = (Partition[]) partSet.toArray();
			Database writeDB = data.getDatabase(writePart, readParts); 
			for (Partition part : partSet) {
				/* Get all of the atoms associated with the node predicate. */
				Set<GroundAtom> nodes = Queries.getAllAtoms(inputDB, nodePred);
				/* Get all the link atoms involved with the nodes, with 1 or 2 endpoints in this partition. */
				List<GroundAtom> links = new ArrayList<GroundAtom>();
				if (internal) {
					
				}
				else {
					
				}
			}
		}
	}

}
