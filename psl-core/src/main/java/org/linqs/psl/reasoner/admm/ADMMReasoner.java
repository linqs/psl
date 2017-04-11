/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.reasoner.admm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.ThreadPool;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;
import org.linqs.psl.reasoner.function.ConstantNumber;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionSingleton;
import org.linqs.psl.reasoner.function.FunctionSum;
import org.linqs.psl.reasoner.function.FunctionSummand;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.linqs.psl.reasoner.function.MaxFunction;
import org.linqs.psl.reasoner.function.PowerOfTwo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import com.google.common.collect.Iterables;

/**
 * Uses an ADMM optimization method to optimize its GroundKernels.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 * @author Eric Norris
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
	public static final int MAX_ITER_DEFAULT = 25000;

	/**
	 * Key for non-negative double property. Controls step size. Higher
	 * values result in larger steps.
	 */
	public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";
	/** Default value for STEP_SIZE_KEY property */
	public static final double STEP_SIZE_DEFAULT = 1;

	/**
	 * Key for positive double property. Absolute error component of stopping
	 * criteria.
	 */
	public static final String EPSILON_ABS_KEY = CONFIG_PREFIX + ".epsilonabs";
	/** Default value for EPSILON_ABS_KEY property */
	public static final double EPSILON_ABS_DEFAULT = 1e-5;

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
	 * Key for positive integer. Number of threads to run the optimization in.
	 */
	public static final String NUM_THREADS_KEY = CONFIG_PREFIX + ".numthreads";
	/** Default value for STOP_CHECK_KEY property
	 * (by default uses the number of processors in the system) */
	public static final int NUM_THREADS_DEFAULT = Runtime.getRuntime().availableProcessors();

	private int maxIter;
	/* Sometimes called rho or eta */
	public final double stepSize;

	private double epsilonRel, epsilonAbs;
	private final int stopCheck;
	private int n;
	private boolean rebuildModel;
	private double lagrangePenalty, augmentedLagrangePenalty;

	/** Ground kernels defining the objective function */
	protected SetValuedMap<Rule, GroundRule> groundKernels;

	/**
	 * Ordered list of GroundKernels for looking up indices in terms.
	 * The integer value corresponds to the index into terms.
	 */
	protected Map<GroundRule, Integer> orderedGroundKernels;

	/** Ground kernels wrapped to be objective function terms for ADMM */
	protected List<ADMMObjectiveTerm> terms;

	/**
	 * Collection of variables and their associated indices for looking up indices in z.
	 * We hold both a forward and reverse mapping.
	 */
	protected BidiMap<Integer, AtomFunctionVariable> variables;

	/** Consensus vector */
	protected List<Double> z;
	/** Lower bounds on variables */
	protected List<Double> lb;
	/** Upper bounds on variables */
	protected List<Double> ub;
	/** Lists of local variable locations for updating consensus variables */
	protected List<List<VariableLocation>> varLocations;

	/* Multithreading variables */
	private final int numThreads;

	public ADMMReasoner(ConfigBundle config) {
		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		epsilonAbs = config.getDouble(EPSILON_ABS_KEY, EPSILON_ABS_DEFAULT);
		if (epsilonAbs <= 0)
			throw new IllegalArgumentException("Property " + EPSILON_ABS_KEY + " must be positive.");
		epsilonRel = config.getDouble(EPSILON_REL_KEY, EPSILON_REL_DEFAULT);
		if (epsilonRel <= 0)
			throw new IllegalArgumentException("Property " + EPSILON_REL_KEY + " must be positive.");
		stopCheck = config.getInt(STOP_CHECK_KEY, STOP_CHECK_DEFAULT);

		rebuildModel = true;

		groundKernels = new HashSetValuedHashMap<Rule, GroundRule>();

		// Multithreading
		numThreads = config.getInt(NUM_THREADS_KEY, NUM_THREADS_DEFAULT);
		if (numThreads <= 0)
			throw new IllegalArgumentException("Property " + NUM_THREADS_KEY + " must be positive.");
	}

	public int getMaxIter() {
		return maxIter;
	}

	public void setMaxIter(int maxIter) {
		this.maxIter = maxIter;
	}

	public double getEpsilonRel() {
		return epsilonRel;
	}

	public void setEpsilonRel(double epsilonRel) {
		this.epsilonRel = epsilonRel;
	}

	public double getEpsilonAbs() {
		return epsilonAbs;
	}

	public void setEpsilonAbs(double epsilonAbs) {
		this.epsilonAbs = epsilonAbs;
	}

	public double getLagrangianPenalty() {
		return this.lagrangePenalty;
	}

	public double getAugmentedLagrangianPenalty() {
		return this.augmentedLagrangePenalty;
	}

	@Override
	public void addGroundRule(GroundRule gk) {
		groundKernels.put(gk.getRule(), gk);
		rebuildModel = true;
	}

	@Override
	public void changedGroundRule(GroundRule gk) {
		rebuildModel = true;
	}

	@Override
	public void changedGroundKernelWeight(WeightedGroundRule gk) {
		if (!rebuildModel) {
			int index = orderedGroundKernels.get(gk);
			if (index != -1) {
				((WeightedObjectiveTerm) terms.get(index)).setWeight(gk.getWeight().getWeight());
			}
		}
	}

	@Override
	public void changedGroundKernelWeights() {
		if (!rebuildModel)
			for (WeightedGroundRule gk : getCompatibilityKernels())
				changedGroundKernelWeight(gk);
	}

	@Override
	public void removeGroundKernel(GroundRule gk) {
		groundKernels.removeMapping(gk.getRule(), gk);
		rebuildModel = true;
	}

	@Override
	public boolean containsGroundKernel(GroundRule gk) {
		return groundKernels.containsMapping(gk.getRule(), gk);
	}

	protected void buildGroundModel() {
		log.debug("(Re)building reasoner data structures");

		/* Initializes data structures */
		orderedGroundKernels = new HashMap<GroundRule, Integer>(groundKernels.size());
		terms = new ArrayList<ADMMObjectiveTerm>(groundKernels.size());

		variables = new DualHashBidiMap<Integer, AtomFunctionVariable>();

		z = new ArrayList<Double>(groundKernels.size() * 2);
		lb = new ArrayList<Double>(groundKernels.size() * 2);
		ub = new ArrayList<Double>(groundKernels.size() * 2);
		varLocations = new ArrayList<List<VariableLocation>>(groundKernels.size() * 2);
		n = 0;

		/* Initializes objective terms from ground kernels */
		log.debug("Initializing objective terms for {} ground kernels", groundKernels.size());
		for (GroundRule groundKernel : groundKernels.values()) {
			ADMMObjectiveTerm term = createTerm(groundKernel);

			if (term.x.length > 0) {
				registerLocalVariableCopies(term);
				orderedGroundKernels.put(groundKernel, orderedGroundKernels.size());
				terms.add(term);
			}
		}

		rebuildModel = false;
	}

	/**
	 * Processes a {@link GroundRule} to create a corresponding
	 * {@link ADMMObjectiveTerm}
	 *
	 * @param groundKernel  the GroundKernel to be added to the ADMM objective
	 * @return  the created ADMMObjectiveTerm
	 */
	protected ADMMObjectiveTerm createTerm(GroundRule groundKernel) {
		boolean squared;
		FunctionTerm function, innerFunction, zeroTerm, innerFunctionA, innerFunctionB;
		ADMMObjectiveTerm term;

		if (groundKernel instanceof WeightedGroundRule) {
			function = ((WeightedGroundRule) groundKernel).getFunctionDefinition();

			/* Checks if the function is wrapped in a PowerOfTwo */
			if (function instanceof PowerOfTwo) {
				squared = true;
				function = ((PowerOfTwo) function).getInnerFunction();
			}
			else
				squared = false;

			/*
			 * If the FunctionTerm is a MaxFunction, ensures that it has two arguments, a linear
			 * function and zero, and constructs the objective term (a hinge loss)
			 */
			if (function instanceof MaxFunction) {
				if (((MaxFunction) function).size() != 2)
					throw new IllegalArgumentException("Max function must have one linear function and 0.0 as arguments.");
				innerFunction = null;
				zeroTerm = null;
				innerFunctionA = ((MaxFunction) function).get(0);
				innerFunctionB = ((MaxFunction) function).get(1);

				if (innerFunctionA instanceof ConstantNumber && innerFunctionA.getValue() == 0.0) {
					zeroTerm = innerFunctionA;
					innerFunction = innerFunctionB;
				}
				else if (innerFunctionB instanceof ConstantNumber && innerFunctionB.getValue() == 0.0) {
					zeroTerm = innerFunctionB;
					innerFunction = innerFunctionA;
				}

				if (zeroTerm == null)
					throw new IllegalArgumentException("Max function must have one linear function and 0.0 as arguments.");

				if (innerFunction instanceof FunctionSum) {
					Hyperplane hp = processHyperplane((FunctionSum) innerFunction);
					if (squared) {
						term = new SquaredHingeLossTerm(this, hp.zIndices, hp.coeffs, hp.constant,
								((WeightedGroundRule) groundKernel).getWeight().getWeight());
					}
					else {
						term = new HingeLossTerm(this, hp.zIndices, hp.coeffs, hp.constant,
								((WeightedGroundRule) groundKernel).getWeight().getWeight());
					}
				}
				else
					throw new IllegalArgumentException("Max function must have one linear function and 0.0 as arguments.");
			}
			/* Else, if it's a FunctionSum, constructs the objective term (a linear loss) */
			else if (function instanceof FunctionSum) {
				Hyperplane hp = processHyperplane((FunctionSum) function);
				if (squared) {
					term = new SquaredLinearLossTerm(this, hp.zIndices, hp.coeffs, 0.0,
							((WeightedGroundRule) groundKernel).getWeight().getWeight());
				}
				else {
					term = new LinearLossTerm(this, hp.zIndices, hp.coeffs,
							((WeightedGroundRule) groundKernel).getWeight().getWeight());
				}
			}
			else
				throw new IllegalArgumentException("Unrecognized function: " + ((WeightedGroundRule) groundKernel).getFunctionDefinition());
		}
		else if (groundKernel instanceof UnweightedGroundRule) {
			ConstraintTerm constraint = ((UnweightedGroundRule) groundKernel).getConstraintDefinition();
			function = constraint.getFunction();
			if (function instanceof FunctionSum) {
				Hyperplane hp = processHyperplane((FunctionSum) function);
				term = new LinearConstraintTerm(this, hp.zIndices, hp.coeffs,
						constraint.getValue() + hp.constant, constraint.getComparator());
			}
			else
				throw new IllegalArgumentException("Unrecognized constraint: " + constraint);
		}
		else
			throw new IllegalArgumentException("Unsupported ground kernel: " + groundKernel);

		return term;
	}

	/**
	 * Computes the incompatibility of the local variable copies corresponding to
	 * GroundKernel gk
	 * @param gk
	 * @return local (dual) incompatibility
	 */
	public double getDualIncompatibility(GroundRule gk) {
		int index = orderedGroundKernels.get(gk);
		ADMMObjectiveTerm term = terms.get(index);
		for (int i = 0; i < term.zIndices.length; i++) {
			int zIndex = term.zIndices[i];
			variables.get(zIndex).setValue(term.x[i]);
		}
		return ((WeightedGroundRule) gk).getIncompatibility();
	}

	public double getConsensusVariableValue(int index) {
		if (z == null) {
			throw new IllegalStateException("Consensus variables have not been initialized. "
					+ "Must call optimize() first.");
		}
		return z.get(index);
	}

	private class ADMMTask implements Runnable {
		public boolean flag;
		private final int termStart, termEnd;
		private final int zStart, zEnd;
		private final CyclicBarrier workerBarrier, checkBarrier;
		private final Semaphore notification;

		public ADMMTask(int index, CyclicBarrier wBarrier, CyclicBarrier cBarrier, Semaphore notification) {
			this.workerBarrier = wBarrier;
			this.checkBarrier = cBarrier;
			this.notification = notification;
			this.flag = true;


			// Determine the section of the terms this thread will look at
			int tIncrement = (int)(Math.ceil((double)terms.size() / (double)numThreads));
			this.termStart = tIncrement * index;
			this.termEnd = Math.min(termStart + tIncrement, terms.size());

			// Determine the section of the z vector this thread will look at
			int zIncrement = (int)(Math.ceil((double)z.size() / (double)numThreads));
			this.zStart = zIncrement * index;
			this.zEnd = Math.min(zStart + zIncrement, z.size());
		}

		public double primalResInc = 0.0;
		public double dualResInc = 0.0;
		public double AxNormInc = 0.0;
		public double BzNormInc = 0.0;
		public double AyNormInc = 0.0;
		protected double lagrangePenalty = 0.0;
		protected double augmentedLagrangePenalty = 0.0;

		private void awaitUninterruptibly(CyclicBarrier b) {
			try {
				b.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (BrokenBarrierException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void run() {
			awaitUninterruptibly(checkBarrier);

			int iter = 1;
			while (flag) {
				boolean check = (iter-1) % stopCheck == 0;

				/* Solves each local function */
				for (int i = termStart; i < termEnd; i ++)
					terms.get(i).updateLagrange().minimize();

				// Ensures all threads are at the same point
				awaitUninterruptibly(workerBarrier);

				if (check) {
					primalResInc = 0.0;
					dualResInc = 0.0;
					AxNormInc = 0.0;
					BzNormInc = 0.0;
					AyNormInc = 0.0;
					lagrangePenalty = 0.0;
					augmentedLagrangePenalty = 0.0;
				}

				for (int i = zStart; i < zEnd; i++) {
					double total = 0.0;
					/* First pass computes newZ and dual residual */
					for (Iterator<VariableLocation> itr = varLocations.get(i).iterator(); itr.hasNext(); ) {
						VariableLocation location = itr.next();
						total += location.term.x[location.localIndex] + location.term.y[location.localIndex] / stepSize;
						if (check) {
							AxNormInc += location.term.x[location.localIndex] * location.term.x[location.localIndex];
							AyNormInc += location.term.y[location.localIndex] * location.term.y[location.localIndex];
						}
					}
					double newZ = total / varLocations.get(i).size();
					if (newZ < lb.get(i))
						newZ = lb.get(i);
					else if (newZ > ub.get(i))
						newZ = ub.get(i);

					if (check) {
						double diff = z.get(i) - newZ;
						/* Residual is diff^2 * number of local variables mapped to z element */
						dualResInc += diff * diff * varLocations.get(i).size();
						BzNormInc += newZ * newZ * varLocations.get(i).size();
					}
					z.set(i, newZ);

					/* Second pass computes primal residuals */
					if (check) {
						for (Iterator<VariableLocation> itr = varLocations.get(i).iterator(); itr.hasNext(); ) {
							VariableLocation location = itr.next();
							double diff = location.term.x[location.localIndex] - newZ;
							primalResInc += diff * diff;
							// computes Lagrangian penalties
							lagrangePenalty += location.term.y[location.localIndex] * (location.term.x[location.localIndex] - z.get(i));
							augmentedLagrangePenalty += 0.5 * stepSize * Math.pow(location.term.x[location.localIndex]-z.get(i), 2);
						}
					}
				}

				if (check)
					notification.release();

				// Waits for main thread
				awaitUninterruptibly(checkBarrier);
			}
			awaitUninterruptibly(checkBarrier);
		}

	}

	@Override
	public void optimize() {
		if (rebuildModel)
			buildGroundModel();

		log.debug("Performing optimization with {} variables and {} terms.", z.size(), terms.size());

		// Starts up the computation threads
		ADMMTask[] tasks = new ADMMTask[numThreads];
		CyclicBarrier workerBarrier = new CyclicBarrier(numThreads);
		CyclicBarrier checkBarrier = new CyclicBarrier(numThreads + 1);
		Semaphore notifySem = new Semaphore(0);
		ThreadPool threadPool = ThreadPool.getPool();
		for (int i = 0; i < numThreads; i ++) {
			tasks[i] = new ADMMTask(i, workerBarrier, checkBarrier, notifySem);
			threadPool.submit(tasks[i]);
		}

		/* Performs inference */
		double primalRes = Double.POSITIVE_INFINITY;
		double dualRes = Double.POSITIVE_INFINITY;
		double epsilonPrimal = 0;
		double epsilonDual = 0;
		double epsilonAbsTerm = Math.sqrt(n) * epsilonAbs;
		double AxNorm = 0.0, BzNorm = 0.0, AyNorm = 0.0;
		boolean check = false;
		int iter = 0;
		while ((primalRes > epsilonPrimal || dualRes > epsilonDual) && iter < maxIter) {
			check = iter % stopCheck == 0;

			// Await check barrier
			try {
				checkBarrier.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (BrokenBarrierException e) {
				throw new RuntimeException(e);
			}

			if (check) {
				// Acquire semaphore
				notifySem.acquireUninterruptibly(numThreads);

				primalRes = 0.0;
				dualRes = 0.0;
				AxNorm = 0.0;
				BzNorm = 0.0;
				AyNorm = 0.0;
				lagrangePenalty = 0.0;
				augmentedLagrangePenalty = 0.0;

				// Total values from threads
				for (ADMMTask task : tasks) {
					primalRes += task.primalResInc;
					dualRes += task.dualResInc;
					AxNorm += task.AxNormInc;
					BzNorm += task.BzNormInc;
					AyNorm += task.AyNormInc;
					lagrangePenalty += task.lagrangePenalty;
					augmentedLagrangePenalty += task.augmentedLagrangePenalty;
				}

				primalRes = Math.sqrt(primalRes);
				dualRes = stepSize * Math.sqrt(dualRes);

				epsilonPrimal = epsilonAbsTerm + epsilonRel * Math.max(Math.sqrt(AxNorm), Math.sqrt(BzNorm));
				epsilonDual = epsilonAbsTerm + epsilonRel * Math.sqrt(AyNorm);
			}

			if (iter % (50 * stopCheck) == 0) {
				log.trace("Residuals at iter {} -- Primal: {} -- Dual: {}", new Object[] {iter, primalRes, dualRes});
				log.trace("--------- Epsilon primal: {} -- Epsilon dual: {}", epsilonPrimal, epsilonDual);
			}

			iter++;
		}

		// Notify threads the optimization is complete
		for (ADMMTask task : tasks)
			task.flag = false;

		try {
			// First wake all threads
			checkBarrier.await();

			// Now wait for all threads to print shutting down msg
			checkBarrier.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (BrokenBarrierException e) {
			throw new RuntimeException(e);
		}

		log.info("Optimization completed in  {} iterations. " +
				"Primal res.: {}, Dual res.: {}", new Object[] {iter, primalRes, dualRes});

		/* Updates variables */
		for (int i = 0; i < variables.size(); i++) {
			variables.get(i).setValue(z.get(i));
		}
	}

	@Override
	public Iterable<GroundRule> getGroundKernels() {
		return groundKernels.values();
	}

	@Override
	public Iterable<WeightedGroundRule> getCompatibilityKernels() {
		return Iterables.filter(groundKernels.values(), WeightedGroundRule.class);
	}

	public Iterable<UnweightedGroundRule> getConstraintKernels() {
		return Iterables.filter(groundKernels.values(), UnweightedGroundRule.class);
	}

	@Override
	public Iterable<GroundRule> getGroundKernels(Rule k) {
		return groundKernels.get(k);
	}

	@Override
	public int size() {
		return groundKernels.size();
	}

	@Override
	public void close() {
		groundKernels = null;
		orderedGroundKernels = null;
		terms = null;
		variables = null;
		z = null;
		lb = null;
		ub = null;
		varLocations = null;

//		try {
//			log.debug("Shutting down thread pool.");
//			threadPool.shutdownNow();
//			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
//		} catch (InterruptedException e) {
//			throw new RuntimeException(e);
//		}
	}

	private void registerLocalVariableCopies(ADMMObjectiveTerm term) {
		for (int i = 0; i < term.x.length; i++) {
			VariableLocation varLocation = new VariableLocation(term, i);
			varLocations.get(term.zIndices[i]).add(varLocation);
		}
	}

	protected Hyperplane processHyperplane(FunctionSum sum) {
		Hyperplane hp = new Hyperplane();
		HashMap<AtomFunctionVariable, Integer> localVarLocations = new HashMap<AtomFunctionVariable, Integer>();
		ArrayList<Integer> tempZIndices = new ArrayList<Integer>(sum.size());
		ArrayList<Double> tempCoeffs = new ArrayList<Double>(sum.size());

		for (Iterator<FunctionSummand> sItr = sum.iterator(); sItr.hasNext(); ) {
			FunctionSummand summand = sItr.next();
			FunctionSingleton singleton = summand.getTerm();
			if (singleton instanceof AtomFunctionVariable && !singleton.isConstant()) {
				/*
				 * If this variable has been encountered before in any hyperplane...
				 */
				if (variables.containsValue(singleton)) {
					int zIndex = variables.getKey(singleton).intValue();

					/*
					 * Checks if the variable has already been encountered
					 * in THIS hyperplane
					 */
					Integer localIndex = localVarLocations.get(singleton);
					/* If it has, just adds the coefficient... */
					if (localIndex != null) {
						tempCoeffs.set(localIndex, tempCoeffs.get(localIndex) + summand.getCoefficient());
					}
					/* Else, creates a new local variable */
					else {
						tempZIndices.add(zIndex);
						tempCoeffs.add(summand.getCoefficient());
						localVarLocations.put((AtomFunctionVariable) singleton, tempZIndices.size()-1);

						/* Increments count of local variables */
						n++;
					}
				}
				/* Else, creates a new global variable and a local variable */
				else {
					/* Creates the global variable */
					variables.put(variables.size(), (AtomFunctionVariable)singleton);

					z.add(singleton.getValue());
					lb.add(0.0);
					ub.add(1.0);

					/* Creates a list of local variable locations for the new variable */
					varLocations.add(new ArrayList<ADMMReasoner.VariableLocation>());

					/* Creates the local variable */
					tempZIndices.add(z.size()-1);
					tempCoeffs.add(summand.getCoefficient());
					localVarLocations.put((AtomFunctionVariable) singleton, tempZIndices.size()-1);

					/* Increments count of local variables */
					n++;
				}
			}
			else if (singleton.isConstant()) {
				/* Subtracts because hyperplane is stored as coeffs^T * x = constant */
				hp.constant -= summand.getValue();
			}
			else
				throw new IllegalArgumentException("Unexpected summand.");
		}

		hp.zIndices = new int[tempZIndices.size()];
		hp.coeffs = new double[tempCoeffs.size()];

		for (int i = 0; i < tempZIndices.size(); i++) {
			hp.zIndices[i] = tempZIndices.get(i);
			hp.coeffs[i] = tempCoeffs.get(i);
		}

		return hp;
	}

	protected class Hyperplane {
		public int[] zIndices;
		public double[] coeffs;
		public double constant;
	}

	public class VariableLocation {
		private final ADMMObjectiveTerm term;
		private final int localIndex;

		private VariableLocation(ADMMObjectiveTerm term, int localIndex) {
			this.term = term;
			this.localIndex = localIndex;
		}
		public ADMMObjectiveTerm getTerm() {
			return term;
		}

		public int getLocalIndex() {
			return localIndex;
		}
	}

}
