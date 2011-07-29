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
package edu.umd.cs.psl.optimizer;

import com.google.common.base.Preconditions;

public class NumericUtilities {

	public static final double relaxedEpsilon = 1e-3;
	public static final double strictEpsilong = 1e-8;
	
	public static boolean equals(double val1, double val2) {
		return Math.abs(val1-val2)<relaxedEpsilon;
	}
	
	public static boolean equals(double[] val1, double[] val2) {
		Preconditions.checkArgument(val1.length==val2.length);
		for (int i=0;i<val1.length;i++) {
			if (!equals(val1[i],val2[i])) return false;
		}
		return true;
	}
	
}
