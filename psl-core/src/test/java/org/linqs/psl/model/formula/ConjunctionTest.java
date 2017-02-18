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
package org.linqs.psl.model.formula;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.linqs.psl.TestModelFactory;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.term.Variable;

/**
 * Check conjunctions.
 */
public class ConjunctionTest {
	private TestModelFactory.ModelInformation model;

	@Before
	public void setup() {
		model = TestModelFactory.getModel();
	}

	@After
	public void cleanup() {
		model.dataStore.close();
	}

	@Test
	public void testGetDNF() {
		Formula testtime = new Conjunction(
			new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
			new Disjunction(
				new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
				new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
			)
		);

		System.out.println(testtime.toString());
		testtime = testtime.getDNF();
		System.out.println(testtime.toString());
	}
}
