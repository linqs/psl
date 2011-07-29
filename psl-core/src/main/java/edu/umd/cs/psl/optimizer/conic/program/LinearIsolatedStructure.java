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
package edu.umd.cs.psl.optimizer.conic.program;

import java.util.Map.Entry;

class LinearIsolatedStructure extends IsolatedStructure {
	private LinearConstraint lc;
	private Variable positiveSlack;
	private Variable negativeSlack;
	
	
	LinearIsolatedStructure(LinearConstraint lc, Variable positiveSlack,
			Variable negativeSlack) {
		super();
		this.lc = lc;
		this.positiveSlack = positiveSlack;
		this.negativeSlack = negativeSlack;
	}
	
	@Override
	void makePrimalFeasible() {
		double value, diff;
		
		value = 0.0;
		for (Entry<Variable, Double> e : lc.getVariables().entrySet()) {
			value += e.getKey().getValue() * e.getValue();
		}
		diff = lc.getConstrainedValue() - value;
		if (diff < 0) {
			negativeSlack.setValue(negativeSlack.getValue() - diff);
		}
		else {
			positiveSlack.setValue(positiveSlack.getValue() + diff);
		}
	}
}
