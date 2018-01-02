/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.model;

public class NumericUtilities {

	public static final double relaxedEpsilon = 5e-2;
	public static final double strictEpsilon = 1e-8;
	
	public static boolean equalsRelaxed(double val1, double val2) {
		return Math.abs(val1-val2)<relaxedEpsilon;
	}
	
	public static boolean equalsStrict(double val1, double val2) {
		return Math.abs(val1-val2)<strictEpsilon;
	}
	
}
