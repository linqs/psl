package edu.umd.cs.psl.application.learning.weight;

import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;

/**
 * 
 * 
 * @author Bert Huang <bert@cs.umd.edu>
 *
 */
public class QuadraticProgram {
	
	public QuadraticProgram(int size) {
		this.size = size;
	}

	/**
	 * 
	 * @param coefficients
	 * @param loss
	 */
	public void addInequalityConstraint(double[] coefficients, double loss) {
		// TODO Auto-generated method stub		
	}

	/**
	 * 
	 */
	public void solve() {
		
	}
	
	public double [] getSolution() {
		return solution;
	}

	/**
	 * 
	 * @param coefficients
	 */
	public void setLinearCoefficients(double[] coefficients) {
		// TODO Auto-generated method stub
		
	}

	
	private int size;
	private double [] solution;
	private ConicProgram conicProgram;
	private ConicProgramSolver solver;
}
