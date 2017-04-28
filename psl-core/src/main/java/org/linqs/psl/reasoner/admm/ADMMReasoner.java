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

// TODO(eriq): Remove imports
import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.application.groundrulestore.MemoryGroundRuleStore;
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
import org.linqs.psl.reasoner.term.MemoryTermStore;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import com.google.common.collect.Iterables;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

/**
 * Uses an ADMM optimization method to optimize its GroundRules.
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

	/**
	 * Sometimes called rho or eta
	 */
	private final double stepSize;

	private double epsilonRel, epsilonAbs;
	private final int stopCheck;

	/**
	 * The count of local and global variables.
	 * Global variables are accessed via |variables|,
	 * and local ones are only accessible to the local objective term.
	 * May be referred to as "n".
	 */
	private int totalVariableCount;

	// TODO(eriq): The entire method that this code manages variables is a bit suspect, ponder on it.

	private boolean rebuildModel;
	private double lagrangePenalty, augmentedLagrangePenalty;

	/**
	 * Collection of variables and their associated indices for looking up indices in z.
	 * We hold both a forward and reverse mapping.
	 */
	protected BidiMap<Integer, AtomFunctionVariable> variables;

	// TODO(eriq): These  were previously public and accessed directly. Do a quick performance check.
	// TODO(eriq): Renames? Include a mapping in comments?
	/** Consensus vector */
	private List<Double> z;
	/** Lower bounds on variables */
	private List<Double> lb;
	/** Upper bounds on variables */
	private List<Double> ub;
	/** Lists of local variable locations for updating consensus variables */
	private List<List<VariableLocation>> varLocations;

	/* Multithreading variables */
	private final int numThreads;

	public ADMMReasoner(ConfigBundle config) {
		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		stopCheck = config.getInt(STOP_CHECK_KEY, STOP_CHECK_DEFAULT);

		epsilonAbs = config.getDouble(EPSILON_ABS_KEY, EPSILON_ABS_DEFAULT);
		if (epsilonAbs <= 0) {
			throw new IllegalArgumentException("Property " + EPSILON_ABS_KEY + " must be positive.");
		}

		epsilonRel = config.getDouble(EPSILON_REL_KEY, EPSILON_REL_DEFAULT);
		if (epsilonRel <= 0) {
			throw new IllegalArgumentException("Property " + EPSILON_REL_KEY + " must be positive.");
		}

		rebuildModel = true;

		// TODO(eriq): We need these initialized for tests,  but I don't like the reinit in buildGroundModel().
		variables = new DualHashBidiMap<Integer, AtomFunctionVariable>();
		z = new ArrayList<Double>();
		lb = new ArrayList<Double>();
		ub = new ArrayList<Double>();
		varLocations = new ArrayList<List<VariableLocation>>();
		totalVariableCount = 0;

		// Multithreading
		numThreads = config.getInt(NUM_THREADS_KEY, NUM_THREADS_DEFAULT);
		if (numThreads <= 0) {
			throw new IllegalArgumentException("Property " + NUM_THREADS_KEY + " must be positive.");
		}
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

	public double getStepSize() {
		return stepSize;
	}

	public double getConsensusValue(int index) {
		return z.get(index).doubleValue();
	}

	/**
	 * Create variable with its associated consensus (z) variable and return the new consensus variable's index.
	 */
	public int addGlobalVariable(AtomFunctionVariable variable) {
		variables.put(variables.size(), variable);

		z.add(variable.getValue());
		lb.add(0.0);
		ub.add(1.0);
		varLocations.add(new ArrayList<ADMMReasoner.VariableLocation>());

		totalVariableCount++;

		return z.size() - 1;
	}

	/**
	 * Just increment the variable count.
	 */
	public void addLocalVariable() {
		totalVariableCount++;
	}

	/**
	 * If the variable exists get it's index into the consensus (z) vector, return -1 otherwise.
	 */
	public int getConsensusIndex(AtomFunctionVariable variable) {
		Integer index = variables.getKey(variable);
		if (index == null) {
			return -1;
		}

		return index.intValue();
	}

	// TODO(eriq): Rethink the concept of rebuilding the model (based on the termstore).
	// TEST(eriq): Because of circular dependencies on the term generator, this is strange for now.
	// private void buildGroundModel(TermStore<ADMMObjectiveTerm> termStore) {
	private void rebuildGroundModel(TermStore<ADMMObjectiveTerm> termStore) {
		log.debug("Rebuilding reasoner data structures");

		varLocations = new ArrayList<List<VariableLocation>>();
		for (int i = 0; i < z.size(); i++) {
			varLocations.add(new ArrayList<ADMMReasoner.VariableLocation>());
		}

		// Register all the local variables.
		for (ADMMObjectiveTerm term : termStore) {
			registerLocalVariableCopies(term);
		}

		rebuildModel = false;
	}

	/**
	 * Computes the incompatibility of the local variable copies corresponding to
	 * GroundRule groundRule
	 * @param groundRule
	 * @return local (dual) incompatibility
	 */
	public double getDualIncompatibility(GroundRule groundRule) {
		/* TODO(eriq): Unsupported until you can get Terms by GroundRules.
		int index = orderedGroundRules.get(groundRule);
		ADMMObjectiveTerm term = termStore.get(index);
		for (int i = 0; i < term.zIndices.length; i++) {
			int zIndex = term.zIndices[i];
			variables.get(zIndex).setValue(term.x[i]);
		}
		return ((WeightedGroundRule)groundRule).getIncompatibility();
		*/
		throw new UnsupportedOperationException("Temporarily unsupported during rework");
	}

	// TODO(eriq): Dup?
	public double getConsensusVariableValue(int index) {
		if (z == null) {
			throw new IllegalStateException("Consensus variables have not been initialized. "
					+ "Must call optimize() first.");
		}
		return z.get(index);
	}

	@Override
	public void optimize() {
	}

	// TEST(eriq)
	public void optimize(TermStore<ADMMObjectiveTerm> termStore) {
		if (rebuildModel) {
			rebuildGroundModel(termStore);
		}

		log.debug("Performing optimization with {} variables and {} terms.", z.size(), termStore.size());

		// Starts up the computation threads
		ADMMTask[] tasks = new ADMMTask[numThreads];
		CyclicBarrier workerBarrier = new CyclicBarrier(numThreads);
		CyclicBarrier checkBarrier = new CyclicBarrier(numThreads + 1);
		Semaphore notifySem = new Semaphore(0);
		ThreadPool threadPool = ThreadPool.getPool();
		for (int i = 0; i < numThreads; i ++) {
			tasks[i] = new ADMMTask(i, workerBarrier, checkBarrier, notifySem, termStore);
			threadPool.submit(tasks[i]);
		}

		/* Performs inference */
		double primalRes = Double.POSITIVE_INFINITY;
		double dualRes = Double.POSITIVE_INFINITY;
		double epsilonPrimal = 0;
		double epsilonDual = 0;
		double epsilonAbsTerm = Math.sqrt(totalVariableCount) * epsilonAbs;
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
	public void close() {
		variables = null;
		z = null;
		lb = null;
		ub = null;
		varLocations = null;
	}

	private void registerLocalVariableCopies(ADMMObjectiveTerm term) {
		for (int i = 0; i < term.x.length; i++) {
			VariableLocation varLocation = new VariableLocation(term, i);
			varLocations.get(term.zIndices[i]).add(varLocation);
		}
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

	private class ADMMTask implements Runnable {
		public boolean flag;
		private final int termStart, termEnd;
		private final int zStart, zEnd;
		private final CyclicBarrier workerBarrier, checkBarrier;
		private final Semaphore notification;
		private final TermStore<ADMMObjectiveTerm> termStore;

		public ADMMTask(int index, CyclicBarrier wBarrier, CyclicBarrier cBarrier,
				Semaphore notification, TermStore<ADMMObjectiveTerm> termStore) {
			this.workerBarrier = wBarrier;
			this.checkBarrier = cBarrier;
			this.notification = notification;
			this.termStore = termStore;
			this.flag = true;


			// Determine the section of the terms this thread will look at
			int tIncrement = (int)(Math.ceil((double)termStore.size() / (double)numThreads));
			this.termStart = tIncrement * index;
			this.termEnd = Math.min(termStart + tIncrement, termStore.size());

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
					termStore.get(i).updateLagrange().minimize();

				// Ensures all threads are at the same point
				awaitUninterruptibly(workerBarrier);

				// TODO(eriq): Be careful here when refactoring. Make sure there are not used between checks,
				// when they have their old values..
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
					for (VariableLocation location : varLocations.get(i)) {
						total += location.term.x[location.localIndex] + location.term.y[location.localIndex] / stepSize;

						if (check) {
							AxNormInc += location.term.x[location.localIndex] * location.term.x[location.localIndex];
							AyNormInc += location.term.y[location.localIndex] * location.term.y[location.localIndex];
						}
					}
					double newZ = total / varLocations.get(i).size();
					if (newZ < lb.get(i)) {
						newZ = lb.get(i);
					} else if (newZ > ub.get(i)) {
						newZ = ub.get(i);
					}

					if (check) {
						double diff = z.get(i) - newZ;
						/* Residual is diff^2 * number of local variables mapped to z element */
						dualResInc += diff * diff * varLocations.get(i).size();
						BzNormInc += newZ * newZ * varLocations.get(i).size();
					}
					z.set(i, newZ);

					/* Second pass computes primal residuals */
					if (check) {
						for (VariableLocation location : varLocations.get(i)) {
							double diff = location.term.x[location.localIndex] - newZ;
							primalResInc += diff * diff;
							// computes Lagrangian penalties
							lagrangePenalty += location.term.y[location.localIndex] * (location.term.x[location.localIndex] - z.get(i));
							augmentedLagrangePenalty += 0.5 * stepSize * Math.pow(location.term.x[location.localIndex]-z.get(i), 2);
						}
					}
				}

				if (check) {
					notification.release();
				}

				// Waits for main thread
				awaitUninterruptibly(checkBarrier);
			}
			awaitUninterruptibly(checkBarrier);
		}
	}




	// TEST(eriq): Remove once we remove GRS from Reasoner








	public void addGroundRule(GroundRule groundRule) {
		rebuildModel = true;
		// TEST
		throw new RuntimeException("TEST(eriq)");
	}

	public void changedGroundRule(GroundRule groundRule) {
		rebuildModel = true;
		// TEST
		throw new RuntimeException("TEST(eriq)");
	}

	// TODO(eriq): Needs to be reworked into GRS/TS/TG.
	public void changedGroundRuleWeight(WeightedGroundRule groundRule) {
		if (!rebuildModel) {
			/* TODO(eriq)
			int index = orderedGroundRules.get(groundRule);
			if (index != -1) {
				((WeightedObjectiveTerm) termStore.get(index)).setWeight(groundRule.getWeight().getWeight());
			}
			*/
		}
		// TEST
		throw new RuntimeException("TEST(eriq)");
	}

	public void changedGroundRuleWeights() {
		if (!rebuildModel) {
			for (WeightedGroundRule groundRule : getCompatibilityRules()) {
				changedGroundRuleWeight(groundRule);
			}
		}
		// TEST
		throw new RuntimeException("TEST(eriq)");
	}

	public boolean containsGroundRule(GroundRule groundRule) {
		// TEST
		throw new RuntimeException("TEST(eriq)");
		// return false;
	}

	public Iterable<WeightedGroundRule> getCompatibilityRules() {
		// TEST
		throw new RuntimeException("TEST(eriq)");
		// return null;
	}

	public Iterable<UnweightedGroundRule> getConstraintRules() {
		// TEST
		throw new RuntimeException("TEST(eriq)");
		// return null;
	}

	public Iterable<GroundRule> getGroundRules() {
		// TEST
		throw new RuntimeException("TEST(eriq)");
		// return null;
	}

	public Iterable<GroundRule> getGroundRules(Rule rule) {
		// TEST
		throw new RuntimeException("TEST(eriq)");
		// return null;
	}

	public void removeGroundRule(GroundRule groundRule) {
		rebuildModel = true;
		// TEST
		throw new RuntimeException("TEST(eriq)");
	}

	public int size() {
		// TEST
		throw new RuntimeException("TEST(eriq)");
		// return 0;
	}

}
