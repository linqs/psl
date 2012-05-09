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

import edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType;

public enum PredicateConstraint {

	Functional {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return PredicateConstraintType.Functional;
		}
	},

	InverseFunctional {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return PredicateConstraintType.InverseFunctional;
		}
	},

	PartialFunctional {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return PredicateConstraintType.PartialFunctional;
		}
	},

	PartialInverseFunctional {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return PredicateConstraintType.PartialInverseFunctional;
		}
	},

	PIF {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return PredicateConstraintType.PartialInverseFunctional;
		}
	},

	PF {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return PredicateConstraintType.PartialFunctional;
		}
	},

	IF {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return PredicateConstraintType.InverseFunctional;
		}
	},

	F {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return PredicateConstraintType.Functional;
		}
	},

	Symmetric {
		@Override
		public PredicateConstraintType getPSLConstraint() {
			return null;
		}
	};

	public abstract PredicateConstraintType getPSLConstraint();

}
