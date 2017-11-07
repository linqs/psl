/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

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

	private static final double LOWER_BOUND = 0.0;
	private static final double UPPER_BOUND = 1.0;

	/**
	 * Sometimes called eta or rho,
	 */
	private final double stepSize;

	/**
	 * Multithreading variables
	 */
	private final int numThreads;

	private double epsilonRel;
	private double epsilonAbs;

	private final int stopCheck;

	private double lagrangePenalty;
	private double augmentedLagrangePenalty;

	private int maxIter;

	// Also sometimes called 'z'.
	// Only populated after inference.
	private double[] consensusValues;

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
		consensusValues = new double[termStore.getNumGlobalVariables()];

		// Starts up the computation threads
		ADMMTask[] tasks = new ADMMTask[numThreads];
		CyclicBarrier termUpdateCompleteBarrier = new CyclicBarrier(numThreads);
		CyclicBarrier workerStartBarrier = new CyclicBarrier(numThreads + 1);
		CyclicBarrier workerEndBarrier = new CyclicBarrier(numThreads + 1);
		ThreadPool threadPool = new ThreadPool();
		for (int i = 0; i < numThreads; i ++) {
			tasks[i] = new ADMMTask(i, termUpdateCompleteBarrier, workerStartBarrier, workerEndBarrier, termStore, consensusValues);
			threadPool.submit(tasks[i]);
		}

		// Performs inference.
		double primalRes = Double.POSITIVE_INFINITY;
		double dualRes = Double.POSITIVE_INFINITY;
		double epsilonPrimal = 0;
		double epsilonDual = 0;
		double epsilonAbsTerm = Math.sqrt(termStore.getNumLocalVariables()) * epsilonAbs;
		double AxNorm = 0.0, BzNorm = 0.0, AyNorm = 0.0;
		boolean check = false;
		int iteration = 1;

		while ((primalRes > epsilonPrimal || dualRes > epsilonDual) && iteration <= maxIter) {
			check = iteration % stopCheck == 0;
			for (ADMMTask task : tasks) {
				task.check = check;
			}

			try {
				// Startup all the workers.
				workerStartBarrier.await();

				// Wait for all workers to report in after optimization round.
				workerEndBarrier.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (BrokenBarrierException e) {
				throw new RuntimeException(e);
			}

			if (check) {
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

			if (iteration % (50 * stopCheck) == 0) {
				log.trace("Residuals at iteration {} -- Primal: {} -- Dual: {}", iteration, primalRes, dualRes);
				log.trace("--------- Epsilon primal: {} -- Epsilon dual: {}", epsilonPrimal, epsilonDual);
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
		public volatile boolean check;

		private final int termIndexStart, termIndexEnd;
		private final int variableIndexStart, variableIndexEnd;
		private final ADMMTermStore termStore;
		private double[] consensusValues;

		private final CyclicBarrier termUpdateCompleteBarrier;
		private final CyclicBarrier workerStartBarrier;
		private final CyclicBarrier workerEndBarrier;

		public double primalResInc;
		public double dualResInc;
		public double AxNormInc;
		public double BzNormInc;
		public double AyNormInc;

		protected double lagrangePenalty;
		protected double augmentedLagrangePenalty;

		public ADMMTask(int index, CyclicBarrier termUpdateCompleteBarrier,
				CyclicBarrier workerStartBarrier, CyclicBarrier workerEndBarrier,
				ADMMTermStore termStore, double[] consensusValues) {
			this.termUpdateCompleteBarrier = termUpdateCompleteBarrier;
			this.workerStartBarrier = workerStartBarrier;
			this.workerEndBarrier = workerEndBarrier;
			this.consensusValues = consensusValues;

			this.termStore = termStore;
			this.done = false;
			this.check = false;

			// Determine the section of the terms this thread will look at
			int tIncrement = (int)(Math.ceil((double)termStore.size() / (double)numThreads));
			this.termIndexStart = tIncrement * index;
			this.termIndexEnd = Math.min(termIndexStart + tIncrement, termStore.size());

			// Determine the section of the consensusValues vector this thread will look at.
			int zIncrement = (int)(Math.ceil(consensusValues.length / numThreads));
			this.variableIndexStart = zIncrement * index;
			this.variableIndexEnd = Math.min(variableIndexStart + zIncrement, consensusValues.length);

			primalResInc = 0.0;
			dualResInc = 0.0;
			AxNormInc = 0.0;
			BzNormInc = 0.0;
			AyNormInc = 0.0;
			lagrangePenalty = 0.0;
			augmentedLagrangePenalty = 0.0;
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
			int iteration = 1;
			while (true) {
				awaitUninterruptibly(workerStartBarrier);
				if (done) {
					break;
				}

				// Solves each local function.
				for (int i = termIndexStart; i < termIndexEnd; i++) {
					termStore.get(i).updateLagrange(stepSize, consensusValues);
					termStore.get(i).minimize(stepSize, consensusValues);
				}

				// Ensures all threads are at the same point
				awaitUninterruptibly(termUpdateCompleteBarrier);

				if (check) {
					primalResInc = 0.0;
					dualResInc = 0.0;
					AxNormInc = 0.0;
					BzNormInc = 0.0;
					AyNormInc = 0.0;
					lagrangePenalty = 0.0;
					augmentedLagrangePenalty = 0.0;
				}

				for (int i = variableIndexStart; i < variableIndexEnd; i++) {
					double total = 0.0;
					// First pass computes newConsensusValue and dual residual fom all local copies.
					// Use indexes instead of iterators for profiling purposes: http://psy-lob-saw.blogspot.co.uk/2014/12/the-escape-of-arraylistiterator.html
					for (int localVarIndex = 0; localVarIndex < termStore.getLocalVariables(i).size(); localVarIndex++) {
						LocalVariable localVariable = termStore.getLocalVariables(i).get(localVarIndex);
						total += localVariable.getValue() + localVariable.getLagrange() / stepSize;

						if (check) {
							AxNormInc += localVariable.getValue() * localVariable.getValue();
							AyNormInc += localVariable.getLagrange() * localVariable.getLagrange();
						}
					}

					double newConsensusValue = total / termStore.getLocalVariables(i).size();
					newConsensusValue = Math.max(Math.min(newConsensusValue, UPPER_BOUND), LOWER_BOUND);

					if (check) {
						double diff = consensusValues[i] - newConsensusValue;
						// Residual is diff^2 * number of local variables mapped to consensusValues element.
						dualResInc += diff * diff * termStore.getLocalVariables(i).size();
						BzNormInc += newConsensusValue * newConsensusValue * termStore.getLocalVariables(i).size();
					}
					consensusValues[i] = newConsensusValue;

					// Second pass computes primal residuals,
					if (check) {
						// Use indexes instead of iterators for profiling purposes: http://psy-lob-saw.blogspot.co.uk/2014/12/the-escape-of-arraylistiterator.html
						for (int localVarIndex = 0; localVarIndex < termStore.getLocalVariables(i).size(); localVarIndex++) {
							LocalVariable localVariable = termStore.getLocalVariables(i).get(localVarIndex);

							double diff = localVariable.getValue() - newConsensusValue;
							primalResInc += diff * diff;
							// computes Lagrangian penalties
							lagrangePenalty += localVariable.getLagrange() * (localVariable.getValue() - consensusValues[i]);
							augmentedLagrangePenalty += 0.5 * stepSize * Math.pow(localVariable.getValue() - consensusValues[i], 2);
						}
					}
				}

				awaitUninterruptibly(workerEndBarrier);
			}
		}
	}
}
