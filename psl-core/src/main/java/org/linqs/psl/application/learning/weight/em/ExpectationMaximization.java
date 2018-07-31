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
package org.linqs.psl.application.learning.weight.em;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Abstract superclass for implementations of the expectation-maximization
 * algorithm for learning with latent variables.
 */
public abstract class ExpectationMaximization extends VotedPerceptron {
	private static final Logger log = LoggerFactory.getLogger(ExpectationMaximization.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "em";

	/**
	 * Key for positive int property for the number of iterations of expectation
	 * maximization to perform
	 */
	public static final String ITER_KEY = CONFIG_PREFIX + ".iterations";
	public static final int ITER_DEFAULT = 10;

	/**
	 * Key for positive double property for the minimum absolute change in weights
	 * such that EM is considered converged
	 */
	public static final String TOLERANCE_KEY = CONFIG_PREFIX + ".tolerance";
	public static final double TOLERANCE_DEFAULT = 1e-3;

	protected final int iterations;
	protected final double tolerance;

	protected int emIteration;

	public ExpectationMaximization(List<Rule> rules, Database rvDB,
			Database observedDB) {
		super(rules, rvDB, observedDB, true);

		iterations = Config.getInt(ITER_KEY, ITER_DEFAULT);
		tolerance = Config.getDouble(TOLERANCE_KEY, TOLERANCE_DEFAULT);
	}

	@Override
	protected void doLearn() {
		double[] previousWeights = new double[mutableRules.size()];
		for (int i = 0; i < previousWeights.length; i++) {
			previousWeights[i] = mutableRules.get(i).getWeight();
		}

		for (emIteration = 0; emIteration < iterations; emIteration++) {
			log.debug("Beginning EM iteration {} of {}", emIteration, iterations);

			eStep();
			mStep();

			// Check if we need to stop (if the weights did not change enough).

			double change = 0;
			for (int i = 0; i < mutableRules.size(); i++) {
				change += Math.pow(previousWeights[i] - mutableRules.get(i).getWeight(), 2);
				previousWeights[i] = mutableRules.get(i).getWeight();
			}
			change = Math.sqrt(change);

			double loss = getLoss();
			double regularizer = computeRegularizer();
			double objective = loss + regularizer;

			log.info("Finished EM iteration {} with m-step norm {}. Loss: {}, regularizer: {}, objective: {}",
					emIteration, change, loss, regularizer, objective);

			if (change <= tolerance) {
				log.info("EM converged.");
				break;
			}
		}
	}

	/**
	 * The Expectation step in the EM algorithm.
	 * This is a prime target for child override.
	 *
	 * The default implementation just inferring the latent variables.
	 * IE, Minimizes the KL divergence by setting the latent variables to their
	 * most probable state conditioned on the evidence and the labeled random variables.
	 */
	protected void eStep() {
		computeLatentMPEState();
	}

	/**
	 * The Maximization step in the EM algorithm.
	 * This is a prime target for child override.
	 * The M step is expected to change the weights in mutableRules.
	 *
	 * The default implementation just calls super.doLearn(), which learns over the non-latent variables.
	 */
	protected void mStep() {
		super.doLearn();
	}
}
