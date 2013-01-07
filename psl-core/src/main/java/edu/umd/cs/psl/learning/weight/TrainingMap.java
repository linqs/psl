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
package edu.umd.cs.psl.learning.weight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * A TrainingMap matches {@link RandomVariableAtom RandomVariableAtoms} in one database
 * to their respective {@link ObservedAtom} in a second database. Any RandomVariableAtoms
 * that do not have a matching ObservedAtom are kept in the set of latent variables.
 * 
 * @author Eric Norris
 */
public class TrainingMap {
	
	/**
	 * The mapping between an atom and its observed truth value.
	 */
	private final Map<RandomVariableAtom, ObservedAtom> trainingMap;
	
	/**
	 * The set of atoms that have no backing of an observed truth value.
	 */
	private final Set<RandomVariableAtom> latentVariables;
	
	/**
	 * Initializes the training map of {@link RandomVariableAtom RandomVariableAtoms}
	 * to {@link ObservedAtom ObservedAtoms}. Any RandomVariableAtom that does not have
	 * a matching ObservedAtom in the second Database are stored in the set of latent
	 * variables.
	 * 
	 * @param rvDB			The database containing the RandomVariableAtoms (any other atom types are ignored)
	 * @param observedDB	The database containing matching ObservedAtoms
	 */
	public TrainingMap(Database rvDB, Database observedDB) {
		// Initialize private variables
		this.trainingMap = new HashMap<RandomVariableAtom, ObservedAtom>();
		this.latentVariables = new HashSet<RandomVariableAtom>();
		
		// Iterate through all of the registered predicates in the RandomVariableAtom database
		for (StandardPredicate predicate : rvDB.getDataStore().getRegisteredPredicates()) {
			// Ignore any closed predicates, they will not return RandomVariableAtoms
			if (rvDB.isClosed(predicate))
				continue;
			
			// Construct the query for this predicate
			Variable vars[] = new Variable[predicate.getArity()];
			for (int i = 0; i < vars.length; i++)
				vars[i] = new Variable(String.valueOf(i));
			Formula queryFormula = new QueryAtom(predicate, vars);
			
			// Execute the query and interpret the results
			ResultList list = rvDB.executeQuery(new DatabaseQuery(queryFormula));
			for (int i = 0; i < list.size(); i ++) {
				// Query the database for this specific atom
				GroundAtom atom = rvDB.getAtom(predicate, list.get(i));
				
				if (atom instanceof RandomVariableAtom) {
					// Now query the other database for this atom's truth value
					GroundAtom otherAtom = observedDB.getAtom(predicate, list.get(i));
					
					if (otherAtom instanceof ObservedAtom)
						trainingMap.put((RandomVariableAtom)atom, (ObservedAtom)otherAtom);
					else
						latentVariables.add((RandomVariableAtom) atom);
				}
			}
		}
	}
	
	/**
	 * Gets the map created by the constructor.
	 * 
	 * @return the training map.
	 */
	public Map<RandomVariableAtom, ObservedAtom> getTrainingMap() {
		return trainingMap;
	}
	
	/**
	 * Gets the set of latent variables created by the constructor.
	 * 
	 * @return the set of latent variables.
	 */
	public Set<RandomVariableAtom> getLatentVariables() {
		return latentVariables;
	}

}
