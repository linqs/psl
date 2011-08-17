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
package edu.umd.cs.psl.model.atom;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;

/**
 * An atom event observer (listener) interface.
 * 
 * @author
 *
 */
public interface AtomEventObserver {

	/**
	 * Method called by manager to inform observer of atom update.
	 * 
	 * @param event The atom event
	 * @param atom The updated atom
	 * @param mode The grounding mode
	 * @param app The model application
	 */
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode, ModelApplication app);
	
}
