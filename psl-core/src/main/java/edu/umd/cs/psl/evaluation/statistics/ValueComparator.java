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
package edu.umd.cs.psl.evaluation.statistics;

import com.google.common.base.Preconditions;

public enum ValueComparator {

	Threshold {
		
		@Override
		public double getDifference(double[] expected, double[] actual, double threshold) {
			Preconditions.checkArgument(expected.length==1 && actual.length==1,"Expected singleton values!");
			double actualVal = actual[0] >= threshold ? 1.0 : 0.0;
			double expectedVal = expected[0] >= threshold ? 1.0 : 0.0;
			return Math.abs(actualVal - expectedVal);
		}
	},

	AbsoluteDifference {

		@Override
		public double getDifference(double[] expected, double[] actual,
				double tolerance) {
			Preconditions.checkArgument(expected.length==1 && actual.length==1,"Expected singleton values!");
			double res = actual[0]-expected[0];
			if (Math.abs(res)<=tolerance) return 0.0;
			else return res;
		}
		
		
		
	},
	
	Manhatten {

		@Override
		public double getDifference(double[] expected, double[] actual,
				double tolerance) {
			Preconditions.checkArgument(expected.length==actual.length,"Values differ in length!");
			double abs = 0.0;
			for (int i=0;i<expected.length;i++) {
				double diff = actual[i]-expected[i];
				abs += Math.abs(diff);
			}
			if (abs<=tolerance) return 0.0;
			else return abs;
		}
		
	},
	
	Euclidean {
		
		@Override
		public double getDifference(double[] expected, double[] actual,
				double tolerance) {
			Preconditions.checkArgument(expected.length==actual.length,"Values differ in length!");
			double abs = 0.0;
			for (int i=0;i<expected.length;i++) {
				double diff = actual[i]-expected[i];
				abs += Math.pow(diff, 2.0);
			}
			abs = Math.sqrt(abs);
			if (abs<=tolerance) return 0.0;
			else return abs;
		}
		
	};
	
	public abstract double getDifference(double[] expected, double[] actual, double tolerance);
	
}
