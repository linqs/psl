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
package edu.umd.cs.psl.model.kernel.setdefinition;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.ConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.set.membership.TermMembership;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;


/**
 * A fuzzy formula atom is a particular type of formula atom that allows its truth values to be in the
 * range from 0 to 1.
 * 
 * Fuzzy atoms have a fuzzy predicate which implements how the truth value is computed. Often, fuzzy predicates
 * depend on the truth value of other (boolean) atoms.
 * 
 * @author Matthias Broecheler
 *
 */
public class GroundSetDefinition implements GroundConstraintKernel {

	private final SetDefinitionKernel definitionType;
	
	private final GroundAtom setAtom;
	
	private final TermMembership set1;
	private final TermMembership set2;
	
	private final Set<GroundAtom> referencedAtoms;
	
	private final int hashcode;
	
	GroundSetDefinition(SetDefinitionKernel s, GroundAtom atom, TermMembership s1, TermMembership s2, Set<GroundAtom> compAtoms) {
		assert s!=null;
		definitionType = s;
		setAtom = atom;
		set1 = s1;
		set2 = s2;
		referencedAtoms = compAtoms;
		
		if (atom instanceof RandomVariableAtom)
			((RandomVariableAtom) setAtom).setValue(getAggregateValue());
		
		hashcode = new HashCodeBuilder().append(definitionType).append(setAtom).toHashCode();
		
		/* Must register after all the members (like the hashcode!) are set */
		setAtom.registerGroundKernel(this);
		for (GroundAtom refAtom : referencedAtoms)
			refAtom.registerGroundKernel(this);
	}
	
	
//	public boolean add2Set(int pos, GroundTerm t, double degree) {
//		if (pos<0 || pos>1) throw new IllegalArgumentException("Invalid position: " + pos);
//		Membership<GroundTerm> set = pos==0?set1:set2;
//		return set.addMember(t, degree);
//	}
//	
//	public Set<Atom> getReferencedAtoms() {
//		return referencedAtoms;
//	}
//
//	public boolean addReferencedAtom(Atom ref) {
//		return referencedAtoms.add(ref);
//	}

//	public double getSizeMultiplier() {
//		return ((SetEntityDefinitionType)definitionType).getAggregator().getSizeMultiplier(set1, set2);
//	}
	
	public Atom getSetAtom() {
		return setAtom;
	}
	
	@Override
	public ConstraintKernel getKernel() {
		return definitionType;
	}

	@Override
	public boolean updateParameters() {
		throw new UnsupportedOperationException("Parameters of evidence type cannot change since there are none!");
	}
	

	@Override
	public ConstraintTerm getConstraintDefinition() {
		return definitionType.getAggregator().defineConstraint(setAtom, set1, set2, referencedAtoms);
	}
	
	@Override
	public Set<GroundAtom> getAtoms() {
		Set<GroundAtom> result = new HashSet<GroundAtom>(referencedAtoms);
		result.add(setAtom);
		return result;
	}

	
	@Override
	public String toString() {
		StringBuilder b =new StringBuilder();
		b.append(setAtom).append("=").append("{");
		for (Atom atom : referencedAtoms) {
			b.append(atom).append(" , ");
		}
		b.delete(b.length()-3, b.length());
		b.append("}");
		b.append(" defined by ").append(definitionType.getAggregator());
		return b.toString();
	}
	
	@Override
	public int hashCode() {
		return hashcode;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		GroundSetDefinition p = (GroundSetDefinition)oth;
		return definitionType.equals(p.definitionType) && setAtom.equals(p.setAtom);
	}


	@Override
	public BindingMode getBinding(Atom atom) {
		if (referencedAtoms.contains(atom)) {
			if (((GroundAtom) atom).getValue() > 0.0)
				return BindingMode.StrongRV;
			else return BindingMode.WeakRV;
		} else if (setAtom.equals(atom)) {
			if (definitionType.getAggregator().enoughSupport(set1, set2, referencedAtoms))
				return BindingMode.StrongRV;
			else return BindingMode.WeakRV;
		} else return BindingMode.NoBinding;
	}

	public double getAggregateValue() {
		return definitionType.getAggregator().aggregateValue(set1, set2, referencedAtoms);
	}

	@Override
	public double getInfeasibility() {
		return Math.abs(setAtom.getValue() - getAggregateValue());
	}
	
}
