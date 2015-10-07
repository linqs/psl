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
package edu.umd.cs.psl.model.kernel.predicateconstraint;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.AbstractKernel;
import edu.umd.cs.psl.model.kernel.ConstraintKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.util.database.Queries;

/**
 * Induces {@link GroundSymmetryConstraint GroundSymmetryConstraints}
 * between {@link Atom Atoms} of a binary {@link StandardPredicate} with
 * symmetric arguments.
 */
public class SymmetryConstraintKernel extends AbstractKernel implements ConstraintKernel {

	private final StandardPredicate predicate;
	private final int hashcode;

	public SymmetryConstraintKernel(StandardPredicate p) {
		Preconditions
				.checkArgument(p.getArity() == 2,
						"Only binary predicates are supported.");
		predicate = p;
		hashcode = new HashCodeBuilder().append(predicate).toHashCode();
	}

	@Override
	public Kernel clone() {
		return new SymmetryConstraintKernel(predicate);
	}

	public StandardPredicate getPredicate() {
		return predicate;
	}

	@Override
	public Parameters getParameters() {
		return Parameters.NoParameters;
	}

	@Override
	public void setParameters(Parameters para) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundKernelStore gks) {
		ResultList results = atomManager.executeQuery(Queries.getQueryForAllAtoms(predicate));
		for (int i = 0; i < results.size(); i++)
			groundConstraint(atomManager.getAtom(predicate, results.get(i)), atomManager, gks);
	}

	/**
	 * Listens for the consideration of a {@link RandomVariableAtom}.
	 * If such an Atom p(A,B) of this Kernel's {@link Predicate} is considered, then this
	 * Kernel will introduce a {@link GroundSymmetryConstraint}
	 * between p(A,B) and p(B,A).
	 */
	@Override
	public void notifyAtomEvent(AtomEvent event, GroundKernelStore gks) {
		GroundAtom atom = event.getAtom();
		groundConstraint(atom, event.getEventFramework(), gks);
	}
	
	
	/**
	 * Introduces a {@link GroundSymmetryConstraint} between p(A,B) and p(B,A),
	 * constraining their truth values to be equal, unless this GroundKernel
	 * already exists or A == B.
	 */
	private void groundConstraint(GroundAtom atom, AtomManager atomManager, GroundKernelStore gks) {
		GroundTerm[] terms = (GroundTerm[]) atom.getArguments();
		/* If A != B... */
		if (!terms[0].equals(terms[1])) {
			GroundAtom atomA = atom;
			GroundTerm[] newTerms = {terms[1], terms[0]};
			GroundAtom atomB = atomManager.getAtom(predicate, newTerms);
			
			GroundSymmetryConstraint con = new GroundSymmetryConstraint(this, atomA, atomB);
			if(!gks.containsGroundKernel(con)) {
				gks.addGroundKernel(con);
			}
		}
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework framework) {
		framework.registerAtomEventListener(AtomEvent.ConsideredEventTypeSet, predicate, this);

	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework framework) {
		framework.unregisterAtomEventListener(AtomEvent.ConsideredEventTypeSet, predicate, this);
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public String toString() {
		return "{constraint} Symmetry on " + predicate.toString();
	}

}
