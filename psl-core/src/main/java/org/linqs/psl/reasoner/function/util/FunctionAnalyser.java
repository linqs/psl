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
package org.linqs.psl.reasoner.function.util;

import org.linqs.psl.reasoner.function.ConstantNumber;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.MaxFunction;

/**
 * Utilities for analyzing functions.
 */
public class FunctionAnalyser {

	/**
	 * Returns whether a function has a core function.
	 *
	 * @param fun  the function to analyze
	 * @return  whether the function has a core function
	 * @see #getCoreObjectiveFunction(FunctionTerm)
	 */
	public static boolean isSimpleObjectiveFunction(FunctionTerm fun) {
		return getCoreObjectiveFunction(fun)!=null;
		
	}
	
	/**
	 * Extracts a function from a {@link MaxFunction} additionally containing
	 * only the constant value zero.
	 *
	 * @param fun  the function to check
	 * @return  the extracted function, null if not found
	 */
	public static FunctionTerm getCoreObjectiveFunction(FunctionTerm fun) {
		if (!(fun instanceof MaxFunction)) return null;
		MaxFunction max = (MaxFunction)fun;
		FunctionTerm corefun = null;
		for (FunctionTerm term : max) {
			if (term instanceof ConstantNumber) {
				if (term.getValue()!=0.0) return null; 
			} else {
				if (corefun!=null) return null;
				corefun = term;
			}
		}
		return corefun;
	}
	
}
