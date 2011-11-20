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
import com.google.common.collect.ImmutableList;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.*;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * The PredicateConstraintKernel specifies a kernel that measures the adherence
 * of a {@link edu.umd.cs.psl.model.predicate.StandardPredicate
 * StandardPredicate} with a
 * {@link edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType
 * PredicateConstraintType}. Currently the kernel only supports single-valued,
 * binary predicates.
 * 
 * @see edu.umd.cs.psl.model.predicate.StandardPredicate
 * @see edu.umd.cs.psl.model.kernel.predicateconstraint.PredicateConstraintType
 * 
 */
public class PredicateConstraintKernel implements Kernel {

	private final StandardPredicate predicate;
	private final PredicateConstraintType constraintType;

	private final int hashcode;

	public PredicateConstraintKernel(StandardPredicate p,
			PredicateConstraintType t) {
		Preconditions
				.checkArgument(p.getNumberOfValues() == 1,
						"Predicate Constraints are only supported on single valued predicates!");
		Preconditions
				.checkArgument(p.getArity() == 2,
						"Currently, PredicateConstraints only support binary predicates!");
		constraintType = t;
		predicate = p;

		hashcode = new HashCodeBuilder().append(predicate)
				.append(constraintType).toHashCode();
	}

	@Override
	public Kernel clone() {
		return new PredicateConstraintKernel(predicate, constraintType);
	}

	public PredicateConstraintType getConstraintType() {
		return constraintType;
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
		throw new UnsupportedOperationException(
				"This evidence type does not have parameters!");
	}

	@Override
	public void groundAll(ModelApplication app) {
		// Not needed, triggered only upon insertion
	}

	/**
	 * notifyAtomEvent will listen for introductions of inference atoms. If
	 * there are no registered {@link edu.umd.cs.psl.model.kernel.GroundKernel
	 * GroundKernels} for the atom this method will either update an existing
	 * {@link edu.umd.cs.psl.model.kernel.predicateconstraint.GroundPredicateConstraint
	 * GroundPredicateConstraint} in the
	 * {@link edu.umd.cs.psl.application.ModelApplication ModelApplication} or
	 * create a new
	 * {@link edu.umd.cs.psl.model.kernel.predicateconstraint.GroundPredicateConstraint
	 * GroundPredicateConstraint} to add to the
	 * {@link edu.umd.cs.psl.application.ModelApplication ModelApplication}
	 * 
	 */
	@Override
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode,
			ModelApplication app) {
		if (AtomEventSets.IntroducedInferenceAtom.subsumes(event)) {
			if (atom.getRegisteredGroundKernels(this).isEmpty()) {
				int pos = constraintType.position();
				assert atom.getArguments()[pos] instanceof Entity;
				assert atom.getArity() == 2;
				Entity anchor = (Entity) atom.getArguments()[pos];
				GroundTerm other = (GroundTerm) atom.getArguments()[1 - pos];
				GroundPredicateConstraint con = new GroundPredicateConstraint(
						this, anchor);

				GroundKernel oldcon = app.getGroundKernel(con);
				if (oldcon != null) {
					((GroundPredicateConstraint) oldcon).addAtom(atom);
					app.changedGroundKernel(oldcon);
				} else {
					con.addAtom(atom);
					// Check for atoms from database
					Variable var = new Variable("V");

					Term[] args = new Term[2];
					args[pos] = anchor;
					args[1 - pos] = var;
					Atom query = new TemplateAtom(predicate, args);

					ResultList res = app.getDatabase().query(query,
							ImmutableList.of(var));
					for (int i = 0; i < res.size(); i++) {
						GroundTerm[] terms = new GroundTerm[2];
						terms[pos] = anchor;
						terms[1 - pos] = res.get(i)[0];
						if (!terms[1 - pos].equals(other)) {
							con.addAtom(app.getAtomManager().getAtom(predicate,
									terms));
						}
					}
					app.addGroundKernel(con);
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
		return "{constraint} " + constraintType.toString() + " on " + predicate.toString();
	}

}
