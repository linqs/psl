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
 * min  x'L'Lx + f'x 
 * s.t. Ax < b
 * by converting problem to a second order cone program
 * 
 * @author Bert Huang <bert@cs.umd.edu>
 *
 */
public class MinNormProgram {

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


	public MinNormProgram(int size, ConfigBundle config) 
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
	}
	/**
	 * Adds a linear inequality constraint
	 * @param coefficients
	 * @param loss
	 */
	public void addInequalityConstraint(double[] coefficients, double value) {
		assert (coefficients.length == size) : "coefficient and variable vectors must be the same size";

		LinearConstraint con = program.createConstraint();
		for (int i = 0; i < coefficients.length; i++)
			con.setVariable(variables[i], coefficients[i]);
		con.setConstrainedValue(value);
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
	 * 
	 * @param includeInNorm boolean vector indicating whether to include variable in squared norm objective
	 */
	public void setQuadraticCoefficients(boolean [] includeInNorm) {
		int count = 0;
		for (boolean b : includeInNorm)
			if (b) count++;
		
		quadraticCone = program.createSecondOrderCone(count + 1);
		
		Iterator<Variable> coneVars = quadraticCone.getVariables().iterator();
		
		for (int i = 0; i < includeInNorm.length; i++)
			if (includeInNorm[i]) {
				// create equality constraint between 
				Variable coneVar = coneVars.next();

				
				/* 
				 * TODO: Is this the right way to make an equality constraint?
				 * It seems like this will wreak havoc on the barrier functions
				 */
				LinearConstraint upper = program.createConstraint();
				upper.setVariable(variables[i], 1.0);
				upper.setVariable(coneVar, -1.0);
				upper.setConstrainedValue(0.0);

				LinearConstraint lower = program.createConstraint();
				lower.setVariable(variables[i], -1.0);
				lower.setVariable(coneVar, 1.0);
				lower.setConstrainedValue(0.0);
			}
		
		quadraticCone.getNthVariable().setObjectiveCoefficient(0.5);
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
}
