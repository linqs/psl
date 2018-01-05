/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.ThreadPool;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.LocalVariable;
import org.linqs.psl.reasoner.inspector.ReasonerInspector;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Parallel;

import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Uses an ADMM optimization method to optimize its GroundRules.
 */
public class ADMMReasoner extends Reasoner {
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

	/**
	 * Default value for MAX_ITER_KEY property
	 */
	public static final int MAX_ITER_DEFAULT = 25000;

	/**
	 * Key for non-negative float property. Controls step size. Higher
	 * values result in larger steps.
	 */
	public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";

	/**
	 * Default value for STEP_SIZE_KEY property
	 */
	public static final float STEP_SIZE_DEFAULT = 1.0f;

	/**
	 * Key for positive float property. Absolute error component of stopping
	 * criteria.
	 */
	public static final String EPSILON_ABS_KEY = CONFIG_PREFIX + ".epsilonabs";

	/**
	 * Default value for EPSILON_ABS_KEY property
	 */
	public static final float EPSILON_ABS_DEFAULT = 1e-5f;

	/**
	 * Key for positive float property. Relative error component of stopping
	 * criteria.
	 */
	public static final String EPSILON_REL_KEY = CONFIG_PREFIX + ".epsilonrel";

	/**
	 * Default value for EPSILON_ABS_KEY property
	 */
	public static final float EPSILON_REL_DEFAULT = 1e-3f;

	/**
	 * Key for positive integer. Number of threads to run the optimization in.
	 */
	public static final String NUM_THREADS_KEY = CONFIG_PREFIX + ".numthreads";

	/**
	 * Default value for the number of work threads
	 * (by default uses the number of processors in the system).
	 */
	public static final int NUM_THREADS_DEFAULT = Parallel.NUM_THREADS;

	private static final float LOWER_BOUND = 0.0f;
	private static final float UPPER_BOUND = 1.0f;

	/**
	 * The size of computation blocks for terms and variables.
	 */
	private static final int BLOCK_SIZE = 20;

	/**
	 * Log the residuals once in every period.
	 */
	private static final int LOG_PERIOD = 50;

	/**
	 * Sometimes called eta or rho,
	 */
	private final float stepSize;

	/**
	 * Multithreading variables
	 */
	private final int numThreads;

	private float epsilonRel;
	private float epsilonAbs;

	private float lagrangePenalty;
	private float augmentedLagrangePenalty;

	private int maxIter;

	// Also sometimes called 'z'.
	// Only populated after inference.
	private float[] consensusValues;

	public ADMMReasoner(ConfigBundle config) {
		super(config);

		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		stepSize = config.getFloat(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);

		epsilonAbs = config.getFloat(EPSILON_ABS_KEY, EPSILON_ABS_DEFAULT);
		if (epsilonAbs <= 0) {
			throw new IllegalArgumentException("Property " + EPSILON_ABS_KEY + " must be positive.");
		}

		epsilonRel = config.getFloat(EPSILON_REL_KEY, EPSILON_REL_DEFAULT);
		if (epsilonRel <= 0) {
			throw new IllegalArgumentException("Property " + EPSILON_REL_KEY + " must be positive.");
		}

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

	public float getEpsilonRel() {
		return epsilonRel;
	}

	public void setEpsilonRel(float epsilonRel) {
		this.epsilonRel = epsilonRel;
	}

	public float getEpsilonAbs() {
		return epsilonAbs;
	}

	public void setEpsilonAbs(float epsilonAbs) {
		this.epsilonAbs = epsilonAbs;
	}

	public float getLagrangianPenalty() {
		return this.lagrangePenalty;
	}

	public float getAugmentedLagrangianPenalty() {
		return this.augmentedLagrangePenalty;
	}

	/**
	 * Computes the incompatibility of the local variable copies corresponding to GroundRule groundRule.
	 * @param groundRule
	 * @return local (dual) incompatibility
	 */
	public double getDualIncompatibility(GroundRule groundRule, ADMMTermStore termStore) {
		// Set the global variables to the value of the local variables for this rule.
		for (Integer termIndex : termStore.getTermIndices((WeightedGroundRule)groundRule)) {
			for (LocalVariable localVariable : termStore.get(termIndex).getVariables()) {
				consensusValues[localVariable.getGlobalId()] = localVariable.getValue();
			}
		}

		// Updates variables
		termStore.updateVariables(consensusValues);

		return ((WeightedGroundRule)groundRule).getIncompatibility();
	}

	@Override
	public void optimize(TermStore baseTermStore) {
		if (!(baseTermStore instanceof ADMMTermStore)) {
			throw new IllegalArgumentException("ADMMReasoner requires an ADMMTermStore");
		}
		ADMMTermStore termStore = (ADMMTermStore)baseTermStore;

		log.debug("Performing optimization with {} variables and {} terms.", termStore.getNumGlobalVariables(), termStore.size());

		// Also sometimes called 'z'.
		consensusValues = new float[termStore.getNumGlobalVariables()];

		SyncCounter termCounter = new SyncCounter((int)Math.ceil(termStore.size() / (float)BLOCK_SIZE));
		SyncCounter variableCounter = new SyncCounter((int)Math.ceil(termStore.getNumGlobalVariables() / (float)BLOCK_SIZE));

		// Starts up the computation threads
		ADMMTask[] tasks = new ADMMTask[numThreads];

		// Only the workers.
		CyclicBarrier termUpdateCompleteBarrier = new CyclicBarrier(numThreads);

		// Workers and master.
		CyclicBarrier workerStartBarrier = new CyclicBarrier(numThreads + 1);
		CyclicBarrier workerEndBarrier = new CyclicBarrier(numThreads + 1);

		ThreadPool threadPool = new ThreadPool();
		for (int i = 0; i < numThreads; i ++) {
			tasks[i] = new ADMMTask(i,
					termUpdateCompleteBarrier, workerStartBarrier, workerEndBarrier,
					termCounter, variableCounter,
					termStore, consensusValues);
			threadPool.submit(tasks[i]);
		}

		// Performs inference.
		float primalRes = Float.POSITIVE_INFINITY;
		float dualRes = Float.POSITIVE_INFINITY;
		float epsilonPrimal = 0.0f;
		float epsilonDual = 0.0f;
		float epsilonAbsTerm = (float)(Math.sqrt(termStore.getNumLocalVariables()) * epsilonAbs);
		float AxNorm = 0.0f;
		float BzNorm = 0.0f;
		float AyNorm = 0.0f;
		int iteration = 1;

		while ((primalRes > epsilonPrimal || dualRes > epsilonDual) && iteration <= maxIter) {
			try {
				// Reset the counters for a new round.
				termCounter.reset();
				variableCounter.reset();

				// Startup all the workers.
				workerStartBarrier.await();

				// Wait for all workers to report in after optimization round.
				workerEndBarrier.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (BrokenBarrierException e) {
				throw new RuntimeException(e);
			}

			primalRes = 0.0f;
			dualRes = 0.0f;
			AxNorm = 0.0f;
			BzNorm = 0.0f;
			AyNorm = 0.0f;
			lagrangePenalty = 0.0f;
			augmentedLagrangePenalty = 0.0f;

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

			primalRes = (float)Math.sqrt(primalRes);
			dualRes = (float)(stepSize * Math.sqrt(dualRes));

			epsilonPrimal = (float)(epsilonAbsTerm + epsilonRel * Math.max(Math.sqrt(AxNorm), Math.sqrt(BzNorm)));
			epsilonDual = (float)(epsilonAbsTerm + epsilonRel * Math.sqrt(AyNorm));

			if (iteration % LOG_PERIOD == 0) {
				log.trace("Residuals at iteration {} -- Primal: {} -- Dual: {}", iteration, primalRes, dualRes);
				log.trace("--------- Epsilon primal: {} -- Epsilon dual: {}", epsilonPrimal, epsilonDual);
			}

			if (inspector != null) {
				// Updating the variables is a costly operation, but the inspector may need access to RVA values.
				log.debug("Updating random variable atoms with consensus values for inspector");
				termStore.updateVariables(consensusValues);

				if (!inspector.update(this, new ADMMStatus(iteration, primalRes, dualRes))) {
					log.info("Stopping ADMM iterations on advice from inspector");
					break;
				}
			}

			iteration++;
		}

		// Notify threads the optimization is complete
		for (ADMMTask task : tasks) {
			task.done = true;
		}

		try {
			// Wake up all threads so they can shutdown.
			workerStartBarrier.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} catch (BrokenBarrierException e) {
			throw new RuntimeException(e);
		}

		threadPool.shutdownAndWait();

		log.info("Optimization completed in {} iterations. " +
				"Primal res.: {}, Dual res.: {}", iteration - 1, primalRes, dualRes);

		// Updates variables
		termStore.updateVariables(consensusValues);
	}

	@Override
	public void close() {
	}

	private class ADMMTask implements Runnable {
		// Set by the parent thread each round of optimization.
		public volatile boolean done;

		private final int threadIndex;

		private final SyncCounter termCounter;
		private final SyncCounter variableCounter;

		private float[] consensusValues;
		private final ADMMTermStore termStore;

		private final CyclicBarrier termUpdateCompleteBarrier;
		private final CyclicBarrier workerStartBarrier;
		private final CyclicBarrier workerEndBarrier;

		public float primalResInc;
		public float dualResInc;
		public float AxNormInc;
		public float BzNormInc;
		public float AyNormInc;

		protected float lagrangePenalty;
		protected float augmentedLagrangePenalty;

		public ADMMTask(
				int threadIndex,
				CyclicBarrier termUpdateCompleteBarrier,
				CyclicBarrier workerStartBarrier, CyclicBarrier workerEndBarrier,
				SyncCounter termCounter, SyncCounter variableCounter,
				ADMMTermStore termStore, float[] consensusValues) {
			this.termUpdateCompleteBarrier = termUpdateCompleteBarrier;
			this.workerStartBarrier = workerStartBarrier;
			this.workerEndBarrier = workerEndBarrier;

			this.threadIndex = threadIndex;
			this.termCounter = termCounter;
			this.variableCounter = variableCounter;

			this.consensusValues = consensusValues;
			this.termStore = termStore;

			this.done = false;

			primalResInc = 0.0f;
			dualResInc = 0.0f;
			AxNormInc = 0.0f;
			BzNormInc = 0.0f;
			AyNormInc = 0.0f;
			lagrangePenalty = 0.0f;
			augmentedLagrangePenalty = 0.0f;
		}

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
			int numTerms = termStore.size();
			int numVariables = termStore.getNumGlobalVariables();

			int iteration = 1;
			while (true) {
				awaitUninterruptibly(workerStartBarrier);
				if (done) {
					break;
				}

				// Minimize each local function (wrt the local variable copies).
				// Instead of dividing up the work ahead of time,
				// get one block of jobs at a time so the threads will have more even workloads.
				for (int blockIndex = termCounter.next(); blockIndex != -1; blockIndex = termCounter.next()) {
					for (int innerBlockIndex = 0; innerBlockIndex < BLOCK_SIZE; innerBlockIndex++) {
						int termIndex = blockIndex * BLOCK_SIZE + innerBlockIndex;

						if (termIndex >= numTerms) {
							break;
						}

						termStore.get(termIndex).updateLagrange(stepSize, consensusValues);
						termStore.get(termIndex).minimize(stepSize, consensusValues);
					}
				}

				// Wait for all the workers to finish minimizing.
				awaitUninterruptibly(termUpdateCompleteBarrier);

				primalResInc = 0.0f;
				dualResInc = 0.0f;
				AxNormInc = 0.0f;
				BzNormInc = 0.0f;
				AyNormInc = 0.0f;
				lagrangePenalty = 0.0f;
				augmentedLagrangePenalty = 0.0f;

				// Instead of dividing up the work ahead of time,
				// get one job at a time so the threads will have more even workloads.
				for (int blockIndex = variableCounter.next(); blockIndex != -1; blockIndex = variableCounter.next()) {
					for (int innerBlockIndex = 0; innerBlockIndex < BLOCK_SIZE; innerBlockIndex++) {
						int variableIndex = blockIndex * BLOCK_SIZE + innerBlockIndex;

						if (variableIndex >= numVariables) {
							break;
						}

						float total = 0.0f;
						int numLocalVariables = termStore.getLocalVariables(variableIndex).size();

						// First pass computes newConsensusValue and dual residual fom all local copies.
						// Use indexes instead of iterators for profiling purposes: http://psy-lob-saw.blogspot.co.uk/2014/12/the-escape-of-arraylistiterator.html
						for (int localVarIndex = 0; localVarIndex < numLocalVariables; localVarIndex++) {
							LocalVariable localVariable = termStore.getLocalVariables(variableIndex).get(localVarIndex);
							total += localVariable.getValue() + localVariable.getLagrange() / stepSize;

							AxNormInc += localVariable.getValue() * localVariable.getValue();
							AyNormInc += localVariable.getLagrange() * localVariable.getLagrange();
						}

						float newConsensusValue = total / numLocalVariables;
						newConsensusValue = Math.max(Math.min(newConsensusValue, UPPER_BOUND), LOWER_BOUND);

						float diff = consensusValues[variableIndex] - newConsensusValue;
						// Residual is diff^2 * number of local variables mapped to consensusValues element.
						dualResInc += diff * diff * numLocalVariables;
						BzNormInc += newConsensusValue * newConsensusValue * numLocalVariables;

						consensusValues[variableIndex] = newConsensusValue;

						// Second pass computes primal residuals.

						// Use indexes instead of iterators for profiling purposes: http://psy-lob-saw.blogspot.co.uk/2014/12/the-escape-of-arraylistiterator.html
						for (int localVarIndex = 0; localVarIndex < numLocalVariables; localVarIndex++) {
							LocalVariable localVariable = termStore.getLocalVariables(variableIndex).get(localVarIndex);

							diff = localVariable.getValue() - newConsensusValue;
							primalResInc += diff * diff;

							// compute Lagrangian penalties
							lagrangePenalty += localVariable.getLagrange() * (localVariable.getValue() - consensusValues[variableIndex]);
							augmentedLagrangePenalty += 0.5 * stepSize * Math.pow(localVariable.getValue() - consensusValues[variableIndex], 2);
						}
					}
				}

				awaitUninterruptibly(workerEndBarrier);
			}
		}
	}

	/**
	 * A thread-safe counter that starts at 0 and returns |max| successive numbers.
	 */
	private static class SyncCounter {
		private final int max;
		private int count;

		public SyncCounter(int max) {
			this.max = max;
			count = 0;
		}

		/**
		 * Returns the next int, or -1 if there are no more.
		 */
		public synchronized int next() {
			if (count >= max) {
				return -1;
			}

			return count++;
		}

		public synchronized void reset() {
			count = 0;
		}
	}

	private static class ADMMStatus extends ReasonerInspector.IterativeReasonerStatus {
		public double primalResidual;
		public double dualResidual;

		public ADMMStatus(int iteration, double primalResidual, double dualResidual) {
			super(iteration);

			this.primalResidual = primalResidual;
			this.dualResidual = dualResidual;
		}

		@Override
		public String toString() {
			return String.format("%s, primal: %f, dual: %f", super.toString(), primalResidual, dualResidual);
		}
	}
}
