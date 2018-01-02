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
package org.linqs.psl.reasoner;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.reasoner.inspector.ReasonerInspector;
import org.linqs.psl.reasoner.term.TermStore;

/**
 * An oprimizer to minimize the total weighted incompatibility
 * of the terms provided by a TermStore.
 */
public abstract class Reasoner {
	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "reasoner";

	/**
	 * Key for the ReasonerInspector to use.
	 * Defaults to no inspector.
	 */
	public static final String REASONER_INSPECTOR_KEY = CONFIG_PREFIX + ".inspector";

	/**
	 * The reasoner to give status updates to.
	 * May be null.
	 */
	protected ReasonerInspector inspector;

	public Reasoner(ConfigBundle config) {
		inspector = (ReasonerInspector)config.getNewObject(REASONER_INSPECTOR_KEY, null);
	}

	/**
	 * Minimizes the total weighted incompatibility of the terms in the provided
	 * TermStore.
	 */
	public abstract void optimize(TermStore termStore);

	/**
	 * Releases all resources acquired by this Reasoner.
	 */
	public abstract void close();
}
