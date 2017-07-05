/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model.atom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.config.EmptyBundle;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.RDBMSPartition;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.model.atom.AtomEvent;
import org.linqs.psl.model.atom.AtomEventFramework;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueID;
import org.linqs.psl.model.term.Variable;

public class AtomEventFrameworkTest {
	
	private AtomEventFramework framework;
	private StandardPredicate p1, p2;
	private UniqueID a, b;
	
	@Before
	public final void setUp() throws ConfigurationException {
		PredicateFactory pf = PredicateFactory.getFactory();
		p1 = pf.createStandardPredicate("AtomEventFrameworkTest_P1", ConstantType.UniqueID, ConstantType.UniqueID);
		p2 = pf.createStandardPredicate("AtomEventFrameworkTest_P2", ConstantType.UniqueID, ConstantType.UniqueID);
		
		DataStore dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Memory, null, true), new EmptyBundle());
		dataStore.registerPredicate(p1);
		dataStore.registerPredicate(p2);
		a = dataStore.getUniqueID(0);
		b = dataStore.getUniqueID(1);
		
		framework = new AtomEventFramework(dataStore.getDatabase(dataStore.getPartition("0")), new EmptyBundle());
	}
	
	@Test
	public void testRegisterAndWorkOffJobQueue() {
		DummyListener p1ConsiderationListener = new DummyListener();
		framework.registerAtomEventListener(AtomEvent.ConsideredEventTypeSet, p1, p1ConsiderationListener);
		
		DummyListener p1ActivationListener = new DummyListener();
		framework.registerAtomEventListener(AtomEvent.ActivatedEventTypeSet, p1, p1ActivationListener);
		
		DummyListener p2AllEventsListener = new DummyListener();
		framework.registerAtomEventListener(AtomEvent.AllEventTypesSet, p2, p2AllEventsListener);
		
		DummyListener allPredicatesConsiderationListener = new DummyListener();
		framework.registerAtomEventListener(AtomEvent.ConsideredEventTypeSet, allPredicatesConsiderationListener);
		
		DummyListener allPredicatesActivationListener = new DummyListener();
		framework.registerAtomEventListener(AtomEvent.ActivatedEventTypeSet, allPredicatesActivationListener);
		
		DummyListener allPredicatesAllEventsListener = new DummyListener();
		framework.registerAtomEventListener(AtomEvent.AllEventTypesSet, allPredicatesAllEventsListener);
		
		/*
		 * Tests consideration of a p1 atom
		 */
		RandomVariableAtom atom = (RandomVariableAtom) framework.getAtom(p1, a, b);
		framework.workOffJobQueue();
		assertEquals(AtomEvent.Type.ConsideredRVAtom, p1ConsiderationListener.lastEvent.getType());
		p1ConsiderationListener.lastEvent = null;
		assertNull(p1ActivationListener.lastEvent);
		assertNull(p2AllEventsListener.lastEvent);
		assertEquals(AtomEvent.Type.ConsideredRVAtom, allPredicatesConsiderationListener.lastEvent.getType());
		allPredicatesConsiderationListener.lastEvent = null;
		assertNull(allPredicatesActivationListener.lastEvent);
		assertEquals(AtomEvent.Type.ConsideredRVAtom, allPredicatesAllEventsListener.lastEvent.getType());
		allPredicatesAllEventsListener.lastEvent = null;
		
		/*
		 * Tests activation of a p1 atom
		 */
		atom.setValue(1.0);
		assertEquals(1, framework.checkToActivate());
		framework.workOffJobQueue();
		ResultList results = framework.executeQuery(new DatabaseQuery(new QueryAtom(p1, new Variable("X"), new Variable("Y"))));
		/* Tests that the event framework committed the Atom */
		assertEquals(1, results.size());
		assertNull(p1ConsiderationListener.lastEvent);
		assertEquals(AtomEvent.Type.ActivatedRVAtom, p1ActivationListener.lastEvent.getType());
		p1ActivationListener.lastEvent = null;
		assertNull(p2AllEventsListener.lastEvent);
		assertNull(allPredicatesConsiderationListener.lastEvent);
		assertEquals(AtomEvent.Type.ActivatedRVAtom, allPredicatesActivationListener.lastEvent.getType());
		allPredicatesActivationListener.lastEvent = null;
		assertEquals(AtomEvent.Type.ActivatedRVAtom, allPredicatesAllEventsListener.lastEvent.getType());
		allPredicatesAllEventsListener.lastEvent = null;
	}
	
	private class DummyListener implements AtomEvent.Listener {
		
		private AtomEvent lastEvent = null;

		@Override
		public void notifyAtomEvent(AtomEvent event) {
			lastEvent = event;
		}
		
	}

}
