package org.linqs.psl.application.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;

import java.util.HashSet;
import java.util.Set;

public class MPEInferenceTest {
	@Test
	/**
	 * A quick test that only checks to see if MPEInference is running.
	 * This is not a targeted or exhaustive test, just a starting point.
	 */
	public void baseTest() {
		TestModelFactory.ModelInformation info = TestModelFactory.getModel();

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
	}
}
