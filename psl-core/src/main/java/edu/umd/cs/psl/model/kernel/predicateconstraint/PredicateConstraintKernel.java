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

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.kernel.AbstractKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * Produces {@link GroundPredicateConstraint GroundPredicateConstraints}.
 * 
 * Checks whether sets of {@link Atom Atoms} of a binary {@link StandardPredicate}
 * adhere to a {@link PredicateConstraintType}.
 */
public class PredicateConstraintKernel extends AbstractKernel {

	private final StandardPredicate predicate;
	private final PredicateConstraintType constraintType;

	private final int hashcode;

	public PredicateConstraintKernel(StandardPredicate p,
			PredicateConstraintType t) {
		Preconditions.checkArgument(p.getArity() == 2, "Currently, " +
				"PredicateConstraints only support binary predicates.");
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
	public boolean isCompatibilityKernel() {
		return false;
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundKernelStore gks) {
		/* Not needed, triggered only upon insertion */
	}
	
	@Override
	protected void notifyAtomEvent(AtomEvent event, GroundKernelStore gks) {
		// TODO: I am not sure this if statement is necessary... it will only receive events it registered for...
		/* When an Atom is considered... */
		if (event == AtomEvent.ConsideredRVAtom) {
			/*
			 * ...checks to see if that Atom has already been added to the
			 * appropriate ground Kernel. (It shouldn't have.)
			 */
			if (event.getAtom().getRegisteredGroundKernels(this).isEmpty()) {
				/* Constructs the ground Kernel in order to see if it already exists */
				GroundAtom atom = event.getAtom();
				int pos = constraintType.position();
				GroundTerm anchor = (GroundTerm) atom.getArguments()[pos];
				GroundTerm other = (GroundTerm) atom.getArguments()[1 - pos];
				GroundPredicateConstraint con = new GroundPredicateConstraint(this, anchor);

				GroundKernel oldcon = gks.getGroundKernel(con);
				
				/* If it already exists, adds the considered Atom to it */
				if (oldcon != null) {
					((GroundPredicateConstraint) oldcon).addAtom(atom);
					gks.changedGroundKernel(oldcon);
				} else {
					con.addAtom(atom);
					// Check for atoms from database
					Variable var = new Variable("V");

					Term[] args = new Term[2];
					args[pos] = anchor;
					args[1 - pos] = var;
					DatabaseQuery query = new DatabaseQuery(new QueryAtom(predicate, args));
					query.getProjectionSubset().add(var);

					ResultList res = event.getEventFramework().getDatabase().executeQuery(query);
					// TODO Fix me: ResultList res = app.getAtomManager().getActiveGroundings(query, ImmutableList.of(var));
					for (int i = 0; i < res.size(); i++) {
						GroundTerm[] terms = new GroundTerm[2];
						terms[pos] = anchor;
						terms[1 - pos] = res.get(i)[0];
						if (!terms[1 - pos].equals(other)) {
							con.addAtom(event.getEventFramework().getAtom(predicate,
									terms));
						}
					}
					gks.addGroundKernel(con);
				}
			} /* else it already has such a constraint defined */
		} else {
			 /* No handling for other events */
			throw new UnsupportedOperationException("Unsupported event encountered: " + event);
		}
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework manager) {
		manager.registerAtomEventListener(ConsideredEventSet, predicate, this);
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework manager) {
		manager.unregisterAtomEventListener(ConsideredEventSet, predicate, this);
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
