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

import java.util.Collection;
import java.util.Set;

import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

public class TemplateAtom extends AbstractAtom {

	public TemplateAtom(Predicate p, Term[] args) {
		super(p, args, AtomStatus.Template);
	}
	
	@Override
	public boolean isGround() {
		for (Term arg : arguments) {
			if (!arg.isGround()) return false;
		}
		return true;
	}

	@Override
	public Collection<Atom> getAtomsInGroup(AtomManager atommanager) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAtomGroup() {
		return false;
	}
	
	//########## Unsupported Operations ##############

	@Override
	public boolean unregisterGroundKernel(GroundKernel f) {
		throw new UnsupportedOperationException();	}

	@Override
	public Collection<GroundKernel> getRegisteredGroundKernels() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getConfidenceValue() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<GroundKernel> getRegisteredGroundKernels(Kernel et) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public int getNumRegisteredGroundKernels() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getValue() {
		throw new UnsupportedOperationException();
	}

	@Override
	public AtomFunctionVariable getVariable() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean registerGroundKernel(GroundKernel f) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setConfidenceValue(double value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setValue(double value) {
		throw new UnsupportedOperationException();
	}
}
