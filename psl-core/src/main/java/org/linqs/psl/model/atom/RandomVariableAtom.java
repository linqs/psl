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
package org.linqs.psl.model.atom;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.ConfidenceValues;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.function.MutableAtomFunctionVariable;

/**
 * A {@link GroundAtom} with a truth value and a confidence value which can be modified.
 * <p>
 * A GroundAtom is instantiated as a RandomVariableAtom is BOTH of the following
 * conditions are met:
 * <ul>
 *   <li>it has a {@link StandardPredicate} that is open in the Atom's Database</li>
 *   <li>it is not persisted in one of its Database's read-only Partitions</li>
 * </ul>
 */
public class RandomVariableAtom extends GroundAtom {

	protected RandomVariableAtom(StandardPredicate p, Constant[] args,
			Database db, double value, double confidenceValue) {
		super(p, args, db, value, confidenceValue);
	}

	@Override
	public StandardPredicate getPredicate() {
		return (StandardPredicate) predicate;
	}

	/**
	 * Sets the truth value of this Atom.
	 * 
	 * @param value  a truth value in [0,1]
	 * @return this for convenience
	 * @throws IllegalArgumentException  if value is not in [0,1]
	 */
	public RandomVariableAtom setValue(double value) {
		//		if (0.0 <= value && value <= 1.0) 
		this.value = value;
		//		else
		//			throw new IllegalArgumentException("Value should be in [0,1] but is " + value);

		return this;
	}

	/**
	 * Sets the confidence value of this Atom.
	 * 
	 * @param value  the new confidence value
	 * @return this for convenience
	 * @throws IllegalArgumentException  if value is invalid
	 * @see ConfidenceValues#isValid(double);
	 */
	public RandomVariableAtom setConfidenceValue(double value) {
		if (ConfidenceValues.isValid(value))
			this.confidenceValue = value;
		else
			throw new IllegalArgumentException();

		return this;
	}

	/**
	 * Calls {@link Database#commit(RandomVariableAtom)} with this Atom
	 * on the Database that instantiated it.
	 */
	public void commitToDB() {
		db.commit(this);
	}

	@Override
	public MutableAtomFunctionVariable getVariable() {
		return new MutableAtomFunctionVariable(this);
	}

}
