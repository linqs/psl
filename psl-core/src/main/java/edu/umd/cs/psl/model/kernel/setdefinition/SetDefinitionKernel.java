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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.argument.*;
import edu.umd.cs.psl.model.argument.type.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.atom.TemplateAtom;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.traversal.FormulaEventAnalysis;
import edu.umd.cs.psl.model.formula.traversal.FormulaGrounder;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.kernel.rule.AbstractGroundRule;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.predicate.SpecialPredicates;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.set.aggregator.EntityAggregatorFunction;
import edu.umd.cs.psl.model.set.membership.SoftTermMembership;
import edu.umd.cs.psl.model.set.membership.TermMembership;
import edu.umd.cs.psl.model.set.term.BasicSetTerm;
import edu.umd.cs.psl.model.set.term.SetTerm;
import edu.umd.cs.psl.ui.aggregators.AggregateConstantSetOverlap;
import edu.umd.cs.psl.ui.aggregators.AggregateSetAverage;
import edu.umd.cs.psl.ui.aggregators.AggregateSetEquality;
import edu.umd.cs.psl.ui.aggregators.AggregateSetOverlap;
import edu.umd.cs.psl.util.dynamicclass.DynamicClassLoader;

/**
 * This class implements an abstract fuzzy predicate which is extended by particular fuzzy predicate
 * classes. This class provides some standard functionality for fuzzy predicates.
 * 
 * To construct a fuzzy predicate use the static create() methods in Predicate.java
 * 
 * @author Matthias Broecheler
 *
 */
public class SetDefinitionKernel implements Kernel {

	private static final Logger log = LoggerFactory.getLogger(SetDefinitionKernel.class);


	
	final SetTerm set1,set2;
	final Variable[] argumentVariableMap;
	final Predicate comparisonPredicate;
	final EntityAggregatorFunction setCompareFct;
	final StandardPredicate setPredicate;
	
	final Map<Variable,Integer> variablePosition;
	
	private final List<FormulaEventAnalysis> triggerFormulas;
	private final List<Variable> projection;
	
	private final List<Set<BasicSetTerm>> sets;
	private final boolean isSoftSet;
	
	private final int hashcode;
	

	
	
	@SuppressWarnings("unchecked")
	public SetDefinitionKernel(StandardPredicate setP, SetTerm s1, SetTerm s2, Variable[] variables, Predicate compareP, EntityAggregatorFunction compare, boolean soft) {
		if (!(compareP instanceof StandardPredicate) && !(compareP instanceof SpecialPredicates)) throw new IllegalArgumentException("Expected basic predicate for comparison!");
		Preconditions.checkArgument(setP.getNumberOfValues()==1);
		Preconditions.checkArgument(compareP.getNumberOfValues()==1);
		
		set1 = s1;
		set2 = s2;
		argumentVariableMap = variables;
		comparisonPredicate = compareP;
		setPredicate = setP;
		setCompareFct = compare;
		
		isSoftSet = soft;
		projection = Arrays.asList(argumentVariableMap);
		sets = Lists.newArrayList(set1.getBasicTerms(),set2.getBasicTerms());
		triggerFormulas = new ArrayList<FormulaEventAnalysis>(sets.get(0).size()*sets.get(1).size());

		
		//Verify schema
		if (setPredicate.getArity()!=variables.length) throw new IllegalArgumentException("Number of variables does not match predicate arity");
		VariableTypeMap freeVars = s1.getAnchorVariables(new VariableTypeMap());
		s2.getAnchorVariables(freeVars);
		
		for (int i=0;i<variables.length;i++) {
			if (!freeVars.hasVariable(variables[i])) throw new IllegalArgumentException("Variable does not occur in either set term: " + variables[i]);
			if (!freeVars.getType(variables[i]).equals(setPredicate.getArgumentType(i))) 
				throw new IllegalArgumentException("Variable does not have matching predicate argument type: " + variables[i]);
		}


		for (BasicSetTerm setterm1 : sets.get(0)) {
			for (BasicSetTerm setterm2 : sets.get(1)) {
				assert !(setterm1.getLeaf() instanceof Variable) || !(setterm2.getLeaf() instanceof Variable) || !setterm1.getLeaf().equals(setterm2.getLeaf());
				Atom connect = new TemplateAtom(comparisonPredicate, new Term[]{setterm1.getLeaf(),setterm2.getLeaf()});
				Formula trigger = connect;
				Formula[] setformulas = new Formula[]{setterm1.getFormula(),setterm2.getFormula()};
				for (int i=0;i<setformulas.length;i++) {
					if (setformulas[i]!=null) {
						trigger  = new Conjunction(setformulas[i],trigger);
					}
				}
				triggerFormulas.add(new FormulaEventAnalysis(trigger));
			}
		}
		
		hashcode = new HashCodeBuilder().append(setPredicate).toHashCode();
		
		variablePosition = new HashMap<Variable,Integer>(argumentVariableMap.length);
		for (int i=0;i<argumentVariableMap.length;i++) {
			variablePosition.put(argumentVariableMap[i], Integer.valueOf(i));
		}
	}

	
	public SetDefinitionKernel(StandardPredicate setP, SetTerm s1, SetTerm s2, Variable[] variables, Predicate compareP, String compare, boolean soft) {
		this(setP, s1,s2,variables,compareP,parseDefinition(compare),soft);
	}
	
	public SetDefinitionKernel(StandardPredicate setP, SetTerm s1, SetTerm s2, Variable[] variables, Predicate compareP, String compare) {
		this(setP, s1,s2,variables,compareP,parseDefinition(compare));
	}
	
	public SetDefinitionKernel(StandardPredicate setP, SetTerm s1, SetTerm s2, Variable[] variables, Predicate compareP, EntityAggregatorFunction compare) {
		this(setP, s1,s2,variables,compareP,compare,false);
	}
	
	public EntityAggregatorFunction getAggregator() {
		return setCompareFct;
	}

	@Override
	public Kernel clone() {
		return new SetDefinitionKernel(setPredicate,set1,set2,argumentVariableMap,comparisonPredicate, setCompareFct,isSoftSet);
	}
	
	public String getName() {
		StringBuilder s = new StringBuilder();
		s.append("{").append(set1).append("} ");
		s.append(setCompareFct.getName()).append("(").append(comparisonPredicate).append(")");
		s.append(" {").append(set2).append("}");
		s.append(" =: ").append(setPredicate);
		s.append(isSoftSet?"[soft]":"[]");
		return s.toString();
	}

	
	@Override
	public boolean isCompatibilityKernel() {
		return false;
	}
	
	@Override
	public Parameters getParameters() {
		return Parameters.NoParameters;
	}
	
	@Override
	public void setParameters(Parameters para) {
		throw new UnsupportedOperationException("Aggregate Predicates have no parameters!");
	}
	
	
	@Override
	public String toString() {
		return getName();
	}
	
	@Override
	public void groundAll(ModelApplication app) {
		for (int k=0;k<triggerFormulas.size();k++) {
			ResultList res = app.getDatabase().query(triggerFormulas.get(k).getFormula(), projection);
			log.debug("Grounding size {} for formula {}",res.size(),triggerFormulas.get(k).getFormula());
			for (int i=0;i<res.size();i++) {
				newSetDefinition(app,res.get(i),true);
			}
		}
		//TODO: Right now, we assume that we have no initial knowledge about setPredicate, i.e.
		//no ground fact instances of setPredicate are in the database
	}

	@Override
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode, ModelApplication app) {
		
		if (AtomEventSets.ActivationEvent.subsumes(event)) {
			if (atom.getPredicate().equals(setPredicate)) {
				if (atom.getRegisteredGroundKernels(this).isEmpty()) {
					newSetDefinition(app,(GroundTerm[])atom.getArguments(),true);
				} //Otherwise, setdefinition already exists
			} else if (atom.getPredicate().equals(comparisonPredicate)) {
				int numTriggered = 0;
				for (FormulaEventAnalysis analysis : triggerFormulas) {
					List<VariableAssignment> vars = analysis.traceAtomEvent(atom);
					if (vars.isEmpty()) continue;
					
					numTriggered+=vars.size();
					
					//Current restriction
					if (vars.size()>1)
						throw new UnsupportedOperationException("Second order ativation is not yet supported!");
					
					for (VariableAssignment var : vars) {
						log.trace("{}",analysis.getFormula());
						ResultList res = app.getDatabase().query(analysis.getFormula(), var, projection);
						for (int i=0;i<res.size();i++) {
							newSetDefinition(app,res.get(i),false);
						}
					}
				}
				if (numTriggered==0) throw new IllegalArgumentException("No event is actually triggered!");
			} else throw new UnsupportedOperationException("Currently, the set membership formulas must be fact based only!");
		} else {
			throw new UnsupportedOperationException("Currently, only activation events are supporte: " + event);
		}
	}
	
	private void newSetDefinition(ModelApplication app, GroundTerm[] args, boolean forceCreation) {
		Atom setAtom = app.getDatabase().getConsideredAtom(setPredicate, args);
		//If the definition already exists, then we can directly return
		if (setAtom!=null && !setAtom.getRegisteredGroundKernels(this).isEmpty()) return;
		
		VariableAssignment ass = new VariableAssignment();
		assert args.length == argumentVariableMap.length;
		for (int i=0;i<args.length;i++) {
			ass.assign(argumentVariableMap[i], args[i]);
		}
		
		//Construct membership
		SoftTermMembership[] members = new SoftTermMembership[2];
		for (int i=0;i<2;i++) {
			members[i]=new SoftTermMembership();
			for (BasicSetTerm setterm : sets.get(i)) {
				if (setterm.getFormula()==null) {
					Term leaf = setterm.getLeaf();
					if (leaf instanceof GroundTerm) {
						members[i].addMember((GroundTerm)leaf, 1.0);
					} else {
						assert leaf instanceof Variable;
						members[i].addMember(ass.getVariable((Variable)leaf),1.0);
					}
				} else {
					assert setterm.getLeaf() instanceof Variable;
					if (isSoftSet) {
						ResultList res = app.getDatabase().query(setterm.getFormula(), ass);
						FormulaGrounder grounder = new FormulaGrounder(app.getAtomManager(), res, ass);
						while (grounder.hasNext()) {
							Formula f = grounder.ground(setterm.getFormula());
							double truth = AbstractGroundRule.formulaNorm.getTruthValue(f);
							//if (truth<=0.0) log.debug("Untrue formula: {}",f);
							members[i].addMember(grounder.getResultVariable((Variable)setterm.getLeaf()), truth);
							grounder.next();
						}
					} else {
						ResultList res = app.getDatabase().query(setterm.getFormula(), ass, ImmutableList.of((Variable)setterm.getLeaf()));
						for (int j=0;j<res.size();j++)
							members[i].addMember(res.get(j)[0], 1.0);
					}
				}
			}
			
		}
		if (forceCreation || enoughSupport(app,members[0],members[1])) {
			setAtom = app.getAtomManager().getAtom(setPredicate, args);	
			//log.debug("New set definition: {}",Arrays.toString(args));
			Set<Atom> compAtoms = new HashSet<Atom>();
			boolean isEmpty = true;
			for (GroundTerm s1 : members[0]) {
				for (GroundTerm s2 : members[1]) {
					Atom atom = app.getAtomManager().getAtom(comparisonPredicate, new GroundTerm[]{s1,s2});
					//log.debug("Added atom: {}",atom);
					compAtoms.add(atom);
					isEmpty = false; 
				}
			}
		
			
			if (isEmpty) {
				double truthval = setCompareFct.aggregateValue(members[0], members[1], compAtoms);
				GroundEmptySetDefinition edef = new GroundEmptySetDefinition(this,setAtom,truthval);
				app.getAtomManager().changeCertainty(setAtom,new double[]{truthval},new double[]{ConfidenceValues.maxConfidence});
				app.addGroundKernel(edef);
			} else {
				GroundSetDefinition sdef = new GroundSetDefinition(this,setAtom,members[0],members[1],compAtoms);
				app.addGroundKernel(sdef);
			}

		}
		
	}
	
	private boolean enoughSupport(ModelApplication app, TermMembership set1, TermMembership set2) {
		Set<Atom> compAtoms = new HashSet<Atom>();
		for (GroundTerm s1 : set1) {
			for (GroundTerm s2 : set2) {
				Atom atom = app.getDatabase().getConsideredAtom(comparisonPredicate, new GroundTerm[]{s1,s2});
				if (atom!=null) compAtoms.add(atom);
			}
		}
		return setCompareFct.enoughSupport(set1, set2, compAtoms);
	}
	

	
	
	@Override
	public void registerForAtomEvents(AtomEventFramework framework, DatabaseAtomStoreQuery db) {
		for (FormulaEventAnalysis analysis : triggerFormulas) {
			analysis.registerFormulaForEvents(framework, this, AtomEventSets.DeOrActivationEvent, db);
		}
		framework.registerAtomEventObserver(setPredicate, AtomEventSets.DeOrActivationEvent, this);

	}
	
	@Override
	public void unregisterForAtomEvents(AtomEventFramework framework, DatabaseAtomStoreQuery db) {
		for (FormulaEventAnalysis analysis : triggerFormulas) {
			analysis.unregisterFormulaForEvents(framework, this, AtomEventSets.DeOrActivationEvent, db);
		}
		framework.unregisterAtomEventObserver(setPredicate, AtomEventSets.DeOrActivationEvent, this);
	}

	
	
	@Override
	public int hashCode() {
		return hashcode;
	}


	
	//===================== STATIC =====================

//	static final AggregatePredicate create(SetTerm s1, SetTerm s2, AggregatorFunction compare, SimplePredicate p) {
//		VariableTypeMap freeVars = s1.getAnchorVariables(new VariableTypeMap());
//		s2.getAnchorVariables(freeVars);
//		
//		Variable[] vars = new Variable[freeVars.size()];
//		ArgumentType[] varTypes = new ArgumentType[vars.length];
//		int i=0;
//		for (Map.Entry<Variable, ArgumentType> entry : freeVars.entrySet()) {
//			vars[i]=entry.getKey();
//			varTypes[i]=entry.getValue();
//			i++;
//		}
//		if (compare instanceof AttributeAggregatorFunction) {
//			return new AggregateAttributePredicate(s1,s2,vars,varTypes,(AttributeAggregatorFunction)compare,p);
//		} else {
//			return new AggregateEntityPredicate(s1,s2,vars,varTypes,(EntityAggregatorFunction)compare,p);
//		}
//		
//	}
	
	static final Map<String,Class<? extends EntityAggregatorFunction>> definedSetComparatorFun = 
		new ImmutableMap.Builder<String,Class<? extends EntityAggregatorFunction>>()
			.put("setequality", AggregateSetEquality.class)
			.put("setaverage", AggregateSetAverage.class)
			.put("setoverlap",AggregateSetOverlap.class)
			.put("setconstant", AggregateConstantSetOverlap.class)
			.build();
	
	static final EntityAggregatorFunction parseDefinition(String definition) {
		try {
			return DynamicClassLoader.loadClassArbitraryArgs(definition, definedSetComparatorFun, EntityAggregatorFunction.class);
		} catch (Throwable e) {
			e.printStackTrace();
			throw new AssertionError("Unknown similarity function: " + definition);
		}
		
	}



	
	
	

}
