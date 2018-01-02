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

package org.linqs.psl.reasoner.bool;

import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class BooleanMCSatTest {
	private TestModelFactory.ModelInformation info;

	@Before
	public void setup() {
		info = TestModelFactory.getModel();

		info.config.setProperty(MPEInference.REASONER_KEY, "org.linqs.psl.reasoner.bool.BooleanMCSat");
		info.config.setProperty(MPEInference.GROUND_RULE_STORE_KEY, "org.linqs.psl.application.groundrulestore.AtomRegisterGroundRuleStore");
		info.config.setProperty(MPEInference.TERM_STORE_KEY, "org.linqs.psl.reasoner.term.ConstraintBlockerTermStore");
		info.config.setProperty(MPEInference.TERM_GENERATOR_KEY, "org.linqs.psl.reasoner.term.ConstraintBlockerTermGenerator");
	}

	/**
	 * A quick test that only checks to see if MPEInference works with BooleanMCSat.
	 * This is not a targeted or exhaustive test, just a starting point.
	 */
	@Test
	public void baseTest() {
		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
		MPEInference mpe = new MPEInference(info.model, inferDB, info.config);

		mpe.mpeInference();
		mpe.close();
		inferDB.close();
	}
}
