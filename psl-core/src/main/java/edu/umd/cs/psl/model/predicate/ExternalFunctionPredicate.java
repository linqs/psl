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
package edu.umd.cs.psl.model.predicate;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.function.ExternalFunction;


/**
 * This class implements an abstract fuzzy predicate which is extended by particular fuzzy predicate
 * classes. This class provides some standard functionality for fuzzy predicates.
 * 
 * To construct a fuzzy predicate use the static create() methods in Predicate.java
 * 
 * @author Matthias Broecheler
 *
 */
public class ExternalFunctionPredicate extends AbstractPredicate implements FunctionalPredicate {

	private final ExternalFunction extFun;
	
	ExternalFunctionPredicate(String name, ExternalFunction extFun) {
		super(name,extFun.getPredicateType(),extFun.getArgumentTypes());
		this.extFun = extFun;
	}
	
	/**
	 * This function computes the truth value of a fuzzy predicate and must be implemented by a particular
	 * fuzzy predicate class
	 * @param args Fuzzy predicate arguments
	 * @return Truth value
	 */
	@Override
	public double[] computeValues(GroundTerm... args) {
		return extFun.getValue(args);
	}
	
	public ExternalFunction getExternalFunction() {
		return extFun;
	}
	
	/**
	 * This is a general toString function for fuzzy predicates which is called from a particular
	 * fuzzy predicate class passing the name of the class (= name of fuzzy predicate) as an argument
	 * @param name Name of fuzzy predicate
	 * @return String representation of fuzzy predicate
	 */
	public String toString(String name) {
		return super.toString() + " := " + name + " - Attribute [" + extFun.toString() + "]";

	}

	@Override
	public boolean isNonDefaultValues(double[] values) {
		throw new UnsupportedOperationException();
	}
	
	
}
