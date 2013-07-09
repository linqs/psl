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
package edu.umd.cs.psl.application.learning.weight.maxmargin;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolverFactory;
import edu.umd.cs.psl.optimizer.conic.ipm.HomogeneousIPMFactory;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

/**
 * (for now) Solves convex programs of the form
 * min  ||weights .* x - origin||^2 + f'x 
 * s.t. Ax < b, x >= 0
 * by converting problem to a second order cone program
 * 
 * @author Bert Huang <bert@cs.umd.edu>
 */
public class PositiveMinNormProgram {
	Logger log = LoggerFactory.getLogger(PositiveMinNormProgram.class);

	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "quadprog";

	/**
	 * Key for {@link edu.umd.cs.psl.config.Factory} or String property.
	 * 
	 * Should be set to a {@link edu.umd.cs.psl.optimizer.conic.ConicProgramSolverFactory}
	 * (or the binary name of one). The ConicReasoner will use this
	 * {@link edu.umd.cs.psl.optimizer.conic.ConicProgramSolverFactory} to
	 * instantiate a {@link edu.umd.cs.psl.optimizer.conic.ConicProgramSolver},
	 * which will then be used for inference.
	 */
	public static final String CPS_KEY = CONFIG_PREFIX + ".conicprogramsolver";
	/**
	 * Default value for CPS_KEY property.
	 * 
	 * Value is instance of {@link edu.umd.cs.psl.optimizer.conic.ipm.HomogeneousIPMFactory}.
	 */
	public static final ConicProgramSolverFactory CPS_DEFAULT = new HomogeneousIPMFactory();


	public PositiveMinNormProgram(int size, ConfigBundle config) 
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		this.size = size;
		program = new ConicProgram();
		ConicProgramSolverFactory cpsFactory = (ConicProgramSolverFactory) config.getFactory(CPS_KEY, CPS_DEFAULT);
		
		solver = cpsFactory.getConicProgramSolver(config);
		variables = new Variable[size];
		for (int i = 0; i < size; i++) {
			NonNegativeOrthantCone cone = program.createNonNegativeOrthantCone();
			variables[i] = cone.getVariable();
		}
		squaredNorm = program.createNonNegativeOrthantCone().getVariable();
		squaredNorm.setObjectiveCoefficient(0.5);
	}
	/**
	 * Adds a linear inequality constraint
	 * @param coefficients
	 * @param loss
	 */
	public void addInequalityConstraint(double[] coefficients, double value) {
		//assert (coefficients.length == size) : "coefficient and variable vectors must be the same size";

		NonNegativeOrthantCone cone = program.createNonNegativeOrthantCone();
		Variable slack = cone.getVariable();
		
		LinearConstraint constraint = program.createConstraint();
		for (int i = 0; i < coefficients.length; i++)
			constraint.setVariable(variables[i], coefficients[i]);
		constraint.setConstrainedValue(value);
		constraint.setVariable(slack, 1.0);
	}

	/**
	 * solves current conic program
	 */
	public void solve() {
		normalizeCoefficients();
		
		solver.setConicProgram(program);
		solver.solve();
	}

	public double [] getSolution() {
		double [] solution = new double[size];
		for (int i = 0; i < size; i++)
			solution[i] = variables[i].getValue();
		return solution;
	}

	/**
	 * set f in x'A'Ax + f'x
	 * @param coefficients
	 */
	public void setLinearCoefficients(double[] coefficients) {
		//assert (coefficients.length == size) : "coefficient and variable vectors must be the same size";
		for (int i = 0; i < size; i++) 
			variables[i].setObjectiveCoefficient(coefficients[i]);
	}

	/**
	 * Sets the quadratic term in the norm minimization objective to be
	 * ||weight .* x - origin||^2
	 * 
	 * @param weights  positive weights to be multiplied element-wise with x
	 * @param origin  origin of the vector to compute the norm of.
	 *                    Set to all zeros to get traditional norm minimization
	 */
	public void setQuadraticTerm(double[] weights, double [] origin) {
		int count = 0;
		for (double w : weights)
			if (w > 0.0)
				count++;
			else if (w < 0.0)
				throw new IllegalArgumentException("Weights must be non-negative.");
		
		/* Clears quadratic cone if it already exists */
		if (quadraticCone != null) {
			for (Variable v : quadraticCone.getVariables())
				for (LinearConstraint c : v.getLinearConstraints())
					c.delete();
			quadraticCone.delete();
		}
		
		/* Constructs new quadratic cone */
		quadraticCone = program.createSecondOrderCone(count + 2);
		
		Iterator<Variable> coneVars = quadraticCone.getInnerVariables().iterator();
		
		Variable leftDummy = coneVars.next();
		LinearConstraint constraint = program.createConstraint();
		constraint.setVariable(leftDummy,  1.0);
		constraint.setVariable(squaredNorm, 0.5);
		constraint.setConstrainedValue(0.5);
		
		for (int i = 0; i < weights.length; i++)
			if (weights[i] != 0.0) {
				// create equality constraint
				Variable coneVar = coneVars.next();
				LinearConstraint variableConstraint = program.createConstraint();
				variableConstraint.setVariable(coneVar, -1.0);
				variableConstraint.setVariable(variables[i], weights[i]);
				variableConstraint.setConstrainedValue(weights[i] * origin[i]);
			}
		
		Variable rightDummy = quadraticCone.getNthVariable();
		constraint = program.createConstraint();
		constraint.setVariable(rightDummy, 1.0);
		constraint.setVariable(squaredNorm, -0.5);
		constraint.setConstrainedValue(0.5);
		
	}
	
	/**
	 * detaches all saved references
	 */
	public void close() {
		program = null;
		solver = null;
		for (int i = 0; i < size; i++)
			variables[i] = null;
		quadraticCone = null;
	}

	/**
	 * scales all objective coefficients to try to improve numerical stability
	 */
	private void normalizeCoefficients() {
		double max = 0.0;
		for (int i = 0; i < size; i++)
			max = Math.max(max, variables[i].getObjectiveCoefficient());
		max = Math.max(max, squaredNorm.getObjectiveCoefficient());
		
		// normalize
		squaredNorm.setObjectiveCoefficient(squaredNorm.getObjectiveCoefficient() / max);
		for (int i = 0; i < size; i++) 
			variables[i].setObjectiveCoefficient(variables[i].getObjectiveCoefficient() / max);
	}
	
	private int size;
	private ConicProgram program;
	private ConicProgramSolver solver;
	private Variable [] variables;
	private SecondOrderCone quadraticCone;
	private Variable squaredNorm;
}
