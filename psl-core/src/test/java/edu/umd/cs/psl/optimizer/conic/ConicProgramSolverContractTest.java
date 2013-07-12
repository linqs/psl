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
package edu.umd.cs.psl.optimizer.conic;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.junit.Before;
import org.junit.Test;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

/**
 * Contract tests for classes that implement {@link ConicProgramSolver}.
 */
abstract public class ConicProgramSolverContractTest {
	
	private static final double SOLUTION_TOLERANCE = 0.001;
	
	private List<? extends ConicProgramSolver> solvers;
	
	private Vector<ConicProgram> programs;
	private Vector<Map<Variable, Double>> solutions;
	
	@Before
	public final void setUp()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		solvers = getConicProgramSolverImplementations();
		programs = new Vector<ConicProgram>();
		solutions = new Vector<Map<Variable, Double>>();
	}

	abstract protected List<? extends ConicProgramSolver> getConicProgramSolverImplementations()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException;
	
	@Test
	public void testSolve() {
		ConicProgram program;
		ConicProgramSolver solver;
		Map<Variable, Double> solution;
		DoubleMatrix1D solutionMatrix;
		
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		Iterator<? extends ConicProgramSolver> itr = solvers.iterator();
		
		while (itr.hasNext()) {
			solver = itr.next();
			addLP();
			addSOCP();
			
			for (int i = 0; i < programs.size(); i++) {
				program = programs.get(i);
				solution = solutions.get(i);
				
				if (solver.supportsConeTypes(program.getConeTypes())) {
					solver.setConicProgram(program);
					solver.solve();
					program.checkOutMatrices();
					solutionMatrix = new DenseDoubleMatrix1D((int) program.getX().size());
					
					for (Map.Entry<Variable, Double> e : solution.entrySet()) {
						solutionMatrix.set(program.getIndex(e.getKey()), e.getValue());
					}
					assertTrue(alg.norm2(solutionMatrix.assign(program.getX(), DoubleFunctions.minus)) < SOLUTION_TOLERANCE);
					program.checkInMatrices();
				}
			}
			
			programs.clear();
			solutions.clear();
		}
	}
	
	/** Tests solving a program after it has been solved once and modified. */
	@Test
	public void testSolveAfterModification() {
		ConicProgram program;
		ConicProgramSolver solver;
		Map<Variable, Double> solution;
		DoubleMatrix1D solutionMatrix;
		
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		Iterator<? extends ConicProgramSolver> itr = solvers.iterator();
		
		while (itr.hasNext()) {
			solver = itr.next();
			program = new ConicProgram();
			
			LinearConstraint phi1 = (LinearConstraint) program.createConstraint();
			LinearConstraint phi2 = (LinearConstraint) program.createConstraint();
			LinearConstraint phi3 = (LinearConstraint) program.createConstraint();
			LinearConstraint c1 = (LinearConstraint) program.createConstraint();
			LinearConstraint c2 = (LinearConstraint) program.createConstraint();

			Variable x1 = program.createNonNegativeOrthantCone().getVariable();
			Variable x2 = program.createNonNegativeOrthantCone().getVariable();
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
			
			solver.setConicProgram(program);
			solver.solve();
			
			Variable x11 = program.createNonNegativeOrthantCone().getVariable();
			LinearConstraint c3 = program.createConstraint();
			c3.setVariable(x2, 1.0);
			c3.setVariable(x11, 1.0);
			c3.setConstrainedValue(0.1);
			
			solver.solve();
			
			solution = new HashMap<Variable, Double>();
			solution.put(x1, 0.1);
			solution.put(x2, 0.1);
			solution.put(x3, 0.6);
			solution.put(x4, 0.0);
			solution.put(x5, 0.0);
			solution.put(x6, 0.0);
			solution.put(x7, 0.0);
			solution.put(x8, 0.1);
			solution.put(x9, 0.9);
			solution.put(x10, 0.9);
			solution.put(x11, 0.0);
			
			program.checkOutMatrices();
			solutionMatrix = new DenseDoubleMatrix1D((int) program.getX().size());
			
			for (Map.Entry<Variable, Double> e : solution.entrySet()) {
				solutionMatrix.set(program.getIndex(e.getKey()), e.getValue());
			}
			assertTrue(alg.norm2(solutionMatrix.assign(program.getX(), DoubleFunctions.minus)) < SOLUTION_TOLERANCE);
			program.checkInMatrices();
		}
	}
	
	private void addLP() {
		ConicProgram program = new ConicProgram();
		
		LinearConstraint phi1 = (LinearConstraint) program.createConstraint();
		LinearConstraint phi2 = (LinearConstraint) program.createConstraint();
		LinearConstraint phi3 = (LinearConstraint) program.createConstraint();
		LinearConstraint c1 = (LinearConstraint) program.createConstraint();
		LinearConstraint c2 = (LinearConstraint) program.createConstraint();

		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
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
		
		programs.add(program);
		
		Map<Variable, Double> solution = new HashMap<Variable, Double>();
		solution.put(x1, 0.2);
		solution.put(x2, 0.2);
		solution.put(x3, 0.5);
		solution.put(x4, 0.0);
		solution.put(x5, 0.0);
		solution.put(x6, 0.0);
		solution.put(x7, 0.0);
		solution.put(x8, 0.0);
		solution.put(x9, 0.8);
		solution.put(x10, 0.8);
		
		solutions.add(solution);
	}
	
	private void addSOCP() {
		ConicProgram program = new ConicProgram();
		
		LinearConstraint phi1 = (LinearConstraint) program.createConstraint();
		LinearConstraint phi2 = (LinearConstraint) program.createConstraint();
		LinearConstraint phi3 = (LinearConstraint) program.createConstraint();
		LinearConstraint c1 = (LinearConstraint) program.createConstraint();
		LinearConstraint c2 = (LinearConstraint) program.createConstraint();
		
		Variable x1 = program.createNonNegativeOrthantCone().getVariable();
		Variable x2 = program.createNonNegativeOrthantCone().getVariable();
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
		
		programs.add(program);
		
		Map<Variable, Double> solution = new HashMap<Variable, Double>();
		
		solution.put(x1, 0.4273);
		solution.put(x2, 0.2909);
		solution.put(x3, 0.2727);
		solution.put(x4, 0.0);
		solution.put(x5, 0.1363);
		solution.put(x6, 0.0);
		solution.put(x7, 0.0909);
		solution.put(x8, 0.0);
		solution.put(x9, 0.5727);
		solution.put(x10, 0.7091);
		
		solution.put(phi1InnerFeatureVar, 0.2727);
		solution.put(phi1InnerSquaredVar, 0.4628);
		solution.put(phi1OuterSquaredVar, 0.5372);

		solution.put(phi2InnerFeatureVar, 0.1364);
		solution.put(phi2InnerSquaredVar, 0.4907);
		solution.put(phi2OuterSquaredVar, 0.5093);

		solution.put(phi3InnerFeatureVar, 0.0909);
		solution.put(phi3InnerSquaredVar, 0.4959);
		solution.put(phi3OuterSquaredVar, 0.5041);

		solution.put(x3Sq, 0.0744);
		solution.put(x5Sq, 0.0186);
		solution.put(x7Sq, 0.0083);
		
		solutions.add(solution);
	}
}
