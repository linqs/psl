/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.database;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;

/**
 * Contract tests for classes that implement {@link ConicProgramSolver}.
 */
abstract public class DataStoreContractTest {
	
	private DataStore datastore;
	private PredicateFactory predicateFactory;
	
	/**
	 * @return the DataStore to be tested, should always be backed by the same
	 *             persistence mechanism
	 */
	abstract public DataStore getDataStore();
	
	/**
	 * Deletes any files and releases any resources used by the tested DataStore
	 * and its persistence mechanism
	 */
	abstract public void cleanUp();

	@Before
	public void setUp() throws Exception {
		datastore = getDataStore();
		predicateFactory = PredicateFactory.getFactory();
	}

	@After
	public void tearDown() throws Exception {
		cleanUp();
	}

	@Test
	public void testInserter() {
		ArgumentType[] types = {ArgumentType.UniqueID, ArgumentType.UniqueID}; 
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", types);
		
		datastore.registerPredicate(p1);
		Inserter inserter = datastore.getInserter(p1, new Partition(0));
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		
		inserter.insert(a, b);
		inserter.insertValue(0.5, b, c);
	}

}
