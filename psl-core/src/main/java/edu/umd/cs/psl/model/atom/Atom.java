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

import java.util.*;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.factorgraph.Factor;
import edu.umd.cs.psl.factorgraph.RandomVariable;
import edu.umd.cs.psl.model.ConfidenceValues;
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
import edu.umd.cs.psl.reasoner.function.FunctionVariable;


/**
 * The abstract FormulaPredicateAtom is the base class for all atoms with a predicate and arguments.
 * It defines most of the standard functionality of predicate atoms with some of it implemented in its
 * two child classes.
 * 
 * FormulaAtom must be constructed through the static create() functions to ensure a unique memory
 * representation.
 * 
 * @author Matthias Broecheler
 *
 */
public interface Atom extends Formula, RandomVariable {
	
	
	public Predicate getPredicate();
	
	public int getNumberOfValues();
	
	public int getArity();
	
	public void setSoftValues(double[] val);

	public void setSoftValue(int pos, double val);
	
	public void setConfidenceValues(double[] val);
	
	public void setConfidenceValue(int pos, double val);
	
	public boolean hasNonDefaultValues();
	
	public double getSoftValue(int pos);
	
	public double[] getSoftValues();
	
	public double getConfidenceValue(int pos);

	public double[] getConfidenceValues();
	
	public boolean registerGroundKernel(GroundKernel f);
	
	public boolean deregisterGroundKernel(GroundKernel f);
	
	public Set<GroundKernel> getRegisteredGroundKernels(Kernel et);
	
	public Collection<GroundKernel> getAllRegisteredGroundKernels();
	
	public int getNumRegisteredGroundKernels();
	
	
	public Term[] getArguments();
	
	public boolean isGround();
	
	public AtomStatus getStatus();
	
	public boolean isFactAtom();
	
	public boolean isInferenceAtom();
	
	public boolean isKnownAtom();
	
	public boolean isCertainty();
	
	public boolean isRandomVariable();
	
	public boolean isActive();
	
	public boolean isConsidered();
	
	public boolean isUnconsidered();
	
	public boolean isDefined();
	
	public boolean isConsideredOrActive();
	

	void delete();

	void consider();
	
	void unconsider();
	
	void activate();
	
	void deactivate();
	
	void makeCertain();
	
	void revokeCertain();

	
	public String getName();
	
	
	
	public boolean isAtomGroup();

	public Collection<Atom> getAtomsInGroup(AtomManager atommanager, DatabaseAtomStoreQuery db);
	

	/*
	 * ###### FunctionVariable Interface ##########
	 */
	
	public AtomFunctionVariable getVariable();
	
	public AtomFunctionVariable getVariable(int position);	


	
}
