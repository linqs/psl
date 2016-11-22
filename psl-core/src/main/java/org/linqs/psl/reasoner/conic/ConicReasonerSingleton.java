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
package org.linqs.psl.reasoner.conic;

import java.util.Map;

import org.linqs.psl.experimental.optimizer.conic.program.Variable;
import org.linqs.psl.reasoner.function.FunctionSingleton;
import org.linqs.psl.reasoner.function.FunctionVariable;

class ConicReasonerSingleton implements FunctionSingleton {

	Variable var;
	
	protected ConicReasonerSingleton(Variable v) {
		var = v;
	}
	
	protected Variable getVariable() {
		return var;
	}
	
	@Override
	public double getValue() {
		return var.getValue();
	}

	@Override
	public double getValue(Map<? extends FunctionVariable, Double> values,
			boolean assumeDefaultValue) {
		return getValue();
	}

	@Override
	public boolean isConstant() {
		return false;
	}

	@Override
	public boolean isLinear() {
		return true;
	}
}
