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
package edu.umd.cs.psl.model.kernel.predicateconstraint;

import edu.umd.cs.psl.reasoner.function.FunctionComparator;

/**
 * PredicateConstraintType provides a method of specifying predicate constraints as used by the 
 * {@link DomainRangeConstraintKernel}. The constraint functions are based on the types supported by 
 * {@link FunctionComparator} and include equality and non-equality constraints. 
 * PredicateConstraintType provides the ability to determine whether a constraint holds or not.
 */
public enum DomainRangeConstraintType {
	Functional {

		@Override
		boolean equality() {
			return true;
		}

		@Override
		int position() {
			return 0;
		}
		
		@Override
		FunctionComparator constraint() {
			return FunctionComparator.Equality;
		}
		
		@Override
		public String toString() {
			return "Functional";
		}
	},
	
	InverseFunctional {
		@Override
		boolean equality() {
			return true;
		}

		@Override
		int position() {
			return 1;
		}
		
		@Override
		FunctionComparator constraint() {
			return FunctionComparator.Equality;
		}
		
		@Override
		public String toString() {
			return "InverseFunctional";
		}
	},
	
	PartialFunctional {
		@Override
		boolean equality() {
			return false;
		}

		@Override
		int position() {
			return 0;
		}

		@Override
		FunctionComparator constraint() {
			return FunctionComparator.SmallerThan;
		}
		
		@Override
		public String toString() {
			return "PartialFunctional";
		}
	},
	
	PartialInverseFunctional {
		@Override
		boolean equality() {
			return false;
		}

		@Override
		int position() {
			return 1;
		}

		@Override
		FunctionComparator constraint() {
			return FunctionComparator.SmallerThan;
		}
		
		@Override
		public String toString() {
			return "PartialInverseFunctional";
		}
		
	};
	/**
	 * Get the position of the constrained argument in the predicate
	 * @return integer position of constrained element in predicate arguments
	 */
	abstract int position();
	
	/**
	 * Determines whether this is an equality constraint or not
	 * @return boolean true if the constraint is an equality constraint, false otherwise
	 */
	abstract boolean equality();
	
	/**
	 * The constraint type (eg. SmallerThan, Equality) as one of the types in {@link FunctionComparator}. 
	 * @return Enumerated type from {@link FunctionComparator}
	 */
	abstract FunctionComparator constraint();
	
	/**
	 * @return the distance between a value and this constraint's nearest feasible value
	 */
	double getInfeasibility(double val) {
		switch(this.constraint()) {
		case SmallerThan:
			return (val > 1.0) ? val - 1.0 : 0.0;
		case Equality:
			return Math.abs(val - 1.0);
		default:
			throw new IllegalArgumentException("Unrecognized comparator: " + this.constraint());
		}
	}
}
