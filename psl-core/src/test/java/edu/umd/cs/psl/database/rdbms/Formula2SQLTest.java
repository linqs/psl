package edu.umd.cs.psl.database.rdbms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.TestModelFactory;
import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Implication;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.SpecialPredicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.rule.Rule;
import edu.umd.cs.psl.model.rule.logical.WeightedLogicalRule;
import edu.umd.cs.psl.model.term.Constant;
import edu.umd.cs.psl.model.term.ConstantType;
import edu.umd.cs.psl.model.term.Variable;

import java.util.HashSet;
import java.util.Set;

public class Formula2SQLTest {
	@Test
	/**
	 * Ensure that ExternalFunctions work with only one argument.
	 */
	public void testUnaryExternalFunction() {
		TestModelFactory.ModelInformation info = TestModelFactory.getModel();

		PredicateFactory predicateFactory = PredicateFactory.getFactory();
		SpyFunction function = new SpyFunction(1);
		Predicate functionPredicate = predicateFactory.createFunctionalPredicate("UnaryFunction", function);

		// Add a rule using the new function.
		// 10: Person(A) & Person(B) & UnaryFunction(A) & UnaryFunction(B) & (A - B) -> Friends(A, B) ^2
		Rule rule = new WeightedLogicalRule(
				new Implication(
					new Conjunction(
						new QueryAtom(info.predicates.get("Person"), new Variable("A")),
						new QueryAtom(info.predicates.get("Person"), new Variable("B")),
						new QueryAtom(functionPredicate, new Variable("A")),
						new QueryAtom(functionPredicate, new Variable("B")),
						new QueryAtom(SpecialPredicate.NotEqual, new Variable("A"), new Variable("B"))
					),
					new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
				),
				10.0,
				true);
		info.model.addRule(rule);

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
		MPEInference mpe = null;

		try {
			mpe = new MPEInference(info.model, inferDB, info.config);
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during MPE constructor.");
		}

		mpe.mpeInference();
		mpe.close();
		inferDB.close();

		// There are 5 people, so we expect the function to be called on the forward and reverse crossproducts.
		// (5 * 5) * 2
		assertEquals(50, function.getCallCount());
	}

	@Test
	/**
	 * Ensure that ExternalFunctions work with three arguments.
	 */
	public void testTernaryExternalFunction() {
		TestModelFactory.ModelInformation info = TestModelFactory.getModel();

		PredicateFactory predicateFactory = PredicateFactory.getFactory();
		SpyFunction function = new SpyFunction(3);
		Predicate functionPredicate = predicateFactory.createFunctionalPredicate("TernaryFunction", function);

		// Add a rule using the new function.
		// 10: Person(A) & Person(B) & TernaryFunction(A, B, A) & (A - B) -> Friends(A, B) ^2
		Rule rule = new WeightedLogicalRule(
				new Implication(
					new Conjunction(
						new QueryAtom(info.predicates.get("Person"), new Variable("A")),
						new QueryAtom(info.predicates.get("Person"), new Variable("B")),
						new QueryAtom(functionPredicate, new Variable("A"), new Variable("B"), new Variable("A")),
						new QueryAtom(SpecialPredicate.NotEqual, new Variable("A"), new Variable("B"))
					),
					new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
				),
				10.0,
				true);
		info.model.addRule(rule);

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
		MPEInference mpe = null;

		try {
			mpe = new MPEInference(info.model, inferDB, info.config);
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during MPE constructor.");
		}

		mpe.mpeInference();
		mpe.close();
		inferDB.close();

		// TODO(eriq): Experimentally, this is 40. Is this correct?
		assertEquals(40, function.getCallCount());
	}

	/**
	 * A spy ExternalFunction.
	 * Only returns 1, but keeps track of how many times it was called.
	 * The number of arguments it accepts is set on construction.
	 */
	private class SpyFunction implements ExternalFunction {
		private int callCount;
		private int arity;

		public SpyFunction(int arity) {
			this.arity = arity;
			callCount = 0;
		}

		public int getArity() {
			return arity;
		}

		public ConstantType[] getArgumentTypes() {
			ConstantType[] args = new ConstantType[arity];
			for (int i = 0; i < arity; i++) {
				args[i] = ConstantType.UniqueID;
			}

			return args;
		}

		public double getValue(ReadOnlyDatabase db, Constant... args) {
			callCount++;
			return 1;
		}

		public int getCallCount() {
			return callCount;
		}
	}
}
