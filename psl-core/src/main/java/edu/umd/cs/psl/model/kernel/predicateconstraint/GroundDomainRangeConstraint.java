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
package edu.umd.cs.psl.model.kernel.predicateconstraint;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.ConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

/**
 * GroundPredicateConstraint uses a
 * {@link edu.umd.cs.psl.model.kernel.predicateconstraint.DomainRangeConstraintKernel
 * PredicateConstraintKernel} as a template and operates on an
 * {@link edu.umd.cs.psl.model.argument.Entity Entity} involved in the
 * associated {@link edu.umd.cs.psl.model.predicate.Predicate Predicate},
 * providing the ability to ground {@link edu.umd.cs.psl.model.atom.Atom Atoms}
 * involving the {@link edu.umd.cs.psl.model.predicate.Predicate Predicate}.
 * 
 * 
 */
public class GroundDomainRangeConstraint implements GroundConstraintKernel {

	private final DomainRangeConstraintKernel template;
	private final GroundTerm anchor;
	private final double value;

	private final Set<GroundAtom> atoms;

	private final int hashcode;

	public GroundDomainRangeConstraint(DomainRangeConstraintKernel t, GroundTerm a, double v) {
		template = t;
		anchor = a;
		value = v;
		atoms = new HashSet<GroundAtom>();
		hashcode = new HashCodeBuilder().append(template).append(anchor)
				.toHashCode();
	}

	/**
	 * Adds an atom to ground over to the GroundPredicateConstraint
	 * @param atom The atom to ground over
	 * @throws IllegalArgumentException
	 *             if the {@link edu.umd.cs.psl.model.atom.Atom Atom's}
	 *             {@link edu.umd.cs.psl.model.predicate.Predicate predicate}
	 *             does not match the
	 *             {@link edu.umd.cs.psl.model.predicate.Predicate predicate} in
	 *             the {@link DomainRangeConstraintKernel} specified in the
	 *             constructor, or the argument
	 *             {@link edu.umd.cs.psl.model.argument.Term Term} in the
	 *             {@link edu.umd.cs.psl.model.atom.Atom Atom's}
	 *             {@link DomainRangeConstraintType constraint}-specified position
	 *             doesn't match the
	 *             {@link edu.umd.cs.psl.model.argument.Entity Entity} provided
	 *             in the constructor.
	 */
	void addAtom(GroundAtom atom) {
		if (!atom.getPredicate().equals(template.getPredicate()))
			throw new IllegalArgumentException(
					"Added atom has non-matching predicate: " + atom);
		if (!atom.getArguments()[template.getConstraintType().position()]
				.equals(anchor))
			throw new IllegalArgumentException("Atom does not match anchor: "
					+ atom);
		
		if (!atoms.contains(atom)) {
			atoms.add(atom);
			atom.registerGroundKernel(this);
		}
	}

	@Override
	public boolean updateParameters() {
		throw new UnsupportedOperationException(
				"Parameters of evidence type cannot change since there are none!");
	}

	public ConstraintTerm getConstraintDefinition() {
		FunctionSum sum = new FunctionSum();
		for (GroundAtom atom : atoms) {
			sum.add(new FunctionSummand(1.0, atom.getVariable()));
		}
		return new ConstraintTerm(sum, template.getConstraintType()
				.constraint(), value);
	}

	@Override
	public ConstraintKernel getKernel() {
		return template;
	}
	
	@Override
	public BindingMode getBinding(Atom atom) {
		if (atoms.contains(atom))
			return BindingMode.WeakRV;
		else
			return BindingMode.NoBinding;
	}

	@Override
	public double getInfeasibility() {
		double sum = 0.0;
		for (GroundAtom atom : atoms) {
			sum += atom.getValue();
		}
		
		return template.getConstraintType().getInfeasibility(sum);
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		return atoms;
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public boolean equals(Object oth) {
		if (oth == this)
			return true;
		if (oth == null || !(getClass().isInstance(oth)))
			return false;
		GroundDomainRangeConstraint con = (GroundDomainRangeConstraint) oth;
		return template.equals(con.template) && anchor.equals(con.anchor);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(template.getConstraintType().toString()).append(" on ")
				.append("{");
		for (Atom atom : atoms) {
			b.append(atom).append(" , ");
		}
		b.delete(b.length() - 3, b.length());
		return b.append("}").toString();
	}

}
