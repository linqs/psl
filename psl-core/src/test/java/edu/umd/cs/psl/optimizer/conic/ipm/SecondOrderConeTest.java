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
package edu.umd.cs.psl.optimizer.conic.ipm;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import cern.colt.matrix.tdouble.DoubleMatrix1D;

import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;

public class SecondOrderConeTest {
	
	private ConicProgram program;

	@Before
	public void setUp() {
		program = new ConicProgram();
	}

	@Test
	public void testGetMaxStep() {
		SecondOrderCone coneA = program.createSecondOrderCone(2);
		
		program.checkOutMatrices();
		
		DoubleMatrix1D x = program.getX();
		x.set(0, 0.0);
		x.set(1, 1.0);
		
		DoubleMatrix1D dx = x.copy();
		dx.set(0, 0.0);
		dx.set(1, -1.1);
		
		double maxStep = coneA.getMaxStep(program.getVarMap(), x, dx);
		assertTrue(1 - 1.1 * maxStep > 0.0);
	}

}
