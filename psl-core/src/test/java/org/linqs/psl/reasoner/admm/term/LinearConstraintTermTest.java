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
import org.linqs.psl.reasoner.function.FunctionComparator;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LinearConstraintTermTest {
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
		 * Constraint inactive at solution
		 */
		double[] z = {0.2, 0.5};
		double[] y = {0.0, 0.0};
		double[] coeffs = {1.0, 1.0};
		double constant = 1.0;
		FunctionComparator comparator = FunctionComparator.SmallerThan;
		double stepSize = 1.0;
		double[] expected = {0.2, 0.5};
		testProblem(z, y, coeffs, constant, comparator, stepSize, expected);
		
		/*
		 * Problem 2
		 * 
		 * Constraint active at solution
		 */
		z = new double[] {0.7, 0.5};
		y = new double[] {0.0, 0.0};
		coeffs = new double[] {1.0, 1.0};
		constant = 1.0;
		comparator = FunctionComparator.SmallerThan;
		stepSize = 1.0;
		expected = new double[] {0.6, 0.4};
		testProblem(z, y, coeffs, constant, comparator, stepSize, expected);
		
		/*
		 * Problem 3
		 * 
		 * Equality constraint
		 */
		z = new double[] {0.7, 0.5};
		y = new double[] {0.0, 0.0};
		coeffs = new double[] {1.0, -1.0};
		constant = 0.0;
		comparator = FunctionComparator.Equality;
		stepSize = 1.0;
		expected = new double[] {0.6, 0.6};
		testProblem(z, y, coeffs, constant, comparator, stepSize, expected);
	}
	
	private void testProblem(double[] z, double[] y, double[] coeffs, double constant,
			FunctionComparator comparator, final double stepSize, double[] expected) {
		List<LocalVariable> variables = new ArrayList<LocalVariable>(z.length);
		List<Double> coeffsList = new ArrayList<Double>(z.length);

		for (int i = 0; i < z.length; i++) {
			variables.add(new LocalVariable(i, z[i]));
			variables.get(i).setLagrange(y[i]);

			coeffsList.add(new Double(coeffs[i]));
		}
		
		LinearConstraintTerm term = new LinearConstraintTerm(variables, coeffsList, constant, comparator);
		term.minimize(stepSize, z);
		
		for (int i = 0; i < z.length; i++) {
			assertEquals(expected[i], variables.get(i).getValue(), 5e-5);
		}
	}
}
