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
package edu.umd.cs.psl.database;

import java.util.Map;
import java.util.Set;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * A DatabasePopulator can easily commit a large number of
 * {@link RandomVariableAtom RandomVariableAtoms}
 * to a database.
 * 
 * @author Eric Norris
 */
public class DatabasePopulator {
	
	/**
	 * The database specified by the constructor
	 */
	private final Database db;
	
	/**
	 * Constructs a DatabasePopulator using the specified {@link Database}.
	 * @param db	the Database to populate
	 */
	public DatabasePopulator(Database db) {
		this.db = db;
	}
	
	/**
	 * Substitutes the {@link Variable Variables} from the substitution map into a
	 * {@link QueryAtom} and commits the resulting RandomVariableAtoms into the Database provided
	 * to the constructor.
	 * 
	 * @param qAtom			the QueryAtom to perform substitution on
	 * @param substitutions		the map of Variables to their possible GroundTerm substitutions
	 */
	public void populate(QueryAtom qAtom, Map<Variable, Set<GroundTerm>> substitutions) {
		this.substitutions = substitutions;
		// Set the variables for the recursive traversal
		rootPredicate = qAtom.getPredicate();
		rootArguments = qAtom.getArguments();
		GroundTerm[] groundArguments = new GroundTerm[rootArguments.length];
		
		// Perform a recursive depth-first traversal of the arguments and their substitutions
		groundAndPersistAtom(0, groundArguments);
	}
	
	/*
	 * The following variables are used for the recursive call
	 */
	private Map<Variable, Set<GroundTerm>> substitutions;
	private Predicate rootPredicate;
	private Term[] rootArguments;
	
	/**
	 * Using the root predicate and root arguments set by the original calling function,
	 * this helper method performs a recursive depth-first traversal of the arguments
	 * @param index			the current "depth" into the arguments
	 * @param arguments		the substituted arguments at this "depth"
	 */
	private void groundAndPersistAtom(int index, GroundTerm[] arguments) {
		if (index < rootArguments.length) {
			// Check the type of the argument
			if (rootArguments[index] instanceof Variable) {
				// Get all of the substitutions for a variable 
				Set<GroundTerm> groundTerms = substitutions.get((Variable)rootArguments[index]);
				
				// Sanity check
				if (groundTerms == null || groundTerms.size() == 0) // Sanity check
					throw new RuntimeException("No valid GroundTerm substitutions for " + rootArguments[index].toString());
				
				// Iterate through the GroundTerms, performing a recursive depth-first replacement
				for (GroundTerm term : groundTerms) {
					arguments[index] = term;
					groundAndPersistAtom(index + 1, arguments);
				}
			} else if (rootArguments[index] instanceof GroundTerm) {
				// No substitutions necessary, there is only one.
				arguments[index] = (GroundTerm) rootArguments[index];
				groundAndPersistAtom(index + 1, arguments);
			} else {
				// Error, this is an unknown / unexpected type of argument.
				throw new RuntimeException("Unknown argument type: " + rootArguments[index].getClass().getName());
			}
		} else {
			// End of the depth-first search
			GroundAtom atom = db.getAtom(rootPredicate, arguments);
			if (atom instanceof RandomVariableAtom) {
				db.commit((RandomVariableAtom) atom);
			}
		}
	}
}
