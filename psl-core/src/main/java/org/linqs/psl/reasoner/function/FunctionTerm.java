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
package org.linqs.psl.reasoner.function;

import java.util.Map;

/**
 * A numeric function.
 */
public interface FunctionTerm {

	/**
	 * Returns the term's value
	 *
	 * @return  the term's value
	 */
	public double getValue();
	
	/**
	 * Returns the term's value using provided values for {@link FunctionVariable FunctionVariables}.
	 * 
	 * @param values		values to use
	 * @param useCurrentValues  use a {@link FunctionVariable FunctionVariable's}
	 *					              current value if it is unspecified in values
	 * @throws IllegalArgumentException  if a FunctionVariable is unspecified and assumeDefaultValue is false
	 * @return 				the term's value
	 */
	public double getValue(Map<? extends FunctionVariable,Double> values, boolean useCurrentValues);
	
	/**
	 * Returns whether the term is linear in its {@link FunctionVariable Variables}.
	 *
	 * Returns true if the term is a constant.
	 *
	 * @return  whether the term is linear
	 */
	public boolean isLinear();
	
	/**
	 * Returns whether the term is constant.
	 *
	 * @return  whether the term is constant
	 */
	public boolean isConstant();
}

