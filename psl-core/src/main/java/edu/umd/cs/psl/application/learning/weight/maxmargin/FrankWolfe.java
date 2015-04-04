package edu.umd.cs.psl.application.learning.weight.maxmargin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.learning.weight.WeightLearningApplication;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.parameters.NegativeWeight;
import edu.umd.cs.psl.model.parameters.PositiveWeight;

/**
 * Implements the batch Frank-Wolfe algorithm for StructSVM
 * (Lacoste-Julian et al., 2012).
 * 
 * This application of the algorithm diverges from the original
 * in that loss-augmented inference returns a real-valued solution,
 * rather than integral. This *should* be OK, since we are solving
 * the primal QP, and therefore don't need to account for the
 * infinite number of dual variables. However, a formal analysis
 * to support this claim is still pending.
 * 
 * @author blondon
 *
 */
public class FrankWolfe extends WeightLearningApplication {

	private static final Logger log = LoggerFactory.getLogger(FrankWolfe.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "frankwolfe";
		
	/**
	 * Key for double property, cutting plane tolerance
	 */
	public static final String CONVERGENCE_TOLERANCE_KEY = CONFIG_PREFIX + ".tolerance";
	/** Default value for CONVERGENCE_TOLERANCE_KEY */
	public static final double CONVERGENCE_TOLERANCE_DEFAULT = 1e-5;

	/**
	 * Key for positive integer, maximum iterations
	 */
	public static final String MAX_ITER_KEY = CONFIG_PREFIX + ".maxiter";
	/** Default value for MAX_ITER_KEY */
	public static final int MAX_ITER_DEFAULT = 500;
	
	/**
	 * Key for boolean property. If true, only non-negative weights will be learned. 
	 */
	public static final String NONNEGATIVE_WEIGHTS_KEY = CONFIG_PREFIX + ".nonnegativeweights";
	/** Default value for NONNEGATIVE_WEIGHTS_KEY */
	public static final boolean NONNEGATIVE_WEIGHTS_DEFAULT = true;
	
	/**
	 * Key for boolean property. If true, loss and gradient will be normalized by number of labels. 
	 */
	public static final String NORMALIZE_KEY = CONFIG_PREFIX + ".normalize";
	/** Default value for NORMALIZE_KEY */
	public static final boolean NORMALIZE_DEFAULT = true;

	/**
	 * Key for double property, regularization parameter \lambda, where objective is \lambda*||w|| + (slack)
	 */
	public static final String REG_PARAM_KEY = CONFIG_PREFIX + ".regparam";
	/** Default value for REG_PARAM_KEY */
	public static final double REG_PARAM_DEFAULT = 1;

	/**
	 * Variables
	 */
	protected final double tolerance;
	protected final int maxIter;
	protected final boolean nonnegativeWeights;
	protected final boolean normalize;
	protected double regParam;
	
	/**
	 * Constructor
	 * @param model
	 * @param rvDB
	 * @param observedDB
	 * @param config
	 */
	public FrankWolfe(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		super(model, rvDB, observedDB, config);
		tolerance = config.getDouble(CONVERGENCE_TOLERANCE_KEY, CONVERGENCE_TOLERANCE_DEFAULT);
		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		nonnegativeWeights = config.getBoolean(NONNEGATIVE_WEIGHTS_KEY, NONNEGATIVE_WEIGHTS_DEFAULT);
		normalize = config.getBoolean(NORMALIZE_KEY, NORMALIZE_DEFAULT);
		regParam = config.getDouble(REG_PARAM_KEY, REG_PARAM_DEFAULT);
	}
	
	@Override
	protected void doLearn() {
		/**
		 * INITIALIZATION
		 */
		
		/* Inits local copy of weights and avgWeights to user-specified values. */
		double[] weights = new double[kernels.size()];
		double[] avgWeights = new double[kernels.size()];
		for (int i = 0; i < weights.length; i++) {
			weights[i] = kernels.get(i).getWeight().getWeight();
			avgWeights[i] = weights[i];
		}
		
		/* Inits loss to zero. */
		double loss = 0;
		
		/* Computes the observed incompatibilities and number of groundings. */
		double[] truthIncompatibility = new double[kernels.size()];
		int[] numGroundings = new int[kernels.size()];
		int totalGroundings = 0;
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(e.getValue().getValue());
		}
		for (int i = 0; i < kernels.size(); i++) {
			for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
				truthIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
				++numGroundings[i];
				++totalGroundings;
			}
		}
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			e.getKey().setValue(0.0);
		}
		
		/* Compute (approximate?) number of labels, for normalizing loss, gradient. */
		int numLabels = trainingMap.getTrainingMap().entrySet().size();

		/* Sets up loss augmenting ground kernels */
		double obsvTrueWeight = -1.0;
		double obsvFalseWeight = -1.0;
		log.debug("Weighting loss of positive (value = 1.0) examples by {} " +
				  "and negative examples by {}", obsvTrueWeight, obsvFalseWeight);
		List<LossAugmentingGroundKernel> lossKernels = new ArrayList<LossAugmentingGroundKernel>(trainingMap.getTrainingMap().size());
		for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
			double truth = e.getValue().getValue();
			NegativeWeight weight = new NegativeWeight((truth == 1.0) ? obsvTrueWeight : obsvFalseWeight);
			/* If ground truth is not integral, this will throw exception. */
			LossAugmentingGroundKernel gk = new LossAugmentingGroundKernel(e.getKey(), truth, weight);
			reasoner.addGroundKernel(gk);
			lossKernels.add(gk);
		}
		
		/**
		 * MAIN LOOP
		 */
		
		boolean converged = false;
		int iter = 0;
		while (!converged && iter++ < maxIter) {
			
			/* Runs loss-augmented inference with current weights. */
			reasoner.optimize();
			
			/* Computes L1 distance to ground truth. */
			double l1Distance = 0.0;
			for (LossAugmentingGroundKernel gk : lossKernels) {
				double truth = trainingMap.getTrainingMap().get(gk.getAtom()).getValue();
				double lossaugValue = gk.getAtom().getValue();
				l1Distance += Math.abs(truth - lossaugValue);
			}

			/* Computes loss-augmented incompatibilities. */
			double[] lossaugIncompatibility = new double[kernels.size()];
			for (int i = 0; i < kernels.size(); i++) {
				for (GroundKernel gk : reasoner.getGroundKernels(kernels.get(i))) {
					if (gk instanceof LossAugmentingGroundKernel)
						continue;
					lossaugIncompatibility[i] += ((GroundCompatibilityKernel) gk).getIncompatibility();
				}
			}
			
			/* Computes gradient of weights, where:
			 *   gradient = (-1 / regParam) * (truthIncompatibilities - lossaugIncompatibilities)
			 * Note: this is the negative of the formula in the paper, because
			 * these are incompatibilities, not compatibilities. 
			 */
			double[] gradient = new double[weights.length];
			for (int i = 0; i < weights.length; ++i) {
				gradient[i] = (-1.0 / regParam) * (truthIncompatibility[i] - lossaugIncompatibility[i]);
			}
			
			/* Normalizes L1 distance and gradient by numLabels. */
			if (normalize) {
				l1Distance /= (double)numLabels;
				for (int i = 0; i < weights.length; ++i) {
					gradient[i] /= (double)numLabels;
				}
			}
			
			/* Computes step size. */
			double numerator = 0.0;
			double denominator = 0.0;
			double stepSize = 0.0;
			for (int i = 0; i < weights.length; ++i) {
				double delta = weights[i] - gradient[i];
				numerator += weights[i] * delta;
				denominator += delta * delta;
			}
			numerator += (l1Distance - loss) / regParam;
			if (denominator != 0.0) {
				stepSize = numerator / denominator;
				if (stepSize > 1.0)
					stepSize = 1.0;
				else if (stepSize < 0.0)
					stepSize = 0.0;
			}
			else if (numerator > 0)
				stepSize = 1.0;
			else
				stepSize = 0.0;

			
			/* Takes step. */
			for (int i = 0; i < weights.length; ++i) {
				/* Updates weights. */
				weights[i] = (1.0 - stepSize) * weights[i] + stepSize * gradient[i];
				if (nonnegativeWeights && weights[i] < 0.0)
					weights[i] = 0.0;
				if (weights[i] >= 0.0)
					kernels.get(i).setWeight(new PositiveWeight(weights[i]));
				else
					kernels.get(i).setWeight(new NegativeWeight(weights[i]));
				/* Updates average weights. */
				avgWeights[i] = (double)iter / ((double)iter + 2.0) * avgWeights[i]
							  + 2.0 / ((double)iter + 2.0) * weights[i];
			}
			reasoner.changedGroundKernelWeights();
			loss = (1.0 - stepSize) * loss + stepSize * l1Distance;
			
			/* Compute duality gap. */
			double gap = regParam * numerator;
			if (gap < tolerance) {
				converged = true;
			}
			
			/* Log */
			log.debug("Iter {}: L1 distance of worst violator: {}", iter, l1Distance);
			log.debug("Iter {}: numerator: {}", iter, numerator);
			log.debug("Iter {}: denominator: {}", iter, denominator);
			log.debug("Iter {}: stepSize: {}", iter, stepSize);
			log.debug("Iter {}: duality gap: {}", iter, gap);
			for (int i = 0; i < weights.length; ++i) {
				log.debug(String.format("Iter %d: i=%d: w_i=%f, g_i=%f", iter, i, weights[i], gradient[i]));
			}
		}
		
		/**
		 * POST-PROCESSING
		 */
		
		/* If not converged, use average weights. */
		if (!converged) {
			log.info("Learning did not converge after {} iterations; using average weights", maxIter);
			for (int i = 0; i < avgWeights.length; ++i) {
				if (avgWeights[i] >= 0.0)
					kernels.get(i).setWeight(new PositiveWeight(avgWeights[i]));
				else
					kernels.get(i).setWeight(new NegativeWeight(avgWeights[i]));
			}
			reasoner.changedGroundKernelWeights();
		}
		else
			log.info("Learning converged after {} iterations", iter);
		
	}


}
