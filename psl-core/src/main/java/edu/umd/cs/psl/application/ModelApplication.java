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
package edu.umd.cs.psl.application;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.ModelEvent;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;

public interface ModelApplication extends ModelEvent.Listener {
	
	public void addGroundKernel(GroundKernel e);
	
	public void changedGroundKernel(GroundKernel e);
	
	public void removeGroundKernel(GroundKernel e);
	
	public boolean containsGroundKernel(GroundKernel e);
	
	public GroundKernel getGroundKernel(GroundKernel e);

	public Iterable<GroundCompatibilityKernel> getCompatibilityKernels();
	
	public Iterable<GroundKernel> getGroundKernel();
	
	public DatabaseAtomStoreQuery getDatabase();

	public AtomManager getAtomManager();	
	
	
	
	public void close();
}
