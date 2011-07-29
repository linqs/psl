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
package edu.umd.cs.psl.groovy;

public enum PredicateConstraint {

	Functional {
		@Override
		public edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint() {
			return edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType.Functional;
		}
	},
	
	InverseFunctional {
		@Override
		public edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint() {
			return edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType.InverseFunctional;
		}		
	},
	
	PartialFunctional {
		@Override
		public edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint() {
			return edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType.PartialFunctional;
		}
	},
	
	PartialInverseFunctional {
		@Override
		public edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint() {
			return edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType.PartialInverseFunctional;
		}
	},
	
	PIF {
		@Override
		public edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint() {
			return edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType.PartialInverseFunctional;
		}
	},
	
	PF {
		@Override
		public edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint() {
			return edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType.PartialFunctional;
		}
	},
	
	IF {
		@Override
		public edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint() {
			return edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType.InverseFunctional;
		}
	},
	
	F {
		@Override
		public edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint() {
			return edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType.Functional;
		}
	};
	
	public abstract edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType getPSLConstraint();
	
}
