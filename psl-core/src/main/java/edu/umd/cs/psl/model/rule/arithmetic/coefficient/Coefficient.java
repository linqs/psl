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
package edu.umd.cs.psl.model.rule.arithmetic.coefficient;

import java.util.Map;

/**
 * Numeric coefficient in a {@link Formula}.
 * <p>
 * Coefficient and its subclasses are composable to represent complex definitions.
 * Its subclasses are defined as inner classes, because there are
 * a lot of them and they are simple. 
 * 
 * @author Stephen Bach
 */
abstract public class Coefficient {
	
	abstract public double getValue(Map<Coefficient.Cardinality, Double> cardinalityMap);
	
	public class ConstantNumber extends Coefficient {
		
		protected final double value;
		
		public ConstantNumber(double value) {
			this.value = value;
		}

		@Override
		public double getValue(Map<Cardinality, Double> cardinalityMap) {
			return value;
		}
	}
	
	/**
	 * The number of substitutions made for a {@link SumVariable} in a grounding.
	 */
	public class Cardinality extends Coefficient {
		
		protected final SumVariable v;
		
		public Cardinality(SumVariable v) {
			this.v = v;
		}

		@Override
		public double getValue(Map<Cardinality, Double> cardinalityMap) {
			return cardinalityMap.get(v);
		}
	}
	
	public class Add extends Coefficient {
		
		protected final Coefficient c1, c2;
		
		public Add(Coefficient c1, Coefficient c2) {
			this.c1 = c1;
			this.c2 = c2;
		}
		
		@Override
		public double getValue(Map<Cardinality, Double> cardinalityMap) {
			return c1.getValue(cardinalityMap) + c2.getValue(cardinalityMap);
		}
	}
	
	public class Subtract extends Coefficient {
		
		protected final Coefficient c1, c2;
		
		public Subtract(Coefficient c1, Coefficient c2) {
			this.c1 = c1;
			this.c2 = c2;
		}
		
		@Override
		public double getValue(Map<Cardinality, Double> cardinalityMap) {
			return c1.getValue(cardinalityMap) - c2.getValue(cardinalityMap);
		}
	}
	
	public class Multiply extends Coefficient {
		
		protected final Coefficient c1, c2;
		
		public Multiply(Coefficient c1, Coefficient c2) {
			this.c1 = c1;
			this.c2 = c2;
		}
		
		@Override
		public double getValue(Map<Cardinality, Double> cardinalityMap) {
			return c1.getValue(cardinalityMap)  * c2.getValue(cardinalityMap);
		}
	}
	
	public class Divide extends Coefficient {
		
		protected final Coefficient c1, c2;
		
		public Divide(Coefficient c1, Coefficient c2) {
			this.c1 = c1;
			this.c2 = c2;
		}
		
		@Override
		public double getValue(Map<Cardinality, Double> cardinalityMap) {
			return c1.getValue(cardinalityMap) / c2.getValue(cardinalityMap);
		}
	}
	
	public class Max extends Coefficient {
		
		protected final Coefficient c1, c2;
		
		public Max(Coefficient c1, Coefficient c2) {
			this.c1 = c1;
			this.c2 = c2;
		}
		
		@Override
		public double getValue(Map<Cardinality, Double> cardinalityMap) {
			return Math.max(c1.getValue(cardinalityMap), c2.getValue(cardinalityMap));
		}
	}
	
	public class Min extends Coefficient {
		
		protected final Coefficient c1, c2;
		
		public Min(Coefficient c1, Coefficient c2) {
			this.c1 = c1;
			this.c2 = c2;
		}
		
		@Override
		public double getValue(Map<Cardinality, Double> cardinalityMap) {
			return Math.min(c1.getValue(cardinalityMap), c2.getValue(cardinalityMap));
		}
	}
}
