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
package edu.umd.cs.psl.optimizer.conic.program;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import cern.colt.matrix.tdouble.DoubleMatrix1D;

/**
 * Tests {@link ConicProgram}. 
 */
public class ConicProgramTest {
	
	private ConicProgram program;
	private Variable x1, x2;
	
	@Before
	public final void setUp() throws Exception {
		program = new ConicProgram();
	}
	
	private void defineSOCP() {
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
		
		phi1.setVariable(x1, 1.0);
		phi1.setVariable(x3, 1.0);
		phi1.setVariable(x4, -1.0);
		
		phi2.setVariable(x1, -1.0);
		phi2.setVariable(x2, 1.0);
		phi2.setVariable(x5, 1.0);
		phi2.setVariable(x6, -1.0);

		phi3.setVariable(x2, -1.0);
		phi3.setVariable(x7, 1.0);
		phi3.setVariable(x8, -1.0);
		
		c1.setVariable(x1, 1.0);
		c1.setVariable(x9, 1.0);
		
		c2.setVariable(x2, 1.0);
		c2.setVariable(x10, 1.0);
		
		phi1.setConstrainedValue(0.7);
		phi2.setConstrainedValue(0.0);
		phi3.setConstrainedValue(-0.2);
		c1.setConstrainedValue(1.0);
		c2.setConstrainedValue(1.0);
		
		/* Squares the variable x3 in the phi1 constraint */
		
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
		phi1InnerFeatureCon.setVariable(x3, 1.0);
		phi1InnerFeatureCon.setVariable(phi1InnerFeatureVar, -1.0);
		phi1InnerFeatureCon.setConstrainedValue(0.0);
		
		LinearConstraint phi1InnerSquaredCon = program.createConstraint();
		phi1InnerSquaredCon.setVariable(phi1InnerSquaredVar, 1.0);
		phi1InnerSquaredCon.setVariable(x3Sq, 0.5);
		phi1InnerSquaredCon.setConstrainedValue(0.5);
		
		LinearConstraint phi1OuterSquaredCon = program.createConstraint();
		phi1OuterSquaredCon.setVariable(phi1OuterSquaredVar, 1.0);
		phi1OuterSquaredCon.setVariable(x3Sq, -0.5);
		phi1OuterSquaredCon.setConstrainedValue(0.5);
		
		/* Squares the variable x5 in the phi2 constraint */
		
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
		phi2InnerFeatureCon.setVariable(x5, 1.0);
		phi2InnerFeatureCon.setVariable(phi2InnerFeatureVar, -1.0);
		phi2InnerFeatureCon.setConstrainedValue(0.0);
		
		LinearConstraint phi2InnerSquaredCon = program.createConstraint();
		phi2InnerSquaredCon.setVariable(phi2InnerSquaredVar, 1.0);
		phi2InnerSquaredCon.setVariable(x5Sq, 0.5);
		phi2InnerSquaredCon.setConstrainedValue(0.5);
		
		LinearConstraint phi2OuterSquaredCon = program.createConstraint();
		phi2OuterSquaredCon.setVariable(phi2OuterSquaredVar, 1.0);
		phi2OuterSquaredCon.setVariable(x5Sq, -0.5);
		phi2OuterSquaredCon.setConstrainedValue(0.5);
		
		/* Squares the variable x7 in the phi3 constraint */
		
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
		phi3InnerFeatureCon.setVariable(x7, 1.0);
		phi3InnerFeatureCon.setVariable(phi3InnerFeatureVar, -1.0);
		phi3InnerFeatureCon.setConstrainedValue(0.0);
		
		LinearConstraint phi3InnerSquaredCon = program.createConstraint();
		phi3InnerSquaredCon.setVariable(phi3InnerSquaredVar, 1.0);
		phi3InnerSquaredCon.setVariable(x7Sq, 0.5);
		phi3InnerSquaredCon.setConstrainedValue(0.5);
		
		LinearConstraint phi3OuterSquaredCon = program.createConstraint();
		phi3OuterSquaredCon.setVariable(phi3OuterSquaredVar, 1.0);
		phi3OuterSquaredCon.setVariable(x7Sq, -0.5);
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

	/** Tests the creation of a second-order cone program. */
	@Test
	public void testCreateSOCP() {
		defineSOCP();

		assertTrue(program.getNumNNOC() == 13);
		assertTrue(program.gtNumSOC() == 3);
		assertTrue(program.getNumRSOC() == 0);
		
		assertTrue(program.getNonNegativeOrthantCones().size() == 13);
		assertTrue(program.getSecondOrderCones().size() == 3);
		assertTrue(program.getCones().size() == 16);

		assertTrue(program.getConstraints().size() == 14);
	}

	/** Tests checking out matrices for a second-order cone program. */
	@Test
	public void testCheckOutSOCP() {
		defineSOCP();
		
		program.checkOutMatrices();
		
		assertTrue(program.getA().rows() == 14);
		assertTrue(program.getA().columns() == 22);
		assertTrue(program.getX().size() == 22);
		assertTrue(program.getB().size() == 14);
		assertTrue(program.getW().size() == 14);
		assertTrue(program.getS().size() == 22);
		assertTrue(program.getC().size() == 22);
		
		assertTrue(program.getC().cardinality() == 3);
	}
	
	/** Tests checking in matrices for a second-order cone program. */
	@Test
	public void testCheckInSOCP() {
		defineSOCP();
		
		double newPrimalValue1 = x1.getValue() + 1.0;
		double newPrimalValue2 = x2.getValue() + 2.0;
		double newDualValue1 = x1.getDualValue() + 1.0;
		double newDualValue2 = x2.getDualValue() + 2.0;
		
		program.checkOutMatrices();
		int index1 = program.getIndex(x1);
		int index2 = program.getIndex(x2);
		DoubleMatrix1D x = program.getX();
		x.set(index1, newPrimalValue1);
		x.set(index2, newPrimalValue2);
		DoubleMatrix1D s = program.getS();
		s.set(index1, newDualValue1);
		s.set(index2, newDualValue2);
		program.checkInMatrices();
		
		assertTrue(x1.getValue() == newPrimalValue1);
		assertTrue(x2.getValue() == newPrimalValue2);
		assertTrue(x1.getDualValue() == newDualValue1);
		assertTrue(x2.getDualValue() == newDualValue2);
		
		newPrimalValue1 = x1.getValue() + 1.0;
		newPrimalValue2 = x2.getValue() + 2.0;
		newDualValue1 = x1.getDualValue() + 1.0;
		newDualValue2 = x2.getDualValue() + 2.0;
		
		x1.setValue(newPrimalValue1);
		x2.setValue(newPrimalValue2);
		x1.setDualValue(newDualValue1);
		x2.setDualValue(newDualValue2);
		
		program.checkOutMatrices();
		program.checkInMatrices();
		
		assertTrue(x1.getValue() == newPrimalValue1);
		assertTrue(x2.getValue() == newPrimalValue2);
		assertTrue(x1.getDualValue() == newDualValue1);
		assertTrue(x2.getDualValue() == newDualValue2);
	}
	
	/** Tests deleting the components of a second-order cone program. */
	@Test
	public void testDeleteSOCP() {
		defineSOCP();
		
		for (Cone cone : program.getCones())
			cone.delete();
				
		for (LinearConstraint lc : program.getConstraints())
			lc.delete();
				
		assertTrue(program.getNumNNOC() == 0);
		assertTrue(program.gtNumSOC() == 0);
		assertTrue(program.getNumRSOC() == 0);
		
		assertTrue(program.getNonNegativeOrthantCones().size() == 0);
		assertTrue(program.getSecondOrderCones().size() == 0);
		assertTrue(program.getCones().size() == 0);

		assertTrue(program.getConstraints().size() == 0);
	}
	
	/** Tests creating, deleting, and then recreating a second-order cone program. */
	@Test
	public void testRecreateSOCP() {
		defineSOCP();
		
		for (Cone cone : program.getCones())
			cone.delete();
				
		for (LinearConstraint lc : program.getConstraints())
			lc.delete();
				
		defineSOCP();
				
		assertTrue(program.getNumNNOC() == 13);
		assertTrue(program.gtNumSOC() == 3);
		assertTrue(program.getNumRSOC() == 0);
		
		assertTrue(program.getNonNegativeOrthantCones().size() == 13);
		assertTrue(program.getSecondOrderCones().size() == 3);
		assertTrue(program.getCones().size() == 16);

		assertTrue(program.getConstraints().size() == 14);
		
		program.checkOutMatrices();
		
		assertTrue(program.getA().rows() == 14);
		assertTrue(program.getA().columns() == 22);
		assertTrue(program.getX().size() == 22);
		assertTrue(program.getB().size() == 14);
		assertTrue(program.getW().size() == 14);
		assertTrue(program.getS().size() == 22);
		assertTrue(program.getC().size() == 22);
		
		assertTrue(program.getC().cardinality() == 3);
	}
	
	/** Tests creating more non-negative orthant cones after checking matrices in. */
	@Test
	public void testCreateNNOCAfterCheckIn() {
		defineSOCP();
		
		program.checkOutMatrices();
		program.checkInMatrices();
		
		assertTrue(program.getNumNNOC() == 13);
		assertTrue(program.gtNumSOC() == 3);
		assertTrue(program.getNumRSOC() == 0);
		
		program.createNonNegativeOrthantCone();
		program.createNonNegativeOrthantCone();
		
		assertTrue(program.getNonNegativeOrthantCones().size() == 15);
		assertTrue(program.getSecondOrderCones().size() == 3);
		assertTrue(program.getCones().size() == 18);

		assertTrue(program.getConstraints().size() == 14);
	}
	
	/** Tests creating more constraints after checking matrices in. */
	@Test
	public void testCreateConstraintAfterCheckIn() {
		defineSOCP();
		
		program.checkOutMatrices();
		program.checkInMatrices();
		
		assertTrue(program.getNumNNOC() == 13);
		assertTrue(program.gtNumSOC() == 3);
		assertTrue(program.getNumRSOC() == 0);
		
		program.createConstraint().setVariable(x1, 1.0);
		program.createConstraint();
		
		assertTrue(program.getNonNegativeOrthantCones().size() == 13);
		assertTrue(program.getSecondOrderCones().size() == 3);
		assertTrue(program.getCones().size() == 16);

		assertTrue(program.getConstraints().size() == 16);
	}
	
	/** Tests checking out matrices after checking them in and modifying the program. */
	@Test
	public void testCheckOutModifiedSOCP() {
		defineSOCP();
		
		program.checkOutMatrices();
		program.checkInMatrices();
		
		program.createNonNegativeOrthantCone();
		program.createNonNegativeOrthantCone();
		
		program.createConstraint().setVariable(x1, 1.0);
		program.createConstraint().setVariable(x2, 1.0);
		
		program.checkOutMatrices();
		
		assertTrue(program.getA().rows() == 16);
		assertTrue(program.getA().columns() == 24);
		assertTrue(program.getX().size() == 24);
		assertTrue(program.getB().size() == 16);
		assertTrue(program.getW().size() == 16);
		assertTrue(program.getS().size() == 24);
		assertTrue(program.getC().size() == 24);
		
		assertTrue(program.getC().cardinality() == 3);
	}
	
	/** Tests adding the same variable twice to a linear constraint. */
	@Test
	public void testAddDuplicateVariableToConstraint() {
		Variable x = program.createNonNegativeOrthantCone().getVariable();
		LinearConstraint lc = program.createConstraint();
		
		lc.setVariable(x, 1.0);
		lc.setVariable(x, -1.0);
		
		assertTrue(lc.getVariables().size() == 1);
		assertTrue(lc.getVariables().get(x) == -1.0);
	}
}

