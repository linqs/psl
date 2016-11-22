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
package org.linqs.psl.reasoner.conic;

import org.linqs.psl.model.rule.GroundRule;

abstract class ConicProgramProxy {
	protected final ConicReasoner reasoner;
	protected final GroundRule kernel;
	
	ConicProgramProxy(ConicReasoner reasoner, GroundRule kernel) {
		this.reasoner = reasoner;
		this.kernel = kernel;
	}
	
	public GroundRule getGroundKernel() {
		return kernel;
	}
	
	abstract void remove();
}
