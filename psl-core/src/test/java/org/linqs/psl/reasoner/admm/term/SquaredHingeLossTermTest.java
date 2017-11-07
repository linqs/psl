/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.reasoner.admm.term;

import static org.junit.Assert.assertEquals;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SquaredHingeLossTermTest {
	
	private ConfigBundle config;
	
	@Before
	public final void setUp() throws ConfigurationException {
		config = ConfigManager.getManager().getBundle("dummy");
	}
	
	@Test
	public void testMinimize() {
		
		/*
		 * Problem 1
		 * 
		 * Solution on the quadratic side
		 */
		double[] z = {0.2, 0.5};
		double[] y = {0.0, 0.0};
		double[] coeffs = {1.0, -1.0};
		double constant = -0.95;
		double weight = 1.0;
		double stepSize = 1.0;
		double[] expected = {-0.06, 0.76};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		
		/*
		 * Problem 2
		 * 
		 * Solution on the quadratic side
		 */
		z = new double[] {0.3, 0.5, 0.1};
		y = new double[] {0.1, 0.0, -0.05};
		coeffs = new double[] {1.0, -0.5, 0.4};
		constant = -0.15;
		weight = 1.0;
		stepSize = 0.5;
		expected = new double[] {0.051798, 0.524096, 0.180720};
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
		 * Solution on the quadratic side
		 */
		z = new double[] {0.1};
		y = new double[] {-0.15};
		coeffs = new double[] {1.0};
		constant = 0.0;
		weight = 2.0;
		stepSize = 1.0;
		expected = new double[] {0.05};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		
		/*
		 * Problem 5
		 * 
		 * Solution on the quadratic side
		 */
		z = new double[] {0.7, .5};
		y = new double[] {0.0, 0.0};
		coeffs = new double[] {1.0, -1.0};
		constant = 0.0;
		weight = 1.0;
		stepSize = 1.0;
		expected = new double[] {0.62, 0.58};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		
		/*
		 * Problem 6
		 * 
		 * Solution on the quadratic side
		 * 
		 * Tests factorization caching by repeating the test three times
		 */
		z = new double[] {3.7, -.5, .5};
		y = new double[] {0.0, 0.0, 0.0};
		coeffs = new double[] {1.0, -1.0, 0.5};
		constant = -0.5;
		weight = 2.0;
		stepSize = 2.0;
		expected = new double[] {1.9, 1.3, -0.4};
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
		testProblem(z, y, coeffs, constant, weight, stepSize, expected);
	}
	
	private void testProblem(double[] z, double[] y,double[] coeffs, double constant,
			double weight, final double stepSize , double[] expected) {
		List<LocalVariable> variables = new ArrayList<LocalVariable>(z.length);
		List<Double> coeffsList = new ArrayList<Double>(z.length);

		for (int i = 0; i < z.length; i++) {
			variables.add(new LocalVariable(i, z[i]));
			variables.get(i).setLagrange(y[i]);

			coeffsList.add(new Double(coeffs[i]));
		}
		
		SquaredHingeLossTerm term = new SquaredHingeLossTerm(variables, coeffsList, constant, weight);
		term.minimize(stepSize, z);
		
		for (int i = 0; i < z.length; i++) {
			assertEquals(expected[i], variables.get(i).getValue(), 5e-5);
		}
	}
}
