package edu.umd.cs.psl.application.learning.weight;

import java.util.Iterator;

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
 * min  ||x||^2 + f'x 
 * s.t. Ax < b, x >= 0
 * by converting problem to a second order cone program
 * 
 * @author Bert Huang <bert@cs.umd.edu>
 *
 */
public class PositiveMinNormProgram {

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
		solver.setConicProgram(program);
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
		assert (coefficients.length == size) : "coefficient and variable vectors must be the same size";

		NonNegativeOrthantCone cone = program.createNonNegativeOrthantCone();
		Variable slack = cone.getVariable();
		
		LinearConstraint constraint = program.createConstraint();
		for (int i = 0; i < coefficients.length; i++)
			constraint.setVariable(variables[i], coefficients[i]);
		constraint.setConstrainedValue(value);
		constraint.setVariable(slack, -1.0);
	}

	/**
	 * solve
	 */
	public void solve() {
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
		assert (coefficients.length == size) : "coefficient and variable vectors must be the same size";

		for (int i = 0; i < size; i++) 
			variables[i].setObjectiveCoefficient(coefficients[i]);
	}

	/**
	 * sets the quadratic term in the norm minimization objective. 
	 * @param includeInNorm boolean indicator of which variables to include in norm
	 * @param origin origin of the vector to compute the norm of. Set to all zeros to get traditional max-margin norm minimization
	 */
	public void setQuadraticTerm(boolean [] includeInNorm, double [] origin) {
		int count = 0;
		for (boolean b : includeInNorm)
			if (b) count++;
		
		quadraticCone = program.createSecondOrderCone(count + 2);
		
		Iterator<Variable> coneVars = quadraticCone.getVariables().iterator();
		
		Variable leftDummy = coneVars.next();
		LinearConstraint constraint = program.createConstraint();
		constraint.setVariable(leftDummy,  1.0);
		constraint.setVariable(squaredNorm, 0.5);
		constraint.setConstrainedValue(0.5);
		
		for (int i = 0; i < includeInNorm.length; i++)
			if (includeInNorm[i]) {
				// create equality constraint between 
				Variable coneVar = coneVars.next();
				LinearConstraint variableConstraint = program.createConstraint();
				variableConstraint.setVariable(coneVar, 1.0);
				variableConstraint.setVariable(variables[i], -1.0);
				variableConstraint.setConstrainedValue(origin[i]);
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

	private int size;
	private ConicProgram program;
	private ConicProgramSolver solver;
	private Variable [] variables;
	private SecondOrderCone quadraticCone;
	private Variable squaredNorm;
}
