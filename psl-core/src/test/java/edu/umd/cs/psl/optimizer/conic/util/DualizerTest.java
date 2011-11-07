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
package edu.umd.cs.psl.optimizer.conic.util;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class DualizerTest {
	
	private Dualizer dualizer;
	private ConicProgram program;
	
	@Before
	public final void setUp() {
		program = new ConicProgram();
		dualizer = new Dualizer(program);
	}
	
	@Test
	public void testCheckOutAndIn() {
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
		Variable x3 = program.createNonNegativeOrthantCone().getVariable();
		LinearConstraint con1 = program.createConstraint();
		LinearConstraint con2 = program.createConstraint();
		con1.addVariable(x1, 1.0);
		con1.addVariable(x2, 1.0);
		con1.setConstrainedValue(2.0);
		con2.addVariable(x1, 1.0);
		con2.addVariable(x3, -1.0);
		con2.setConstrainedValue(1.0);
		
		program.checkOutMatrices();
		program.getX().set(program.index(x1), 2.0);
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
}
