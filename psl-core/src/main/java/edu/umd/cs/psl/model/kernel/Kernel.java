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
package edu.umd.cs.psl.model.kernel;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventObserver;
import edu.umd.cs.psl.model.parameters.Parameters;


/**
 * Kernels provide a framework to express the adherence of a formula with some set of data. 
 * Kernels currently support formulas that include constraints as well as relations between 
 * predicates. By grounding the atoms  
 * GroundKernels measure the incompatibility of a particular formula given a grounding of the atoms
 * 
 * *upon initial grounding or triggered by changes to individual atoms. Evidence types register new
 * *evidence with the model application and are responsible for communicating changes to atom states
 * *to the model application.
 * 
 * @author Matthias Broecheler (mail@knowledgefrominformation.com)
 *
 */
public interface Kernel extends AtomEventObserver, Cloneable {

	public void registerForAtomEvents(AtomEventFramework framework, DatabaseAtomStoreQuery db);
	
	public void unregisterForAtomEvents(AtomEventFramework framework, DatabaseAtomStoreQuery db);
	
	public void groundAll(ModelApplication app);
	
	public Parameters getParameters();
	
	public void setParameters(Parameters para);
	
	public boolean isCompatibilityKernel();
	
	public Kernel clone() throws CloneNotSupportedException;
	
}
