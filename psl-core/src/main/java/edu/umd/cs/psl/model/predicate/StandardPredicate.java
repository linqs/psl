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

import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.predicate.type.PredicateType;


/**
 * This class implements a boolean predicate which is defined by its name and type signature.
 * Boolean Predicates cannot be directly constructed but must be constructed through the static
 * create() methods in Predicate.java
 * 
 * @author Matthias Broecheler
 *
 */
public class StandardPredicate extends AbstractPredicate {

	private final double[] defaultParameters;
	
	StandardPredicate(String name, PredicateType type, ArgumentType[] types, double[] defaultParas) {
		super(name, type, types);
		if (defaultParas.length!=type.getNumberOfActivationParameters()) throw new IllegalArgumentException();
		defaultParameters=defaultParas;
	}
	
	@Override
	public boolean isNonDefaultValues(double[] values) {
		return type.isNonDefaultValues(values, defaultParameters);
	}

	
}
