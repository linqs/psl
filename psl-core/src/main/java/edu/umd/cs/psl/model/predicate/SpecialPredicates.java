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

import edu.umd.cs.psl.database.UniqueID;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;
import edu.umd.cs.psl.model.predicate.type.PredicateType;
import edu.umd.cs.psl.model.predicate.type.PredicateTypes;

public enum SpecialPredicates implements FunctionalPredicate {

	Unequal {

		@Override
		public double[] computeValues(GroundTerm... args) {
			assert args.length==2;
			assert args[0] instanceof Entity && args[1] instanceof Entity;
			if (args[0].equals(args[1])) return new double[]{0.0};
			else return new double[]{1.0};
		}

		@Override
		public String getName() {
			return "#NonReflexive";
		}		
	},
	
	Equal {

		@Override
		public double[] computeValues(GroundTerm... args) {
			assert args.length==2;
			assert args[0] instanceof Entity && args[1] instanceof Entity;
			if (args[0].equals(args[1])) return new double[]{1.0};
			else return new double[]{0.0};
		}

		@Override
		public String getName() {
			return "#Equal";
		}
		
	},

	
	NonSymmetric {
		
		@Override
		public double[] computeValues(GroundTerm... args) {
			assert args.length==2;
			assert args[0] instanceof Entity && args[1] instanceof Entity;
			UniqueID uid1 = ((Entity)args[0]).getID();
			UniqueID uid2 = ((Entity)args[1]).getID();
			return new double[]{(uid1.compareTo(uid2)<0)?1.0:0.0};
		}

		@Override
		public String getName() {
			return "#NonSymmetric";
		}
		
	};
	
	private static final PredicateType type = PredicateTypes.BooleanTruth;
	
	@Override
	public ArgumentType getArgumentType(int position) {
		assert position>=0 && position<2;
		return ArgumentTypes.Entity;
	}

	@Override
	public int getArity() {
		return 2;
	}
	
	@Override
	public PredicateType getType() {
		return type;
	}
	
	@Override
	public int getNumberOfValues() {
		return 1;
	}
	
	@Override
	public double[] getDefaultValues() {
		return type.getDefaultValues();
	}
	
	
	@Override
	public double[] getStandardValues() {
		return type.getStandardValues();
	}

	@Override
	public String getValueName(int pos) {
		return type.getValueName(pos);
	}

	@Override
	public boolean isNonDefaultValues(double[] values) {
		return type.isNonDefaultValues(values, new double[0]);
	}

	@Override
	public boolean validValue(int pos, double value) {
		return type.validValue(pos, value);
	}
	
	@Override
	public boolean validValues(double[] values) {
		return AbstractPredicate.validValues(this,values);
	}
	
}
