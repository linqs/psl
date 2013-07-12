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
package edu.umd.cs.psl.model;

/**
 * Static methods related to valid range of confidence values.
 */
public class ConfidenceValues {
	private static final double min = 0.0;
	private static final double max = Double.MAX_VALUE;
	
	public static final double getMin() {
		return min;
	}
	
	public static final double getMax() {
		return max;
	}
	
	public static final boolean isValid(double confidenceVal) {
		return (confidenceVal>=getMin() && confidenceVal<=getMax()) || Double.isNaN(confidenceVal);
	}
	
}
