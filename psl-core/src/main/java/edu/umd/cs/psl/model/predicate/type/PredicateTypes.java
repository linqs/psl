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
package edu.umd.cs.psl.model.predicate.type;

import edu.umd.cs.psl.optimizer.NumericUtilities;

public enum PredicateTypes implements PredicateType {
	
	SoftTruth {
		@Override
		public int getNumberOfValues() { return 1; }

		@Override
		public String getValueName(int pos) { 
			if (pos==0) return "Truth";
			else throw new ArrayIndexOutOfBoundsException();
		}

		@Override
		public double[] getDefaultValues() { return new double[]{0.0}; }
		
		@Override
		public double[] getStandardValues() { return new double[]{1.0}; }

		@Override
		public boolean isNonDefaultValues(double[] values, double[] defaultParas) {
			assert values.length==1 && defaultParas.length==1;
			return values[0]>=defaultParas[0];
		}

		@Override
		public boolean validValue(int pos, double value) {
			if (pos!=0) throw new ArrayIndexOutOfBoundsException();
			return value>-NumericUtilities.relaxedEpsilon && value<1+NumericUtilities.relaxedEpsilon;
		}	
	},
	
	BooleanTruth {
		@Override
		public int getNumberOfValues() { return 1; }

		@Override
		public String getValueName(int pos) { 
			if (pos==0) return "Truth";
			else throw new ArrayIndexOutOfBoundsException();
		}
		
		@Override
		public double[] getDefaultValues() { return new double[]{0.0}; }
		
		@Override
		public double[] getStandardValues() { return new double[]{1.0}; }
		
		@Override
		public boolean isNonDefaultValues(double[] values, double[] defaultParas) {
			assert values.length==1 && defaultParas.length==0;
			return values[0]==1.0;
		}
		
		@Override
		public int getNumberOfActivationParameters() { 
			return 0; 
		}

		@Override
		public boolean validValue(int pos, double value) {
			if (pos!=0) throw new ArrayIndexOutOfBoundsException();
			return value==0.0 || value==1.0;
		}	
	},
	
	Bernoulli {
		@Override
		public int getNumberOfValues() { return 1; }

		@Override
		public String getValueName(int pos) { 
			if (pos==0) return "Probability";
			else throw new ArrayIndexOutOfBoundsException();
		}
		
		@Override
		public double[] getDefaultValues() { return new double[]{0.0}; }
		
		@Override
		public double[] getStandardValues() { return new double[]{1.0}; }
		
		@Override
		public boolean isNonDefaultValues(double[] values, double[] defaultParas) {
			assert values.length==1 && defaultParas.length==1;
			return values[0]>=defaultParas[0];
		}

		@Override
		public boolean validValue(int pos, double value) {
			if (pos!=0) throw new ArrayIndexOutOfBoundsException();
			return value>-NumericUtilities.relaxedEpsilon && value<1+NumericUtilities.relaxedEpsilon;
		}	
	},
	
	Gaussian {
		@Override
		public int getNumberOfValues() { return 2; }

		@Override
		public String getValueName(int pos) { 
			if (pos==0) return "Mean";
			else if (pos==1) return "Std-Deviation";
			else throw new ArrayIndexOutOfBoundsException();
		}
		
		@Override
		public double[] getDefaultValues() { return new double[]{0.0, 1.0}; }
		
		@Override
		public double[] getStandardValues() { throw new UnsupportedOperationException(); }

		@Override
		public boolean isNonDefaultValues(double[] values, double[] defaultParas) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean validValue(int pos, double value) {
			throw new UnsupportedOperationException();
		}	
	};
	
	@Override
	public int getNumberOfActivationParameters() { 
		return 1; 
	}
	
}
