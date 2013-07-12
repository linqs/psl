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
package edu.umd.cs.psl.optimizer.conic.util;

import static org.junit.Assert.assertEquals;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.ipm.HomogeneousIPM;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class DualizerTest {
	
	private Dualizer dualizer;
	private ConicProgram program;
	private ConicProgramSolver solver;
	
	@Before
	public final void setUp()
			throws ConfigurationException, ClassNotFoundException, IllegalAccessException, InstantiationException {
		program = new ConicProgram();
		dualizer = new Dualizer(program);
		
		ConfigManager cm = ConfigManager.getManager();
		ConfigBundle config = cm.getBundle("dualizertest");
		solver = new HomogeneousIPM(config);
	}
	
	@Test
	public void testCheckOutAndIn() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		LinearConstraint con1 = program.createConstraint();
		LinearConstraint con2 = program.createConstraint();
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setConstrainedValue(2.0);
		con2.setVariable(x1, 1.0);
		con2.setVariable(x3, -1.0);
		con2.setConstrainedValue(1.0);
		
		program.checkOutMatrices();
		program.getX().set(program.getIndex(x1), 2.0);
		dualizer.checkOutProgram();
		dualizer.getDualProgram().checkOutMatrices();
		dualizer.getDualProgram().getW().set(0, 1.5);
		dualizer.getDualProgram().checkInMatrices();
		dualizer.checkInProgram();
		program.checkInMatrices();
		
		assertEquals(1.5, x1.getValue(), 0.0);
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testModifyDualProgram() {
		dualizer.getDualProgram().createConstraint();
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCheckOutDualProgramBeforePrimalMatrices() {
		dualizer.checkOutProgram();
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCheckOutDualMatricesBeforeCheckOutPrimalMatrices() {
		dualizer.getDualProgram().checkOutMatrices();		
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCheckOutDualMatricesBeforeCheckOutDualProgram() {
		program.checkOutMatrices();
		dualizer.getDualProgram().checkOutMatrices();		
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCheckInDualProgramBeforeCheckInDualMatrices() {
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.getDualProgram().checkOutMatrices();
		dualizer.checkInProgram();
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCheckInPrimalMatricesBeforeCheckInDualProgram() {
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		program.checkInMatrices();
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCheckOutDualProgramBeforeCheckInDualProgram() {
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.checkOutProgram();
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCheckInDualProgramBeforeCheckOutDualProgram() {
		program.checkOutMatrices();
		dualizer.checkInProgram();
	}
	
	/**
	 * Tests deleting a slack variable.
	 */
	@Test
	public void testDeleteSlackVariable() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		
		x1.setObjectiveCoefficient(1.0);
		x2.setObjectiveCoefficient(2.0);

		LinearConstraint con1 = program.createConstraint();
		
		con1.setConstrainedValue(1.0);
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setVariable(x3, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.checkInProgram();
		program.checkInMatrices();
		
		x3.getCone().delete();
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		ConicProgram dualProgram = dualizer.getDualProgram();
		solver.setConicProgram(dualProgram);
		solver.solve();
		dualizer.checkInProgram();
		
		assertEquals(1.0, program.getC().zDotProduct(program.getX()), 10e-5);
	}
	
	/**
	 * Tests that the dualizer can correctly handle deleting a slack variable if
	 * the corresponding linear constraint has already been deleted.
	 * 
	 * Creates a conic program with an inequality constraint, checks out the
	 * dual program, checks it back in, then deletes first the constraint and
	 * second the slack variable.
	 */
	@Test
	public void testDeleteConstraintThenSlackVariable() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		
		x1.setObjectiveCoefficient(-1.0);
		x2.setObjectiveCoefficient(-2.0);

		LinearConstraint con1 = program.createConstraint();
		LinearConstraint con2 = program.createConstraint();
		
		con1.setConstrainedValue(2.0);
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setVariable(x3, 1.0);
		
		con2.setConstrainedValue(1.0);
		con2.setVariable(x1, 1.0);
		con2.setVariable(x2, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.checkInProgram();
		program.checkInMatrices();
		
		con1.delete();
		x3.getCone().delete();
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		ConicProgram dualProgram = dualizer.getDualProgram();
		solver.setConicProgram(dualProgram);
		solver.solve();
		dualizer.checkInProgram();
		
		assertEquals(-2.0, program.getC().zDotProduct(program.getX()), 10e-5);
	}
	
	/**
	 * Tests deleting an equality constraint, i.e., without a slack variable.
	 */
	@Test
	public void testDeleteEqualityConstaint() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		
		x1.setObjectiveCoefficient(-1.0);
		x2.setObjectiveCoefficient(-2.0);
		x3.setObjectiveCoefficient(-3.0);

		LinearConstraint con1 = program.createConstraint();
		LinearConstraint con2 = program.createConstraint();
		
		con1.setConstrainedValue(2.0);
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setVariable(x3, 1.0);
		
		con2.setConstrainedValue(1.0);
		con2.setVariable(x1, 1.0);
		con2.setVariable(x2, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.checkInProgram();
		program.checkInMatrices();
		
		con2.delete();
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		ConicProgram dualProgram = dualizer.getDualProgram();
		solver.setConicProgram(dualProgram);
		solver.solve();
		dualizer.checkInProgram();
		
		assertEquals(-6.0, program.getC().zDotProduct(program.getX()), 10e-5);
	}
	
	/**
	 * Tests deleting a regular, i.e., non-slack, variable.
	 */
	@Test
	public void testDeleteRegularVariable() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		
		x1.setObjectiveCoefficient(-1.0);
		x2.setObjectiveCoefficient(-2.0);
		x3.setObjectiveCoefficient(-3.0);

		LinearConstraint con1 = program.createConstraint();
		LinearConstraint con2 = program.createConstraint();
		
		con1.setConstrainedValue(2.0);
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setVariable(x3, 1.0);
		
		con2.setConstrainedValue(1.0);
		con2.setVariable(x1, 1.0);
		con2.setVariable(x2, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.checkInProgram();
		program.checkInMatrices();
		
		x1.getCone().delete();
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		ConicProgram dualProgram = dualizer.getDualProgram();
		solver.setConicProgram(dualProgram);
		solver.solve();
		dualizer.checkInProgram();
		
		assertEquals(-5.0, program.getC().zDotProduct(program.getX()), 10e-5);
	}
	
	/**
	 * Tests that the dual program is empty after deleting everything in
	 * the primal program.
	 */
	@Test
	public void testDeleteEverything() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();

		LinearConstraint con1 = program.createConstraint();
		LinearConstraint con2 = program.createConstraint();
		
		con1.setConstrainedValue(2.0);
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setVariable(x3, 1.0);
		
		con2.setConstrainedValue(1.0);
		con2.setVariable(x1, 1.0);
		con2.setVariable(x2, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.checkInProgram();
		program.checkInMatrices();
		
		con1.delete();
		con2.delete();
		x1.getCone().delete();
		x2.getCone().delete();
		x3.getCone().delete();
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		
		ConicProgram dualProgram = dualizer.getDualProgram();
		
		assertEquals(0, dualProgram.getNumCones());
		assertEquals(0, dualProgram.getNumLinearConstraints());
	}
	
	/**
	 * Tests adding a variable to a constraint in the primal program after checking
	 * out and in.
	 */
	@Test
	public void testAddVariable() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		
		x1.setObjectiveCoefficient(-1.0);
		x2.setObjectiveCoefficient(-2.0);
		x3.setObjectiveCoefficient(-3.0);

		LinearConstraint con1 = program.createConstraint();
		LinearConstraint con2 = program.createConstraint();
		
		con1.setConstrainedValue(2.0);
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setVariable(x3, 1.0);
		
		con2.setConstrainedValue(1.0);
		con2.setVariable(x1, 1.0);
		con2.setVariable(x2, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.checkInProgram();
		program.checkInMatrices();
		
		Variable x4 = program.createNonNegativeOrthantCone().getVariable();
		x4.setObjectiveCoefficient(-4.0);
		con2.setVariable(x4, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		ConicProgram dualProgram = dualizer.getDualProgram();
		solver.setConicProgram(dualProgram);
		solver.solve();
		dualizer.checkInProgram();
		
		assertEquals(-10.0, program.getC().zDotProduct(program.getX()), 10e-5);
	}
	
	/**
	 * Tests deleting and replacing variables in the primal program after checking
	 * out and in.
	 */
	@Test
	public void testReplaceVariables() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		
		x1.setObjectiveCoefficient(1.0);
		x2.setObjectiveCoefficient(2.0);

		LinearConstraint con1 = program.createConstraint();
		LinearConstraint con2 = program.createConstraint();
		
		con1.setConstrainedValue(2.0);
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setVariable(x3, 1.0);
		
		con2.setConstrainedValue(1.0);
		con2.setVariable(x1, 1.0);
		con2.setVariable(x2, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		dualizer.checkInProgram();
		program.checkInMatrices();
		
		x1.getCone().delete();
		x2.getCone().delete();
		x3.getCone().delete();
		
		x1 = program.createNonNegativeOrthantCone().getVariable();
		x2 = program.createNonNegativeOrthantCone().getVariable();
		x3 = program.createNonNegativeOrthantCone().getVariable();
		
		x1.setObjectiveCoefficient(1.0);
		x2.setObjectiveCoefficient(2.0);
		
		con1.setVariable(x1, 1.0);
		con1.setVariable(x2, 1.0);
		con1.setVariable(x3, 1.0);
		
		con2.setVariable(x1, 1.0);
		con2.setVariable(x2, 1.0);
		
		program.checkOutMatrices();
		dualizer.checkOutProgram();
		ConicProgram dualProgram = dualizer.getDualProgram();
		solver.setConicProgram(dualProgram);
		solver.solve();
		dualizer.checkInProgram();
		
		assertEquals(1.0, program.getC().zDotProduct(program.getX()), 10e-5);
	}
}
