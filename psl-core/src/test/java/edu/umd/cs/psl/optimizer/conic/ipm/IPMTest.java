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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class IPMTest {
	
	private static final double SOLUTION_TOLERANCE = 0.01;
	
	ConicProgram program;
	IPM ipm;
	
	Variable x1, x2;

	@Before
	public void setUp() throws Exception {
		program = new ConicProgram();
		ipm = new IPM(new EmptyBundle());
		
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
		
		x1.setObjectiveCoefficient(0.0);
		x2.setObjectiveCoefficient(0.0);
		x3.setObjectiveCoefficient(1.0);
		x4.setObjectiveCoefficient(0.0);
		x5.setObjectiveCoefficient(2.0);
		x6.setObjectiveCoefficient(0.0);
		x7.setObjectiveCoefficient(3.0);
		x8.setObjectiveCoefficient(0.0);
		x9.setObjectiveCoefficient(0.0);
		x10.setObjectiveCoefficient(0.0);
	}

	@Test
	public void testSolve() {
		ipm.solve(program);
		assertTrue(Math.abs(x1.getValue() - 0.2) < SOLUTION_TOLERANCE);
		assertTrue(Math.abs(x2.getValue() - 0.2) < SOLUTION_TOLERANCE);
	}

}
