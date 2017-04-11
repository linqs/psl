package org.linqs.psl.application.learning.weight.maxlikelihood;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Variable;

import java.util.HashSet;
import java.util.Set;

public class MaxLikelihoodMPETest {
	private TestModelFactory.ModelInformation model;

	@Before
	public void setup() {
		initModel(true);
	}

	private void initModel(boolean useNice) {
		if (model != null) {
			model.dataStore.close();
			model = null;
		}

		model = TestModelFactory.getModel(useNice);
	}

	/**
	 * A quick test that only checks to see if MPEInference is running.
	 * This is not a targeted or exhaustive test, just a starting point.
	 */
	@Test
	public void baseTest() {
		Set<StandardPredicate> allPredicates = new HashSet<StandardPredicate>(model.predicates.values());
		Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>(model.predicates.values());
		closedPredicates.remove(model.predicates.get("Friends"));

		Database weightLearningTrainDB = model.dataStore.getDatabase(model.targetPartition, closedPredicates, model.observationPartition);
		Database weightLearningTruthDB = model.dataStore.getDatabase(model.truthPartition, allPredicates, model.observationPartition);

		WeightLearningApplication weightLearner = null;
		try {
			weightLearner = new MaxLikelihoodMPE(model.model, weightLearningTrainDB, weightLearningTruthDB, model.config);
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during MPE constructor.");
		}

		try {
			weightLearner.learn();
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during weight learning.");
		}

		weightLearner.close();
		
		weightLearningTrainDB.close();
		weightLearningTruthDB.close();
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
					new QueryAtom(model.predicates.get("Nice"), model.dataStore.getUniqueID("ZzZ__FAKE_PERSON_A__ZzZ")),
					new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
				),
				new QueryAtom(model.predicates.get("Friends"), model.dataStore.getUniqueID("ZzZ__FAKE_PERSON_A__ZzZ"), new Variable("B"))
			),
			5.0,
			true
		);
		model.model.addRule(newRule);

		Set<StandardPredicate> allPredicates = new HashSet<StandardPredicate>(model.predicates.values());
		Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>(model.predicates.values());
		closedPredicates.remove(model.predicates.get("Friends"));

		Database weightLearningTrainDB = model.dataStore.getDatabase(model.targetPartition, closedPredicates, model.observationPartition);
		Database weightLearningTruthDB = model.dataStore.getDatabase(model.truthPartition, allPredicates, model.observationPartition);

		WeightLearningApplication weightLearner = null;
		try {
			weightLearner = new MaxLikelihoodMPE(model.model, weightLearningTrainDB, weightLearningTruthDB, model.config);
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during MPE constructor.");
		}

		try {
			weightLearner.learn();
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during weight learning.");
		}

		weightLearner.close();
		
		weightLearningTrainDB.close();
		weightLearningTruthDB.close();
	}

	@After
	public void cleanup() {
		model.dataStore.close();
	}
}
