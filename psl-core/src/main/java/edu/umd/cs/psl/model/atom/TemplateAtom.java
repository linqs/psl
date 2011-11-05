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

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.VariableTypeMap;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

public class TemplateAtom implements Atom {

	private final Predicate predicate;
	
	private final Term[] arguments;
	
	private final int hashcode;

	public TemplateAtom(Predicate p, Term[] args) {
		predicate = p;
		arguments = args;
		hashcode = new HashCodeBuilder().append(predicate).append(arguments).toHashCode();
		checkSchema();
	}
	
	private void checkSchema() {
		if (predicate.getArity()!=arguments.length) {
			throw new IllegalArgumentException("Length of Schema does not match the number of arguments.");
		}
		for (int i=0;i<arguments.length;i++) {
			if (arguments[i]==null) throw new IllegalArgumentException("Arguments must not be null!");
			if ((arguments[i] instanceof GroundTerm) && !((GroundTerm)arguments[i]).getType().isSubTypeOf(predicate.getArgumentType(i)))
				throw new IllegalArgumentException("Expected type "+predicate.getArgumentType(i)+" at position "+i+" but was given: " + arguments[i] + " for predicate " + predicate);
		}
	}
	
	@Override
	public Formula dnf() {
		return this;
	}
	
	@Override
	public Predicate getPredicate() {
		return predicate;
	}
	
	@Override
	public int getNumberOfValues() {
		return predicate.getNumberOfValues();
	}
	
	@Override
	public int getArity() {
		return predicate.getArity();
	}
	
	@Override
	public VariableTypeMap getVariables(VariableTypeMap varMap) {
		for (int i=0;i<arguments.length;i++) {
			if (arguments[i] instanceof Variable) {
				ArgumentType t = predicate.getArgumentType(i);
				varMap.addVariable((Variable)arguments[i], t);
			}
		}
		return varMap;
	}
	
	@Override
	public Term[] getArguments() {
		return arguments;
	}
	
	@Override
	public boolean isGround() {
		for (Term arg : arguments) {
			if (!arg.isGround()) return false;
		}
		return true;
	}
	
	@Override
	public Collection<Atom> getAtoms(Collection<Atom> atoms) {
		atoms.add(this);
		return atoms;
	}
	
	@Override
	public String getName() {
		StringBuilder s = new StringBuilder();
		s.append(predicate.getName()).append("(");
		String connector = "";
		for (Term arg : arguments) {
			s.append(connector).append(arg);
			connector = ", ";
		}
		s.append(")");
		return s.toString();
	}
	
	
	@Override 
	public String toString() {
		return getName();
	}
	
	@Override
	public int hashCode() {
		return hashcode;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		TemplateAtom other = (TemplateAtom)oth;
		return predicate.equals(other.predicate) && Arrays.deepEquals(arguments, other.arguments);  
	}
	

	@Override
	public AtomStatus getStatus() {
		return AtomStatus.Template;
	}

	@Override
	public boolean isDefined() {
		return false;
	}
	

	@Override
	public Collection<Atom> getAtomsInGroup(AtomManager atommanager, DatabaseAtomStoreQuery db) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAtomGroup() {
		return false;
	}
	
	//########## Unsupported Operations ##############

	@Override
	public void activate() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void makeCertain() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean deregisterGroundKernel(GroundKernel f) {
		throw new UnsupportedOperationException();	}

	@Override
	public Collection<GroundKernel> getAllRegisteredGroundKernels() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getConfidenceValue(int pos) {
		throw new UnsupportedOperationException();
	}

	@Override
	public double[] getConfidenceValues() {
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
	public double getSoftValue(int pos) {
		throw new UnsupportedOperationException();
	}

	@Override
	public double[] getSoftValues() {
		throw new UnsupportedOperationException();
	}

	@Override
	public AtomFunctionVariable getVariable() {
		throw new UnsupportedOperationException();
	}

	@Override
	public AtomFunctionVariable getVariable(int position) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasNonDefaultValues() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isActive() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isCertainty() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isConsidered() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean isConsideredOrActive() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isFactAtom() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean isKnownAtom() {
		throw new UnsupportedOperationException();
	}
		
	@Override
	public boolean isInferenceAtom() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isRandomVariable() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isUnconsidered() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean registerGroundKernel(GroundKernel f) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setConfidenceValue(int pos, double val) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setConfidenceValues(double[] val) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setSoftValue(int pos, double val) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setSoftValues(double[] val) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void consider() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deactivate() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void delete() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void revokeCertain() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void unconsider() {
		throw new UnsupportedOperationException();
	}

	
}
