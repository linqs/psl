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
package edu.umd.cs.psl.groovy;

import edu.umd.cs.psl.model.kernel.predicateconstraint.DomainRangeConstraintType;

public enum PredicateConstraint {

	Functional {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return DomainRangeConstraintType.Functional;
		}
	},

	InverseFunctional {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return DomainRangeConstraintType.InverseFunctional;
		}
	},

	PartialFunctional {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return DomainRangeConstraintType.PartialFunctional;
		}
	},

	PartialInverseFunctional {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return DomainRangeConstraintType.PartialInverseFunctional;
		}
	},

	PIF {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return DomainRangeConstraintType.PartialInverseFunctional;
		}
	},

	PF {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return DomainRangeConstraintType.PartialFunctional;
		}
	},

	IF {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return DomainRangeConstraintType.InverseFunctional;
		}
	},

	F {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return DomainRangeConstraintType.Functional;
		}
	},

	Symmetric {
		@Override
		public DomainRangeConstraintType getPSLConstraint() {
			return null;
		}
	};

	public abstract DomainRangeConstraintType getPSLConstraint();

}
