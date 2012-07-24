/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.Reasoner.DistributionType;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.util.collection.HashList;

/**
 * Performs probabilistic inference over {@link Atom Atoms} based on a set of
 * {@link GroundKernel GroundKernels}.
 * 
 * The (unnormalized) probability density function is an exponential model of the
 * following form: P(X) = exp(-sum(w_i * pow(k_i, l))), where w_i is the weight of
 * the ith {@link GroundCompatibilityKernel}, k_i is its incompatibility value,
 * and l is an exponent with value 1 (linear model) or 2 (quadratic model).
 * A state X has zero density if any {@link GroundConstraintKernel} is unsatisfied.
 * 
 * Uses ADMM optimization method to maximize the density.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ADMMReasoner implements Reasoner {
	
	private static final Logger log = LoggerFactory.getLogger(ADMMReasoner.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "admmreasoner";
	
	/**
	 * Key for int property for the maximum number of iterations of ADMM to
	 * perform in a round of inference
	 */
	public static final String MAX_ITER_KEY = CONFIG_PREFIX + ".maxiterations";
	/** Default value for MAX_ITER_KEY property */
	public static final int MAX_ITER_DEFAULT = 2000;
	
	/**
	 * Key for non-negative double property. Controls step size. Higher
	 * values result in larger steps.
	 */
	public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";
	/** Default value for STEP_SIZE_KEY property */
	public static final double STEP_SIZE_DEFAULT = 1;
	
//	/**
//	 * Key for positive double property. A round of ADMM will terminate
//	 * when both the primal and dual residuals are less than this threshold.
//	 */
//	public static final String MAX_RESIDUAL_KEY = CONFIG_PREFIX + ".maxresidual";
//	/** Default value for MAX_RESIDUAL_KEY property */
//	public static final double MAX_RESIDUAL_DEFAULT = 10e-2;
	
	/**
	 * Key for positive double property. Absolute error component of stopping
	 * criteria.
	 */
	public static final String EPSILON_ABS_KEY = CONFIG_PREFIX + ".epsilonabs";
	/** Default value for EPSILON_ABS_KEY property */
	public static final double EPSILON_ABS_DEFAULT = 1e-8;
	
	/**
	 * Key for positive double property. Relative error component of stopping
	 * criteria.
	 */
	public static final String EPSILON_REL_KEY = CONFIG_PREFIX + ".epsilonrel";
	/** Default value for EPSILON_ABS_KEY property */
	public static final double EPSILON_REL_DEFAULT = 1e-3;
	
	/**
	 * Key for positive integer. The number of ADMM iterations after which the
	 * termination criteria will be checked.
	 */
	public static final String STOP_CHECK_KEY = CONFIG_PREFIX + ".stopcheck";
	/** Default value for STOP_CHECK_KEY property */
	public static final int STOP_CHECK_DEFAULT = 1;
	
	/**
	 * Key for boolean property. If true, subproblems for compatibility kernels
	 * will always be solved iteratively.
	 */
	public static final String ALWAYS_ITERATIVE_KEY = CONFIG_PREFIX + ".alwaysiterative";
	/** Default value for ALWAYS_ITERATIVE_KEY property */
	public static final boolean ALWAYS_ITERATIVE_DEFAULT = false;
	
	/**
	 * Key for boolean property. If true, subproblems will always be solved
	 * iteratively.
	 */
	public static final String PESSIMISTIC_KEY = CONFIG_PREFIX + ".pessimistic";
	/** Default value for ALWAYS_ITERATIVE_KEY property */
	public static final boolean PESSIMISTIC_DEFAULT = false;
	
	/** Key for {@link DistributionType} property. */
	public static final String DISTRIBUTION_KEY = CONFIG_PREFIX + ".distribution";
	/** Default value for DISTRIBUTION_KEY property. */
	public static final DistributionType DISTRIBUTION_DEFAULT = DistributionType.linear;
	
	/** Key for int property for the maximum number of rounds of inference */
	public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxrounds";
	/** Default value for MAX_ROUNDS_KEY property */
	public static final int MAX_ROUNDS_DEFAULT = 500;
	
	private final AtomEventFramework atomFramework;
	private final int maxIter;
	/* Sometimes called rho or eta */
	final double stepSize;
//	private final double maxResidual;
	private final int maxMapRounds;
	private final boolean alwaysIterative;
	private final boolean pessimistic;
	final DistributionType type;
	
	private final double epsilonRel, epsilonAbs;
	private final int stopCheck;
	private int n;
	
	/** Ground kernels defining the density function */
	Set<GroundKernel> groundKernels;
	/** Ground kernels wrapped to be objective functions for ADMM */
	Vector<GroundKernelWrapper> factors;
	/** Ordered list of variables for looking up indices */
	HashList<AtomFunctionVariable> variables;
	/** Consensus vector */
	Vector<Double> z;
	/** Lists of local variable locations for updating consensus variables */
	Vector<Vector<VariableLocation>> varLocations;
	
	/* Stats for subproblems */
	int linearUnarySolvedZero = 0;
	int linearUnarySolvedFunction = 0;
	int linearUnarySolvedIntersection = 0;
	int linearPairwiseSolvedZero = 0;
	int linearPairwiseSolvedFunction = 0;
	int linearPairwiseSolvedIntersection = 0;
	int linearHigherOrderSolvedZero = 0;
	int linearHigherOrderSolvedFunction = 0;
	int linearHigherOrderIterative = 0;
	int quadUnarySolvedZero = 0;
	int quadUnarySolvedFunction = 0;
	int quadUnarySolvedIntersection = 0;
	int quadPairwiseSolvedZero = 0;
	int quadPairwiseSolvedIterative = 0;
	int quadHigherOrderSolvedZero = 0;
	int quadHigherOrderSolvedIterative = 0;
	int linearConstraintSolvedInterior = 0;
	int linearConstraintSolvedFace = 0;
	
	
	public ADMMReasoner(AtomEventFramework framework, ConfigBundle config) {
		atomFramework = framework;
		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
//		maxResidual = config.getDouble(MAX_RESIDUAL_KEY, MAX_RESIDUAL_DEFAULT);
		epsilonAbs = config.getDouble(EPSILON_ABS_KEY, EPSILON_ABS_DEFAULT);
		if (epsilonAbs <= 0)
			throw new IllegalArgumentException("Property " + EPSILON_ABS_KEY + " must be positive.");
		epsilonRel = config.getDouble(EPSILON_REL_KEY, EPSILON_REL_DEFAULT);
		if (epsilonRel <= 0)
			throw new IllegalArgumentException("Property " + EPSILON_REL_KEY + " must be positive.");
		alwaysIterative = config.getBoolean(ALWAYS_ITERATIVE_KEY, ALWAYS_ITERATIVE_DEFAULT);
		pessimistic = config.getBoolean(PESSIMISTIC_KEY, PESSIMISTIC_DEFAULT);
		stopCheck = config.getInt(STOP_CHECK_KEY, STOP_CHECK_DEFAULT);
		type = (DistributionType) config.getEnum(DISTRIBUTION_KEY, DISTRIBUTION_DEFAULT);
		maxMapRounds = config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);
		
		groundKernels = new HashSet<GroundKernel>();
	}
	
	@Override
	public DistributionType getDistributionType() {
		return type;
	}

	@Override
	public void addGroundKernel(GroundKernel gk) {
		groundKernels.add(gk);
	}

	@Override
	public void updateGroundKernel(GroundKernel gk) {
		groundKernels.add(gk);
	}

	@Override
	public void removeGroundKernel(GroundKernel gk) {
		groundKernels.remove(gk);
	}

	@Override
	public boolean containsGroundKernel(GroundKernel gk) {
		return groundKernels.contains(gk);
	}

	@Override
	public void mapInference() {
		int rounds = 0;
		int numActivated = 0;
		// TODO: Change this loop so it runs if ground kernels are added, not
		//       just if atoms are activated
		do {
			rounds++;
			log.debug("Starting round {} optimization", rounds);
			inferenceStep();
			// Only activate if there is another iteration
			if (rounds < maxMapRounds) {
				numActivated = atomFramework.checkToActivate();
				atomFramework.workOffJobQueue();
			}
			log.debug("Completed Round {} and activated {} atoms.", rounds, numActivated);
		} while (numActivated > 0 && rounds < maxMapRounds);
	}
	
	private void inferenceStep() {
		log.debug("Initializing optimization.");
		/* Initializes data structures */
		factors = new Vector<GroundKernelWrapper>(groundKernels.size());
		variables = new HashList<AtomFunctionVariable>(groundKernels.size() * 2);
		z = new Vector<Double>(groundKernels.size() * 2);
		varLocations = new Vector<Vector<VariableLocation>>(groundKernels.size() * 2);
		n = 0;
		
		GroundKernel groundKernel;
		
		/* Initializes factors */
		for (Iterator<GroundKernel> itr = groundKernels.iterator(); itr.hasNext(); ) {
			groundKernel = itr.next();
			if (groundKernel instanceof GroundCompatibilityKernel) {
//				if (DistributionType.linear.equals(getDistributionType()))
//					factors.add(new PairwiseLinearConvexCompatibilityWrapper(this, (GroundCompatibilityKernel) groundKernel));
//				else
				factors.add(new MOSEKWrapper(this, (GroundCompatibilityKernel) groundKernel, alwaysIterative, pessimistic));
			}
			else if (groundKernel instanceof GroundConstraintKernel) {
				factors.add(new PairwiseLinearConstraintWrapper(this, (GroundConstraintKernel) groundKernel));
			}
			else
				throw new IllegalStateException("Unsupported ground kernel: " + groundKernel);
		}
		
		log.debug("Performing optimization with {} variables and {} factors.", z.size(), factors.size());
		
		/* Performs inference */
		double primalRes = Double.POSITIVE_INFINITY;
		double dualRes = Double.POSITIVE_INFINITY;
		double epsilonPrimal = 0;
		double epsilonDual = 0;
		double epsilonAbsTerm = Math.sqrt(n) * epsilonAbs;
		double AxNorm = 0.0, BzNorm = 0.0, AyNorm = 0.0;
		boolean check = false;
		int iter = 1;
//		while ((primalRes > maxResidual || dualRes > maxResidual) && iter <= maxIter) {
		while ((primalRes > epsilonPrimal || dualRes > epsilonDual) && iter <= maxIter) {
			check = (iter-1) % stopCheck == 0;
			
			/* Solves each local function */
			for (Iterator<GroundKernelWrapper> itr = factors.iterator(); itr.hasNext(); )
				itr.next().updateLagrange().minimize();
			
			/* Updates consensus variables and computes residuals */
			double total, newZ, diff;
			VariableLocation location;
			if (check) {
				primalRes = 0.0;
				dualRes = 0.0;
				AxNorm = 0.0;
				BzNorm = 0.0;
				AyNorm = 0.0;
			}
			for (int i = 0; i < z.size(); i++) {
				total = 0.0;
				/* First pass computes newZ and dual residual */
				for (Iterator<VariableLocation> itr = varLocations.get(i).iterator(); itr.hasNext(); ) {
					location = itr.next();
					total += location.factor.x.get(location.localIndex);
					if (check) {
						AxNorm += location.factor.x.get(location.localIndex) * location.factor.x.get(location.localIndex);
						AyNorm += location.factor.y.get(location.localIndex) * location.factor.y.get(location.localIndex);
					}
				}
				newZ = total / varLocations.get(i).size();
				if (check) {
					diff = z.get(i) - newZ;
					/* Residual is diff^2 * number of local variables mapped to z element */
					dualRes += diff * diff * varLocations.get(i).size();
					BzNorm += newZ * newZ * varLocations.get(i).size();
				}
				z.set(i, newZ);
				
				/* Second pass computes primal residuals */
				if (check) {
					for (Iterator<VariableLocation> itr = varLocations.get(i).iterator(); itr.hasNext(); ) {
						location = itr.next();
						diff = location.factor.x.get(location.localIndex) - newZ;
						primalRes += diff * diff;
					}
				}
			}

			/* Finishes computing the residuals */
			if (check) {
				primalRes = Math.sqrt(primalRes);
				dualRes = stepSize * Math.sqrt(dualRes);
				
				epsilonPrimal = epsilonAbsTerm + epsilonRel * Math.max(Math.sqrt(AxNorm), Math.sqrt(BzNorm));
				epsilonDual = epsilonAbsTerm + epsilonRel * Math.sqrt(AyNorm);
			}
				
			if ((iter - 1) % 20 == 0) {
				log.debug("Residuals at iter {} -- Primal: {} -- Dual: {}", new Object[] {iter, primalRes, dualRes});
				log.debug("--------- Epsilon primal: {} -- Epsilon dual: {}", epsilonPrimal, epsilonDual);
			}
			
			iter++;
		}
		
		log.debug("Optimization complete.");
		log.debug("Optimization took {} iterations.", iter);
		
		/* Updates variables */
		for (int i = 0; i < variables.size(); i++)
			variables.get(i).setValue(z.get(i));
	}
	
	int getConsensusIndex(GroundKernelWrapper factor, AtomFunctionVariable var, int localIndex) {
		int consensusIndex = variables.indexOf(var);
		
		/* If the variable has not been given a consensus variable yet */
		if (consensusIndex == -1) {
			consensusIndex = variables.size();
			variables.add(var);
			z.add(var.getValue());
			
			/* Creates a list of local variable locations for the new variable */
			varLocations.add(new Vector<ADMMReasoner.VariableLocation>());
		}
		
		/* Adds a new entry to the list of local variable locations */
		VariableLocation varLocation = new VariableLocation(factor, localIndex);
		varLocations.get(consensusIndex).add(varLocation);
		
		/* Increments count of local variables */
		n++;
		
		return consensusIndex;
	}

	@Override
	public void close() {
		groundKernels = null;
		factors = null;
		variables = null;
		z = null;
		System.out.println("Linear unary potential solved at zero: " + linearUnarySolvedZero);
		System.out.println("Linear unary potential solved at function: " + linearUnarySolvedFunction);
		System.out.println("Linear unary potential solved at intersection: " + linearUnarySolvedIntersection);
		System.out.println("Linear pairwise potential solved at zero: " + linearPairwiseSolvedZero);
		System.out.println("Linear pairwise potential solved at function: " + linearPairwiseSolvedFunction);
		System.out.println("Linear pairwise potential solved at intersection: " + linearPairwiseSolvedIntersection);
		System.out.println("Linear higher order potential solved at zero: " + linearHigherOrderSolvedZero);
		System.out.println("Linear higher order potential solved at function: " + linearHigherOrderSolvedFunction);
		System.out.println("Linear higher order potential solved iteratively: " + linearHigherOrderIterative);
		
		System.out.println();
		
		System.out.println("Quad unary potential solved at zero: " + quadUnarySolvedZero);
		System.out.println("Quad unary potential solved at function: " + quadUnarySolvedFunction);
		System.out.println("Quad unary potential solved at intersection: " + quadUnarySolvedIntersection);
		System.out.println("Quad pairwise potential solved at zero: " + quadPairwiseSolvedZero);
		System.out.println("Quad pairwise potential solved iteratively: " + quadPairwiseSolvedIterative);
		System.out.println("Quad higher order potential solved at zero: " + quadHigherOrderSolvedZero);
		System.out.println("Quad higher order potential solved iteratively: " + quadHigherOrderSolvedIterative);
		
		System.out.println();
		
		System.out.println("Constraint solved in interior of polytope: " + linearConstraintSolvedInterior);
		System.out.println("Constraint solved on face of polytope: " + linearConstraintSolvedFace);
	}
	
	private class VariableLocation {
		private final GroundKernelWrapper factor;
		private final int localIndex;
		
		private VariableLocation(GroundKernelWrapper factor, int localIndex) {
			this.factor = factor;
			this.localIndex = localIndex;
		}
	}

}
