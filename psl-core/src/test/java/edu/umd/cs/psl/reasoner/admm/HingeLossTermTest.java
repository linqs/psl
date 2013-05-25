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
package edu.umd.cs.psl.reasoner.admm;

import static org.junit.Assert.assertEquals;

import java.util.Vector;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;

public class HingeLossTermTest {
	
	private ConfigBundle config;
	
	@Before
	public final void setUp() throws ConfigurationException {
		ConfigManager manager = ConfigManager.getManager();
		config = manager.getBundle("dummy");
	}
	
	@Test
	public void testMinimize() {
		
		/*
		 * Problem 1
		 * 
		 * Solution on the hinge
		 */
		double[] z = {0.2, 0.5};
		double[] y = {0.0, 0.0};
		double[] coeffs = {1.0, -1.0};
		double constant = -0.95;
		double weight = 1.0;
		double stepSize = 1.0;
		double[] expected = {-.125, 0.825};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		
		/*
		 * Problem 2
		 * 
		 * Solution on the hinge
		 */
		z = new double[] {0.3, 0.5, 0.1};
		y = new double[] {0.1, 0.0, -0.05};
		coeffs = new double[] {1.0, -0.5, 0.4};
		constant = -0.15;
		weight = 1.0;
		stepSize = 0.5;
		expected = new double[] {0.043257, 0.528361, 0.177309};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		
		
		
		/*
		 * Problem 3
		 * 
		 * Solution on the zero side
		 */
		z = new double[] {0.3, 0.5, 0.1};
		y = new double[] {0.1, 0.0, -0.05};
		coeffs = new double[] {1.0, -0.5, 0.4};
		constant = 0.0;
		weight = 2.0;
		stepSize = 0.5;
		expected = new double[] {0.1, 0.5, 0.2};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		
		/*
		 * Problem 4
		 * 
		 * Solution on the zero side
		 */
		z = new double[] {0.1};
		y = new double[] {0.15};
		coeffs = new double[] {1.0};
		constant = 0.0;
		weight = 2.0;
		stepSize = 1.0;
		expected = new double[] {-0.05};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		
		/*
		 * Problem 5
		 * 
		 * Solution on the linear side
		 */
		z = new double[] {0.7, .5};
		y = new double[] {0.0, 0.0};
		coeffs = new double[] {1.0, -1.0};
		constant = 0.0;
		weight = 1.0;
		stepSize = 1.0;
		expected = new double[] {0.6, 0.6};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		
		/*
		 * Problem 6
		 * 
		 * Solution on the hinge, two variables, non-1.0 stepsize and non-0.0 dual
		 * variables
		 */
		z = new double[] {0.7, 0.5};
		y = new double[] {0.05, 1.0};
		coeffs = new double[] {1.0, -1.0};
		constant = -0.5;
		weight = 2.0;
		stepSize = 2.0;
		expected = new double[] {0.0875, 0.5875};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
	}
	
	private void testProblem(double[] z, double[] y, double[] coeffs, double constant,
			double weight, final double stepSize, double[] expected) {
		config.setProperty("admmreasoner.stepsize", stepSize);
		ADMMReasoner reasoner = new ADMMReasoner(config);
		reasoner.z = new Vector<Double>(z.length);
		for (int i = 0; i < z.length; i++)
			reasoner.z.add(z[i]);
		
		int[] zIndices = new int[z.length];
		for (int i = 0; i < z.length; i++)
			zIndices[i] = i;
		
		HingeLossTerm term = new HingeLossTerm(reasoner, zIndices, coeffs, constant, weight);
		for (int i = 0; i < z.length; i++)
			term.y[i] = y[i];
		term.minimize();
		
		for (int i = 0; i < z.length; i++)
			assertEquals(expected[i], term.x[i], 5e-5);
	}

}
