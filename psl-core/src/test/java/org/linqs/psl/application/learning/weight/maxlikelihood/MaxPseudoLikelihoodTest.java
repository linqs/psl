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
package org.linqs.psl.application.learning.weight.maxlikelihood;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.linqs.psl.PSLTest;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxPseudoLikelihood;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class MaxPseudoLikelihoodTest {
	private Database weightLearningTrainDB;
	private Database weightLearningTruthDB;
	private TestModelFactory.ModelInformation info;

	@Before
	public void setup() {
		initModel(true);
	}

	@After
	public void cleanup() {
		PSLTest.disableLogger();

		weightLearningTrainDB.close();
		weightLearningTrainDB = null;

		weightLearningTruthDB.close();
		weightLearningTruthDB = null;

		info.dataStore.close();
		info = null;
	}

	private void initModel(boolean useNice) {
		if (weightLearningTrainDB != null) {
			weightLearningTrainDB.close();
			weightLearningTrainDB = null;
		}

		if (weightLearningTruthDB != null) {
			weightLearningTruthDB.close();
			weightLearningTruthDB = null;
		}

		if (info != null) {
			info.dataStore.close();
			info = null;
		}

		info = TestModelFactory.getModel(useNice);

		Set<StandardPredicate> allPredicates = new HashSet<StandardPredicate>(info.predicates.values());
		Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>(info.predicates.values());
		closedPredicates.remove(info.predicates.get("Friends"));

		weightLearningTrainDB = info.dataStore.getDatabase(info.targetPartition, closedPredicates, info.observationPartition);
		weightLearningTruthDB = info.dataStore.getDatabase(info.truthPartition, allPredicates, info.observationPartition);
	}

	/**
	 * A quick test that only checks to see if MPEInference is running.
	 * This is not a targeted or exhaustive test, just a starting point.
	 */
	@Test
	public void baseTest() {
		WeightLearningApplication weightLearner = new MaxPseudoLikelihood(info.model, weightLearningTrainDB, weightLearningTruthDB, info.config);
		weightLearner.learn();
		weightLearner.close();
	}

	/**
	 * Ensure that a rule with no groundings does not break.
	 */
	@Test
	public void ruleWithNoGroundingsTest() {
		// Add in a rule that will have zero groundings.
		// People are not friends with themselves.
		Rule newRule = new WeightedLogicalRule(
			new Implication(
				new Conjunction(
					new QueryAtom(info.predicates.get("Nice"), new UniqueStringID("ZzZ__FAKE_PERSON_A__ZzZ")),
					new QueryAtom(info.predicates.get("Nice"), new Variable("B"))
				),
				new QueryAtom(info.predicates.get("Friends"), new UniqueStringID("ZzZ__FAKE_PERSON_A__ZzZ"), new Variable("B"))
			),
			5.0,
			true
		);
		info.model.addRule(newRule);

		WeightLearningApplication weightLearner = new MaxPseudoLikelihood(info.model, weightLearningTrainDB, weightLearningTruthDB, info.config);
		weightLearner.learn();
		weightLearner.close();
	}
}
