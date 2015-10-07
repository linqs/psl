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

import java.util.HashMap;
import java.util.Map;

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
import edu.umd.cs.psl.model.kernel.ConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.util.database.Queries;

/**
 * Produces {@link GroundDomainRangeConstraint GroundDomainRangeConstraints}.
 * <p>
 * Checks whether sets of {@link Atom Atoms} of a binary {@link StandardPredicate}
 * adhere to a {@link DomainRangeConstraintType}.
 */
public class DomainRangeConstraintKernel extends AbstractKernel implements ConstraintKernel {

	private final StandardPredicate predicate;
	private final DomainRangeConstraintType constraintType;
	private final Map<GroundTerm, Double> valueMap;

	private final int hashcode;
	
	public DomainRangeConstraintKernel(StandardPredicate p,
			DomainRangeConstraintType t) {
		this(p, t, new HashMap<GroundTerm, Double>());
	}

	public DomainRangeConstraintKernel(StandardPredicate p,
			DomainRangeConstraintType t, Map<GroundTerm, Double> constrainedValueMap) {
		super();
		Preconditions.checkArgument(p.getArity() == 2, "Currently, " +
				"DomainRangeConstraintKernels only support binary predicates.");
		constraintType = t;
		predicate = p;
		valueMap = constrainedValueMap;

		hashcode = new HashCodeBuilder().append(predicate)
				.append(constraintType).toHashCode();
	}

	@Override
	public Kernel clone() {
		return new DomainRangeConstraintKernel(predicate, constraintType, new HashMap<GroundTerm, Double>(valueMap));
	}

	public DomainRangeConstraintType getConstraintType() {
		return constraintType;
	}

	public StandardPredicate getPredicate() {
		return predicate;
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundKernelStore gks) {
		ResultList results = atomManager.executeQuery(Queries.getQueryForAllAtoms(predicate));
		for (int i = 0; i < results.size(); i++)
			groundConstraint(atomManager.getAtom(predicate, results.get(i)), atomManager, gks);
	}

	@Override
	protected void notifyAtomEvent(AtomEvent event, GroundKernelStore gks) {
		//if (event.getAtom().getRegisteredGroundKernels(this).isEmpty()) {
		groundConstraint(event.getAtom(), event.getEventFramework(), gks);
	}
	
	private void groundConstraint(GroundAtom atom, AtomManager atomManager, GroundKernelStore gks) {
		/* Constructs the ground Kernel in order to see if it already exists */
		int pos = constraintType.position();
		GroundTerm anchor = (GroundTerm) atom.getArguments()[pos];
		GroundTerm other = (GroundTerm) atom.getArguments()[1 - pos];
		Double value = valueMap.get(anchor);
		if (value == null) {
			value = 1.0;
		}
		GroundDomainRangeConstraint con = new GroundDomainRangeConstraint(this, anchor, value);

		GroundKernel oldcon = gks.getGroundKernel(con);

		/* If it already exists, adds the considered Atom to it */
		if (oldcon != null) {
			((GroundDomainRangeConstraint) oldcon).addAtom(atom);
			gks.changedGroundKernel(oldcon);
		} else {
			con.addAtom(atom);
			
			/* Check for Atoms from database */
			Variable var = new Variable("V");
			Term[] args = new Term[2];
			args[pos] = anchor;
			args[1 - pos] = var;
			
			/* Constructs and executes the DatabaseQuery */
			DatabaseQuery query = new DatabaseQuery(new QueryAtom(predicate, args));
			query.getProjectionSubset().add(var);
			ResultList res = atomManager.executeQuery(query);
			
			/* Adds the Atoms to the GroundConstraintKernel */
			for (int i = 0; i < res.size(); i++) {
				GroundTerm[] terms = new GroundTerm[2];
				terms[pos] = anchor;
				terms[1 - pos] = res.get(i)[0];
				if (!terms[1 - pos].equals(other)) {
					con.addAtom(atomManager.getAtom(predicate, terms));
				}
			}
			gks.addGroundKernel(con);
		}
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework manager) {
		manager.registerAtomEventListener(AtomEvent.ConsideredEventTypeSet, predicate, this);
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework manager) {
		manager.unregisterAtomEventListener(AtomEvent.ConsideredEventTypeSet, predicate, this);
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
