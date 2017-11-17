package org.linqs.psl.application.inference;

import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class BooleanMaxWalkSatTest {
	/**
	 * A quick test that only checks to see if MPEInference works with BooleanMaxWalkSat.
	 * This is not a targeted or exhaustive test, just a starting point.
	 */
	@Test
	public void baseTest() {
		TestModelFactory.ModelInformation info = TestModelFactory.getModel();

		info.config.setProperty(MPEInference.REASONER_KEY, "org.linqs.psl.reasoner.bool.BooleanMaxWalkSat");
		info.config.setProperty(MPEInference.GROUND_RULE_STORE_KEY, "org.linqs.psl.application.groundrulestore.AtomRegisterGroundRuleStore");
		info.config.setProperty(MPEInference.TERM_STORE_KEY, "org.linqs.psl.reasoner.term.ConstraintBlockerTermStore");
		info.config.setProperty(MPEInference.TERM_GENERATOR_KEY, "org.linqs.psl.reasoner.term.ConstraintBlockerTermGenerator");

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
		MPEInference mpe = new MPEInference(info.model, inferDB, info.config);

		mpe.mpeInference();
		mpe.close();
		inferDB.close();
	}
}
