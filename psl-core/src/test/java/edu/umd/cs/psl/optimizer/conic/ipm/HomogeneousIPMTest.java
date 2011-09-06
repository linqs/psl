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
package edu.umd.cs.psl.optimizer.conic.ipm;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class HomogeneousIPMTest {
	
	private static final double SOLUTION_TOLERANCE = 0.01;
	
	ConicProgram program;
	HomogeneousIPM ipm;
	
	Variable x1, x2;

	@Before
	public void setUp() throws Exception {
		program = new ConicProgram();
		ipm = new HomogeneousIPM(new EmptyBundle());
		
		LinearConstraint phi1 = (LinearConstraint) program.createConstraint();
		LinearConstraint phi2 = (LinearConstraint) program.createConstraint();
		LinearConstraint phi3 = (LinearConstraint) program.createConstraint();
		LinearConstraint c1 = (LinearConstraint) program.createConstraint();
		LinearConstraint c2 = (LinearConstraint) program.createConstraint();
		
		x1 = program.createNonNegativeOrthantCone().getVariable();
		x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		Variable x4 = program.createNonNegativeOrthantCone().getVariable();
		Variable x5 = program.createNonNegativeOrthantCone().getVariable();
		Variable x6 = program.createNonNegativeOrthantCone().getVariable();
		Variable x7 = program.createNonNegativeOrthantCone().getVariable();
		Variable x8 = program.createNonNegativeOrthantCone().getVariable();
		Variable x9 = program.createNonNegativeOrthantCone().getVariable();
		Variable x10 = program.createNonNegativeOrthantCone().getVariable();
		
		phi1.addVariable(x1, 1.0);
		phi1.addVariable(x3, 1.0);
		phi1.addVariable(x4, -1.0);
		
		phi2.addVariable(x1, -1.0);
		phi2.addVariable(x2, 1.0);
		phi2.addVariable(x5, 1.0);
		phi2.addVariable(x6, -1.0);

		phi3.addVariable(x2, -1.0);
		phi3.addVariable(x7, 1.0);
		phi3.addVariable(x8, -1.0);
		
		c1.addVariable(x1, 1.0);
		c1.addVariable(x9, 1.0);
		
		c2.addVariable(x2, 1.0);
		c2.addVariable(x10, 1.0);
		
		phi1.setConstrainedValue(0.7);
		phi2.setConstrainedValue(0.0);
		phi3.setConstrainedValue(-0.2);
		c1.setConstrainedValue(1.0);
		c2.setConstrainedValue(1.0);
		
		/* Squares the variable x3 in the phi1 contstraint */
		
		Variable x3Sq = program.createNonNegativeOrthantCone().getVariable();
		SecondOrderCone soc = program.createSecondOrderCone(3);
		Variable phi1OuterSquaredVar = soc.getNthVariable();
		Variable phi1InnerFeatureVar = null, phi1InnerSquaredVar = null;
		for (Variable v : soc.getVariables()) {
			if (!v.equals(phi1OuterSquaredVar))
				if (phi1InnerFeatureVar == null)
					phi1InnerFeatureVar = v;
				else
					phi1InnerSquaredVar = v;
		}
		
		LinearConstraint phi1InnerFeatureCon = program.createConstraint();
		phi1InnerFeatureCon.addVariable(x3, 1.0);
		phi1InnerFeatureCon.addVariable(phi1InnerFeatureVar, -1.0);
		phi1InnerFeatureCon.setConstrainedValue(0.0);
		
		LinearConstraint phi1InnerSquaredCon = program.createConstraint();
		phi1InnerSquaredCon.addVariable(phi1InnerSquaredVar, 1.0);
		phi1InnerSquaredCon.addVariable(x3Sq, 0.5);
		phi1InnerSquaredCon.setConstrainedValue(0.5);
		
		LinearConstraint phi1OuterSquaredCon = program.createConstraint();
		phi1OuterSquaredCon.addVariable(phi1OuterSquaredVar, 1.0);
		phi1OuterSquaredCon.addVariable(x3Sq, -0.5);
		phi1OuterSquaredCon.setConstrainedValue(0.5);
		
		/* Squares the variable x5 in the phi2 contstraint */
		
		Variable x5Sq = program.createNonNegativeOrthantCone().getVariable();
		soc = program.createSecondOrderCone(3);
		Variable phi2OuterSquaredVar = soc.getNthVariable();
		Variable phi2InnerFeatureVar = null, phi2InnerSquaredVar = null;
		for (Variable v : soc.getVariables()) {
			if (!v.equals(phi2OuterSquaredVar))
				if (phi2InnerFeatureVar == null)
					phi2InnerFeatureVar = v;
				else
					phi2InnerSquaredVar = v;
		}
		
		LinearConstraint phi2InnerFeatureCon = program.createConstraint();
		phi2InnerFeatureCon.addVariable(x5, 1.0);
		phi2InnerFeatureCon.addVariable(phi2InnerFeatureVar, -1.0);
		phi2InnerFeatureCon.setConstrainedValue(0.0);
		
		LinearConstraint phi2InnerSquaredCon = program.createConstraint();
		phi2InnerSquaredCon.addVariable(phi2InnerSquaredVar, 1.0);
		phi2InnerSquaredCon.addVariable(x5Sq, 0.5);
		phi2InnerSquaredCon.setConstrainedValue(0.5);
		
		LinearConstraint phi2OuterSquaredCon = program.createConstraint();
		phi2OuterSquaredCon.addVariable(phi2OuterSquaredVar, 1.0);
		phi2OuterSquaredCon.addVariable(x5Sq, -0.5);
		phi2OuterSquaredCon.setConstrainedValue(0.5);
		
		/* Squares the variable x7 in the phi3 contstraint */
		
		Variable x7Sq = program.createNonNegativeOrthantCone().getVariable();
		soc = program.createSecondOrderCone(3);
		Variable phi3OuterSquaredVar = soc.getNthVariable();
		Variable phi3InnerFeatureVar = null, phi3InnerSquaredVar = null;
		for (Variable v : soc.getVariables()) {
			if (!v.equals(phi3OuterSquaredVar))
				if (phi3InnerFeatureVar == null)
					phi3InnerFeatureVar = v;
				else
					phi3InnerSquaredVar = v;
		}
		
		LinearConstraint phi3InnerFeatureCon = program.createConstraint();
		phi3InnerFeatureCon.addVariable(x7, 1.0);
		phi3InnerFeatureCon.addVariable(phi3InnerFeatureVar, -1.0);
		phi3InnerFeatureCon.setConstrainedValue(0.0);
		
		LinearConstraint phi3InnerSquaredCon = program.createConstraint();
		phi3InnerSquaredCon.addVariable(phi3InnerSquaredVar, 1.0);
		phi3InnerSquaredCon.addVariable(x7Sq, 0.5);
		phi3InnerSquaredCon.setConstrainedValue(0.5);
		
		LinearConstraint phi3OuterSquaredCon = program.createConstraint();
		phi3OuterSquaredCon.addVariable(phi3OuterSquaredVar, 1.0);
		phi3OuterSquaredCon.addVariable(x7Sq, -0.5);
		phi3OuterSquaredCon.setConstrainedValue(0.5);
		
		x1.setObjectiveCoefficient(0.0);
		x2.setObjectiveCoefficient(0.0);
		x3.setObjectiveCoefficient(0.0);
		x4.setObjectiveCoefficient(0.0);
		x5.setObjectiveCoefficient(0.0);
		x6.setObjectiveCoefficient(0.0);
		x7.setObjectiveCoefficient(0.0);
		x8.setObjectiveCoefficient(0.0);
		x9.setObjectiveCoefficient(0.0);
		x10.setObjectiveCoefficient(0.0);
		
		x3Sq.setObjectiveCoefficient(1.0);
		x5Sq.setObjectiveCoefficient(2.0);
		x7Sq.setObjectiveCoefficient(3.0);
	}

	@Test
	public void testSolve() {
		ipm.solve(program);
		assertTrue(Math.abs(x1.getValue() - 0.427) < SOLUTION_TOLERANCE);
		assertTrue(Math.abs(x2.getValue() - 0.2909) < SOLUTION_TOLERANCE);
	}

}
