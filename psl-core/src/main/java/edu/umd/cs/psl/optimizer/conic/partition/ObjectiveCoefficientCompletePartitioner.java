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
package edu.umd.cs.psl.optimizer.conic.partition;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class ObjectiveCoefficientCompletePartitioner extends HierarchicalPartitioner {
	
	private static final int base = 2;

	public ObjectiveCoefficientCompletePartitioner(ConfigBundle config) {
		super(config);
	}

	@Override
	protected double getWeight(LinearConstraint lc, Cone cone) {
		if (restrictedConstraints.contains(lc)) {
			return 300000.0 / alwaysCutConstraints.size();
		}
		else {
			boolean hasFirstSingleton = false;
			
			for (Variable var : lc.getVariables().keySet()) {
				if (isSingleton(var.getCone())) {
					if (hasFirstSingleton) {
						if (p % 2 == 0) {
							if (cone instanceof NonNegativeOrthantCone)
								return Math.pow(base, Math.abs(((NonNegativeOrthantCone) cone).getVariable().getObjectiveCoefficient()) + 1);
							else if (cone instanceof SecondOrderCone) {
								double weight = 0.0;
								for (Variable socVar : ((SecondOrderCone) cone).getVariables())
									weight += socVar.getObjectiveCoefficient();
								return Math.pow(base, Math.abs(weight) + 1);
							}
							else
								throw new IllegalStateException();
						}
						else {
							if (cone instanceof NonNegativeOrthantCone)
								return 1 / (Math.pow(base, Math.abs(((NonNegativeOrthantCone) cone).getVariable().getObjectiveCoefficient()) + 1));
							else if (cone instanceof SecondOrderCone) {
								double weight = 0.0;
								for (Variable socVar : ((SecondOrderCone) cone).getVariables())
									weight += socVar.getObjectiveCoefficient();
								return 1 / (Math.pow(base, Math.abs(weight) + 1));
							}
							else
								throw new IllegalStateException();
						}
					}
					else {
						hasFirstSingleton = true;
					}
				}
			}
			
			return Double.POSITIVE_INFINITY;
		}
	}

	@Override
	protected void processAcceptedPartition() {
		return;
	}

}
