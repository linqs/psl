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
package edu.umd.cs.psl.model.kernel.predicateconstraint;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * Induces {@link GroundSymmetricPredicateConstraint} {@link GroundKernel GroundKernels}
 * to ensure symmetry among {@link Atom Atoms} of a {@link StandardPredicate}.
 */
public class SymmetricPredicateConstraintKernel implements Kernel {

	private final StandardPredicate predicate;
	private final int hashcode;

	public SymmetricPredicateConstraintKernel(StandardPredicate p) {
		Preconditions
				.checkArgument(p.getNumberOfValues() == 1,
						"Predicate Constraints are only supported on single valued predicates!");
		Preconditions
				.checkArgument(p.getArity() == 2,
						"Only binary predicates are supported.");
		predicate = p;
		hashcode = new HashCodeBuilder().append(predicate).toHashCode();
	}

	@Override
	public Kernel clone() {
		return new SymmetricPredicateConstraintKernel(predicate);
	}

	public StandardPredicate getPredicate() {
		return predicate;
	}

	@Override
	public Parameters getParameters() {
		return Parameters.NoParameters;
	}

	@Override
	public boolean isCompatibilityKernel() {
		return false;
	}

	@Override
	public void setParameters(Parameters para) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void groundAll(ModelApplication app) {
		// Not needed, triggered only upon insertion
	}

	/**
	 * notifyAtomEvent will listen for introductions of inference {@link Atom Atoms}.
	 * If an Atom p(A,B) of this Kernel's {@link Predicate} is introduced, then this
	 * Kernel will introduce a {@link GroundSymmetricPredicateConstraint}
	 * between p(A,B) and p(B,A), constraining their truth values to be equal,
	 * unless this GroundKernel already exists or A == B.
	 */
	@Override
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode,
			ModelApplication app) {
		if (AtomEventSets.IntroducedInferenceAtom.subsumes(event)) {
			if (atom.getRegisteredGroundKernels(this).isEmpty()) {
				GroundTerm[] terms = (GroundTerm[]) atom.getArguments();
				/* If A != B... */
				if (!terms[0].equals(terms[1])) {
					Atom atomA = atom;
					GroundTerm[] newTerms = {terms[1], terms[0]};
					Atom atomB = app.getAtomManager().getAtom(predicate, newTerms);
					
					GroundSymmetricPredicateConstraint con = new GroundSymmetricPredicateConstraint(this, atomA, atomB);
	
					GroundKernel oldcon = app.getGroundKernel(con);
					if (oldcon == null) {
						app.addGroundKernel(con);
					}
				}
			} // else it already has such a constraint defined
		} else {
			throw new UnsupportedOperationException(
					"Currently, only insertions are supported!");
		}
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework framework,
			DatabaseAtomStoreQuery db) {
		framework.registerAtomEventObserver(predicate,
				AtomEventSets.IntroducedReleasedInferenceAtom, this);

	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework framework,
			DatabaseAtomStoreQuery db) {
		framework.unregisterAtomEventObserver(predicate,
				AtomEventSets.IntroducedReleasedInferenceAtom, this);
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
