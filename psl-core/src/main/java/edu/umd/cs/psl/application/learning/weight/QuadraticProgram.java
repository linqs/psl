package edu.umd.cs.psl.application.learning.weight;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolverFactory;
import edu.umd.cs.psl.optimizer.conic.ipm.HomogeneousIPMFactory;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;

/**
 * (for now) Solves convex programs of the form
 * min  x'L'Lx + f'x 
 * s.t. Ax < b
 * by converting problem to a second order cone program
 * 
 * @author Bert Huang <bert@cs.umd.edu>
 *
 */
public class QuadraticProgram {
	
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
	
	
	public QuadraticProgram(int size, ConfigBundle config) 
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		this.size = size;
		program = new ConicProgram();
		ConicProgramSolverFactory cpsFactory = (ConicProgramSolverFactory) config.getFactory(CPS_KEY, CPS_DEFAULT);
		solver = cpsFactory.getConicProgramSolver(config);
		solver.setConicProgram(program);
	}
	/**
	 * Adds a linear inequality constraint
	 * @param coefficients
	 * @param loss
	 */
	public void addInequalityConstraint(double[] coefficients, double loss) {
		// TODO Auto-generated method stub		
	}

	/**
	 * solve
	 */
	public void solve() {
		
	}
	
	public double [] getSolution() {
		return solution;
	}

	/**
	 * set f in x'A'Ax + f'x
	 * @param coefficients
	 */
	public void setLinearCoefficients(double[] coefficients) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * set L = diag(diagonalHessianFactor)
	 * Can we use the sparse matrix class for this?
	 * @param diagonalHessianFactor
	 */
	public void setDiagonalHessian(double[] diagonalHessianFactor) {
		// TODO Auto-generated method stub
		
	}

	private int size;
	private double [] solution;
	private ConicProgram program;
	private ConicProgramSolver solver;
}
