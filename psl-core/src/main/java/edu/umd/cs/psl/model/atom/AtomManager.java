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

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * The atom manager interface contains base functionality for {@link AtomEventFramework}. 
 * @author
 *
 */
public interface AtomManager {

	public Atom getAtom(Predicate p, GroundTerm[] arguments);

	public void changeCertainty(Atom atom, double[] values,	double[] confidences);
	
	public void release(Atom atom);
	
	public void checkToDeactivate(Atom atom);
	
}
