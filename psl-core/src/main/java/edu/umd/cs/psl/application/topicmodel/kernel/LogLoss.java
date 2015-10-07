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
package edu.umd.cs.psl.application.topicmodel.kernel;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;

/**
 * First order log loss kernels, useful when PSL variables are given a
 * probabilistic interpretation, as in latent topic networks.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class LogLoss implements Kernel {

	@Override
	public void notifyAtomEvent(AtomEvent event) {
		// TODO Auto-generated method stub
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundKernelStore gks) {
		// TODO Auto-generated method stub
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework eventFramework,
			GroundKernelStore gks) {
		// TODO Auto-generated method stub
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework eventFramework,
			GroundKernelStore gks) {
		// TODO Auto-generated method stub
	}

	@Override
	public Parameters getParameters() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setParameters(Parameters para) {
		// TODO Auto-generated method stub
	}

	@Override
	public Kernel clone() throws CloneNotSupportedException {
		// TODO Auto-generated method stub
		return null;
	}

}
