package edu.umd.cs.psl.application.topicmodel;

import java.util.List;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.logloss.GroundLogLoss;

/** Ground log loss kernels for LDA.  Keeps a pointer to an array of coefficients, which may be updated.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 *
 */
public class LDAgroundLogLoss extends GroundLogLoss {
	final double[] coefficientsArray;
	public LDAgroundLogLoss(CompatibilityKernel k, List<GroundAtom> literals, List<Double> coefficients, double[] coefficientsArray) {
		super(k, literals, coefficients);
		this.coefficientsArray = coefficientsArray;
	}
	
	public double[] getCoefficientsArray() {
		return coefficientsArray;
	}

}
