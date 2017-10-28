package org.linqs.psl.application.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.PSLTest;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.Queries;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LazyMPEInferenceTest {
	// TODO(eriq): Tests:
	//   - base: not nice
	//	 - below threshold (will require different rules that don't hit all targets on initial grounding)
	//   - multiple predicates
	//   - partially observed
	//   - rules such that no instantiation will happen
	//   - arithmetic rules

	/**
	 * A quick test that only checks to see if LazyMPEInference is running.
	 * This is not a targeted or exhaustive test, just a starting point.
	 */
	@Test
	public void testBase() {
		TestModelFactory.ModelInformation info = TestModelFactory.getModel(true);

		// Get an empty partition so that no targets will exist in it and we will have to lazily instantiate them all.
		Partition targetPartition = info.dataStore.getPartition(TestModelFactory.PARTITION_UNUSED);

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(targetPartition, toClose, info.observationPartition);
		LazyMPEInference mpe = new LazyMPEInference(info.model, inferDB, info.config);

		// The Friends predicate should be empty.
		assertEquals(0, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.mpeInference();

		// Now the Friends predicate should have the crossproduct (5x5) minus self pairs (5) in it.
		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.close();
		inferDB.close();
	}

	/**
	 * Make sure lazy inference works even when everything is fully specified.
	 */
	@Test
	public void testFullySpecified() {
		TestModelFactory.ModelInformation info = TestModelFactory.getModel(true);

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
		LazyMPEInference mpe = new LazyMPEInference(info.model, inferDB, info.config);

		// The Friends predicate should be fully defined.
		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.mpeInference();

		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.close();
		inferDB.close();
	}

	@Test
	// TEST(eriq): Some investigations.
	public void testWork() {
		// TEST
		PSLTest.initLogger("TRACE");

		// TEST(eriq): Make everyone nice?
		// TestModelFactory.ModelInformation info = TestModelFactory.getModel();
		TestModelFactory.ModelInformation info = TestModelFactory.getModel(true);

		// Get an empty partition so that no targets will exist in it and we will have to lazily instantiate them all.
		Partition targetPartition = info.dataStore.getPartition(TestModelFactory.PARTITION_UNUSED);

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(targetPartition, toClose, info.observationPartition);
		LazyMPEInference mpe = new LazyMPEInference(info.model, inferDB, info.config);

		// The Friends predicate should be empty.
		assertEquals(0, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.mpeInference();

		// TEST
		for (org.linqs.psl.model.rule.Rule rule : info.model.getRules()) {
			int count = 0;

			for (Object groundRule : mpe.getGroundRuleStore().getGroundRules(rule)) {
				count++;
			}

			System.out.printf("%s -- %d\n", rule, count);
		}

		// Now the Friends predicate should have the crossproduct (5x5) minus self pairs (5) in it.
		assertEquals(20, Queries.countAllGroundRandomVariableAtoms(inferDB, info.predicates.get("Friends")));

		mpe.close();
		inferDB.close();
	}

	@After
	public void cleanup() {
		PSLTest.disableLogger();
	}
}
