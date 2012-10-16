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
package edu.umd.cs.psl.model.atom;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
//import edu.umd.cs.psl.reasoner.function.MutableAtomFunctionVariable;

/**
 * A {@link StandardAtom} that does not exist in one of its {@link Database}'s read
 * Partitions.
 * <p>
 * A RandomVariableAtom's truth value and confidence value can be modified if it
 * is not fixed.
 * TODO: Generate an event instead?
 */
public class RandomVariableAtom extends StandardAtom {
	
	protected RandomVariableAtom(StandardPredicate p, GroundTerm[] args,
			Database db, double value, double confidenceValue, boolean fixed) {
		super(p, args, db, value);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Sets the truth value of this Atom.
	 * 
	 * @param value  a truth value in [0,1]
	 * @throws IllegalArgumentException  if value is not in [0,1]
	 */
	public void setValue(double value) {
		
	}
	
	/**
	 * Sets the confidence value of this Atom.
	 * 
	 * @param value A confidence value in [0, +Infinity)
	 * @throws IllegalArgumentException  if value is not in [0, +Infinity)
	 */
	public void setConfidenceValue(double value) {
		
	}
	
	public void commitToDB() {
		db.commit(this);
	}
	
	public boolean isFixed() {
		// TODO
		return false;
	}

	@Override
	public double getConfidenceValue() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public AtomFunctionVariable getVariable() {
		// TODO Auto-generated method stub
		return null;
	}

}
