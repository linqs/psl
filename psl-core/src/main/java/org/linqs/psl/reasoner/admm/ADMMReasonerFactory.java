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
package org.linqs.psl.reasoner.admm;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.ReasonerFactory;

/**
 * Factory for a {@link ADMMReasoner}.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ADMMReasonerFactory implements ReasonerFactory {

	@Override
	public Reasoner getReasoner(ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		return new ADMMReasoner(config);
	}

}
