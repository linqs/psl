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
package edu.umd.cs.psl.application.util;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.kernel.Kernel;

public class Grounding {

	private final static com.google.common.base.Predicate<Kernel> all = new com.google.common.base.Predicate<Kernel>(){
		@Override
		public boolean apply(Kernel el) {	return true; }
	};
	
	public static void groundAll(Model m, ModelApplication app) {
		groundAll(m,app,all);
	}
	
	public static void groundAll(Model m, ModelApplication app, com.google.common.base.Predicate<Kernel> filter) {
		for (Kernel me : m.getKernels()) {
			if (filter.apply(me))
				me.groundAll(app);
		}
	}
	
}
