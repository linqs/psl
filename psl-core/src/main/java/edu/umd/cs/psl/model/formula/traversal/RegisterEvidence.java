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
package edu.umd.cs.psl.model.formula.traversal;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.GroundKernel;

public class RegisterEvidence extends FormulaTraverser {

	private final GroundKernel evidence;
	
	public RegisterEvidence(GroundKernel e) {
		evidence = e;
	}
	
	@Override
	public void visitAtom(Atom atom) {
		assert atom.isGround() && atom.isDefined();
		atom.registerGroundKernel(evidence);
	}
	
	public static void register(Formula f, GroundKernel e) {
		//TODO: Do we need this still?
		FormulaTraverser.traverse(f, new RegisterEvidence(e));
	}
	
}
