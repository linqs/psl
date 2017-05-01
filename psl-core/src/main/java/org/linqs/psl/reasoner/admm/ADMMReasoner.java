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

	// TODO(eriq): These  were previously public and accessed directly. Do a quick performance check.
	// TODO(eriq): Does not need to be member data anymore (along with most non-finals).
	/**
	 * Consensus vector.
	 * Also sometimes called 'z'.
	 */
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

		consensusValues = null;

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

	@Override
	public void optimize(TermStore baseTermStore) {
		if (!(baseTermStore instanceof ADMMTermStore)) {
			throw new IllegalArgumentException("ADMMReasoner requires an ADMMTermStore");
		}
		ADMMTermStore termStore = (ADMMTermStore)baseTermStore;

		log.debug("Performing optimization with {} variables and {} terms.", termStore.getNumGlobalVariables(), termStore.size());

		consensusValues = new double[termStore.getNumGlobalVariables()];

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
		double epsilonAbsTerm = Math.sqrt(termStore.getNumLocalVariables()) * epsilonAbs;
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
		for (ADMMTask task : tasks) {
			task.flag = false;
		}

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

		// Updates variables
		termStore.updateVariables(consensusValues);
	}

	@Override
	public void close() {
		consensusValues = null;
	}

	private class ADMMTask implements Runnable {
		public boolean flag;
		private final int termIndexStart, termIndexEnd;
		private final int variableIndexStart, variableIndexEnd;
		private final CyclicBarrier workerBarrier, checkBarrier;
		private final Semaphore notification;
		private final ADMMTermStore termStore;

		public double primalResInc;
		public double dualResInc;
		public double AxNormInc;
		public double BzNormInc;
		public double AyNormInc;
		protected double lagrangePenalty;
		protected double augmentedLagrangePenalty;

		public ADMMTask(int index, CyclicBarrier wBarrier, CyclicBarrier cBarrier,
				Semaphore notification, ADMMTermStore termStore) {
			this.workerBarrier = wBarrier;
			this.checkBarrier = cBarrier;
			this.notification = notification;
			this.termStore = termStore;
			this.flag = true;

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
			awaitUninterruptibly(checkBarrier);

			int iter = 1;
			while (flag) {
				boolean check = ((iter - 1) % stopCheck) == 0;

				// Solves each local function.
				for (int i = termIndexStart; i < termIndexEnd; i ++) {
					termStore.get(i).updateLagrange(stepSize, consensusValues);
					termStore.get(i).minimize(stepSize, consensusValues);
				}

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

				for (int i = variableIndexStart; i < variableIndexEnd; i++) {
					double total = 0.0;
					// First pass computes newConsensusValue and dual residual fom all local copies.
					for (LocalVariable localVariable : termStore.getLocalVariables(i)) {
						total += localVariable.getValue() + localVariable.getLagrange() / stepSize;

						if (check) {
							AxNormInc += localVariable.getValue() * localVariable.getValue();
							AyNormInc += localVariable.getLagrange() * localVariable.getLagrange();
						}
					}

					double newConsensusValue = total / termStore.getLocalVariables(i).size();
					if (newConsensusValue < LOWER_BOUND) {
						newConsensusValue = LOWER_BOUND;
					} else if (newConsensusValue > UPPER_BOUND) {
						newConsensusValue = UPPER_BOUND;
					}

					if (check) {
						double diff = consensusValues[i] - newConsensusValue;
						// Residual is diff^2 * number of local variables mapped to consensusValues element.
						dualResInc += diff * diff * termStore.getLocalVariables(i).size();
						BzNormInc += newConsensusValue * newConsensusValue * termStore.getLocalVariables(i).size();
					}
					consensusValues[i] = newConsensusValue;

					/* Second pass computes primal residuals */
					if (check) {
						for (LocalVariable localVariable : termStore.getLocalVariables(i)) {
							double diff = localVariable.getValue() - newConsensusValue;
							primalResInc += diff * diff;
							// computes Lagrangian penalties
							lagrangePenalty += localVariable.getLagrange() * (localVariable.getValue() - consensusValues[i]);
							augmentedLagrangePenalty += 0.5 * stepSize * Math.pow(localVariable.getValue() - consensusValues[i], 2);
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
}
