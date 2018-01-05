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

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.WeightedRule;

import com.google.common.collect.Iterables;

public class LatentObjectiveComputer extends HardEM {
	public LatentObjectiveComputer(Model model, Database rvDB,
			Database observedDB, ConfigBundle config) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		super(model, rvDB, observedDB, config);

		// Gathers the CompatibilityRules.
		for (WeightedRule rule : Iterables.filter(model.getRules(), WeightedRule.class)) {
			if (rule.isWeightMutable()) {
				mutableRules.add(rule);
			} else {
				immutableRules.add(rule);
			}
		}

		// Sets up the ground model.
		initGroundModel();
	}

	/**
	 * Computes primal objective
	 */
	public double getObjective() {
		termGenerator.updateWeights(groundRuleStore, termStore);
		minimizeKLDivergence();
		computeObservedIncomp();
		computeExpectedIncomp();
		return computeRegularizer() + computeLoss();
	}
}
