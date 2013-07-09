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
package edu.umd.cs.psl.reasoner.admm;

import static org.junit.Assert.assertEquals;

import java.util.Vector;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

public class LinearConstraintTermTest {
	
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
		config.setProperty("admmreasoner.stepsize", stepSize);
		ADMMReasoner reasoner = new ADMMReasoner(config);
		reasoner.z = new Vector<Double>(z.length);
		for (int i = 0; i < z.length; i++)
			reasoner.z.add(z[i]);
		
		int[] zIndices = new int[z.length];
		for (int i = 0; i < z.length; i++)
			zIndices[i] = i;
		
		LinearConstraintTerm term = new LinearConstraintTerm(reasoner, zIndices, coeffs, constant, comparator);
		for (int i = 0; i < z.length; i++)
			term.y[i] = y[i];
		term.minimize();
		
		for (int i = 0; i < z.length; i++)
			assertEquals(expected[i], term.x[i], 5e-5);
	}

}
