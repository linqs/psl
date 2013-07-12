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

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;

public interface GroundCompatibilityKernel extends GroundKernel {
	
	@Override
	public CompatibilityKernel getKernel();
	
	/**
	 * Returns the Weight of this GroundCompatibilityKernel.
	 * <p>
	 * Until {@link #setWeight(Weight)} is called, this GroundKernel's weight
	 * is the current weight of its parent Kernel. After it is called, it remains
	 * the most recent Weight set by {@link #setWeight(Weight)}.
	 * 
	 * @return this GroundKernel's Weight
	 * @see CompatibilityKernel#getWeight()
	 */
	public Weight getWeight();
	
	/**
	 * Sets a weight for this GroundCompatibilityKernel.
	 * 
	 * @param w  new weight
	 */
	public void setWeight(Weight w);
	
	public FunctionTerm getFunctionDefinition();

	/**
	 * Returns the incompatibility of the truth values of this GroundKernel's
	 * {@link GroundAtom GroundAtoms}.
	 * <p>
	 * Incompatibility is always non-negative.
	 * 
	 * @return the incompatibility of the current truth values
	 */
	public double getIncompatibility();
	
}
