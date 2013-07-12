/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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

import java.util.Set;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;

/**
 * A function that either constrains or measures the compatibility of the
 * truth values of {@link GroundAtom GroundAtoms}.
 * <p>
 * GroundKernels are templated by a parent {@link Kernel}.
 */
public interface GroundKernel {

	/**
	 * Notifies this GroundKernel that the parameterization of its parent
	 * {@link Kernel} has changed.
	 * 
	 * @return TRUE if this GroundKernel's incompatibility changed
	 * @see Kernel#getParameters()
	 * @see #getIncompatibility()
	 */
	public boolean updateParameters();

	/**
	 * @return this GroundKernel's parent {@link Kernel}
	 */
	public Kernel getKernel();

	/**
	 * @return set of {@link GroundAtom GroundAtoms} which determine this
	 *             GroundKernel's incompatibility or infeasibility
	 */
	public Set<GroundAtom> getAtoms();

	/**
	 * Something about whether GroundAtoms can be removed from this GroundKernel
	 * if they have truth value of 0.0...
	 */
	public BindingMode getBinding(Atom atom);
}
