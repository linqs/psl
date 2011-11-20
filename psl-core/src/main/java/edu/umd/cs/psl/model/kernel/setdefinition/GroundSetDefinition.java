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
package edu.umd.cs.psl.model.kernel.setdefinition;

import java.util.*;

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.atom.Atom;

import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.set.membership.TermMembership;
import edu.umd.cs.psl.optimizer.NumericUtilities;
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
	
	private final Atom setAtom;
	
	private final TermMembership set1;
	private final TermMembership set2;
	
	private final Set<Atom> referencedAtoms;
	
	private final int hashcode;
	
	GroundSetDefinition(SetDefinitionKernel s, Atom atom, TermMembership s1, TermMembership s2, Set<Atom> refAtoms) {
		assert s!=null;
		definitionType = s;
		setAtom = atom;
		set1 = s1;
		set2 = s2;
		referencedAtoms = refAtoms;
		setAtom.setSoftValue(0, getAggregateValue());
		
		hashcode = new HashCodeBuilder().append(setAtom).toHashCode();
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
	public Kernel getKernel() {
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
	public Set<Atom> getAtoms() {
		Set<Atom> result = new HashSet<Atom>(referencedAtoms);
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
		return setAtom.equals(p.setAtom);
	}


	@Override
	public BindingMode getBinding(Atom atom) {
		if (referencedAtoms.contains(atom)) {
			if (setAtom.isActive()) 
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
	public double getIncompatibility() {
		assert setAtom.getNumberOfValues()==1;
		if (NumericUtilities.equals(setAtom.getSoftValue(0), getAggregateValue())) {
			return 0.0;
		} else
			return Double.POSITIVE_INFINITY;
	}
	
}
