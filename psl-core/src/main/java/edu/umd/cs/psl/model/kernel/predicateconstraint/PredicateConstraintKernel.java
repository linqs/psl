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

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * Produces {@link GroundPredicateConstraint GroundPredicateConstraints}.
 * 
 * Checks whether sets of {@link Atom Atoms} of a binary {@link StandardPredicate}
 * adhere to a {@link PredicateConstraintType}.
 */
public class PredicateConstraintKernel implements Kernel {

	private final StandardPredicate predicate;
	private final PredicateConstraintType constraintType;

	private final int hashcode;

	public PredicateConstraintKernel(StandardPredicate p,
			PredicateConstraintType t) {
		Preconditions
				.checkArgument(p.getArity() == 2,
						"Currently, PredicateConstraints only support binary predicates.");
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
		throw new UnsupportedOperationException("This Kernel does not have parameters.");
	}

	@Override
	public void groundAll(ModelApplication app) {
		/* Not needed, triggered only upon insertion */
	}

	@Override
	public void notifyAtomEvent(AtomEvent event) {
		/* When an Atom is considered... */
		if (AtomEventSets.ConsideredStandardAtom.contains(event)) {
			/*
			 * ...checks to see if that Atom has already been added to the
			 * appropriate ground Kernel. (It shouldn't have.)
			 */
			if (event.getAtom().getRegisteredGroundKernels(this).isEmpty()) {
				/* Constructs the ground Kernel in order to see if it already exists */
				Atom atom = event.getAtom();
				ModelApplication app = event.getModelApplication();
				int pos = constraintType.position();
				Entity anchor = (Entity) atom.getArguments()[pos];
				GroundTerm other = (GroundTerm) atom.getArguments()[1 - pos];
				GroundPredicateConstraint con = new GroundPredicateConstraint(
						this, anchor);

				GroundKernel oldcon = app.getGroundKernel(con);
				
				/* If it already exists, adds the considered Atom to it */
				if (oldcon != null) {
					((GroundPredicateConstraint) oldcon).addAtom(atom);
					app.changedGroundKernel(oldcon);
				}
				else {
					con.addAtom(atom);
					// Check for atoms from database
					Variable var = new Variable("V");

					Term[] args = new Term[2];
					args[pos] = anchor;
					args[1 - pos] = var;
					Atom query = new QueryAtom(predicate, args);

					ResultList res = app.getAtomManager().getActiveGroundings(query,
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
			} /* else it already has such a constraint defined */
		} 
		/* No handling for other events */
		else {
			throw new UnsupportedOperationException("Unsupported event encountered: " + event);
		}
	}

	@Override
	public void registerForAtomEvents(AtomManager manager) {
		manager.registerAtomEventListener(
				AtomEventSets.ConsideredUnconsideredGroundAtom, predicate, this);
	}

	@Override
	public void unregisterForAtomEvents(AtomManager manager) {
		manager.registerAtomEventListener(
				AtomEventSets.ConsideredUnconsideredGroundAtom, predicate, this);
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
