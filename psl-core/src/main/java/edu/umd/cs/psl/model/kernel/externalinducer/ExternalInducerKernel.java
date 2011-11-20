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
package edu.umd.cs.psl.model.kernel.externalinducer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.database.ResultListValues;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.function.BulkExternalFunction;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.model.function.LearnableExternalFunction;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.model.predicate.ExternalFunctionPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.predicate.type.PredicateTypes;

public class ExternalInducerKernel implements Kernel {

	private final Model model;
	private PositiveWeight weight;
	
	private final VariableTypeMap vartypes;
	private final Conjunction body;
	private final Atom head;
	private Atom externalAtom;
	private final Set<Variable> externalAtomVariables;
	private ExternalFunction extFun;
	private final Map<Variable,List<Atom>> retrievals;
	
	private final int hashcode;

	public ExternalInducerKernel(Model m, Conjunction c, Atom head, double w) {
		model=m;
		weight = new PositiveWeight(w);

		Preconditions.checkArgument(head.getPredicate() instanceof StandardPredicate);
		Preconditions.checkArgument(head.getPredicate().getNumberOfValues()==1);
		this.head = head;
		retrievals = new HashMap<Variable,List<Atom>>();
		vartypes = new VariableTypeMap();
		for (int i=0;i<head.getArity();i++) {
			Term t = head.getArguments()[i];
			if (t instanceof Variable) {
				retrievals.put((Variable)t, new ArrayList<Atom>()); 
				vartypes.put((Variable)t, head.getPredicate().getArgumentType(i));
			}
			
		}
		
		externalAtom = null;
		Set<Variable> associatedVariables = new HashSet<Variable>();

		body = c;
		//Preconditions.checkArgument(FormulaUtil.isConjunction(c));
		for (Atom atom : c.getAtoms(new HashSet<Atom>())) {
			if (atom.getPredicate() instanceof ExternalFunctionPredicate) {
				if (externalAtom!=null) throw new IllegalArgumentException("Only one external function is allowed!");
				externalAtom = atom;
				extFun = ((ExternalFunctionPredicate)atom.getPredicate()).getExternalFunction();
			} else {
				assert atom.getPredicate() instanceof StandardPredicate;
				boolean associated=false;
				for (Term t : atom.getArguments()) {
					if ((t instanceof Variable) && retrievals.containsKey(t)) {
						if (associated) throw new IllegalArgumentException("One atom cannot be associated with multiple variables: " + atom);
						associated=true;
						retrievals.get(t).add(atom);
					} else if (t instanceof Variable) associatedVariables.add((Variable)t);
				}
				if (!associated) throw new IllegalArgumentException("Atom could not be associated: " + atom);
			}
		}
		externalAtomVariables = new HashSet<Variable>();
		for (Term t : externalAtom.getArguments()) {
			if ((t instanceof Variable)) {
				Variable v = (Variable)t;
				externalAtomVariables.add(v);
				if (!associatedVariables.contains(v) && !retrievals.containsKey(v))
					throw new IllegalArgumentException("Variable in external function could not be associated: " + v);
			}
		}
		
		hashcode = new HashCodeBuilder().append(body).append(head).toHashCode();
	}
	
	@Override
	public Kernel clone() {
		return new ExternalInducerKernel(model,body,head,weight.getWeight());
	}

	
	public Weight getWeight() {
		return weight;
	}
	
	@Override
	public Parameters getParameters() {
		return weight.duplicate();
	}
	

	@Override
	public void setParameters(Parameters para) {
		if (!(para instanceof Weight)) throw new IllegalArgumentException("Expected weight parameter!");
		PositiveWeight newweight = (PositiveWeight)para;
		if (!newweight.equals(weight)) {
			weight = newweight;
			model.notifyKernelParametersModified(this);
		}
	}
	
	@Override
	public boolean isCompatibilityKernel() {
		return true;
	}
	
	private Set<Predicate> getRetrievalPredicates() {
		Set<Predicate> predicates = new HashSet<Predicate>();
		for (List<Atom> atoms : retrievals.values()) {
			for (Atom atom : atoms) {
				predicates.add(atom.getPredicate());
			}
		}
		return predicates;
	}
	
	private void validatePredicates(DatabaseAtomStoreQuery db) {
		for (Predicate p : getRetrievalPredicates()) {
				Preconditions.checkArgument(db.isClosed(p),p);
				Preconditions.checkArgument(p.getType()==PredicateTypes.BooleanTruth,p);
		}
	}
	
	public void learn(DatabaseAtomStoreQuery db) {
		if (!(extFun instanceof LearnableExternalFunction))
			throw new IllegalStateException("The provided external function is not learnable!");
		LearnableExternalFunction learnFun = (LearnableExternalFunction)extFun;
		getDataAndExecute(db,Execution.Learn,null);
	}
	
	private static enum Execution { Bulk, Learn }
	
	@SuppressWarnings("unchecked")
	private void getDataAndExecute(DatabaseAtomStoreQuery db, Execution exe, ModelApplication app) {
		Map<GroundTerm,GroundTerm[]>[] datas = (Map<GroundTerm,GroundTerm[]>[])new Object[retrievals.size()];
		Map<Variable,int[]> argMap = new HashMap<Variable,int[]>();
		
		Map<Variable,Integer> varkeymap = new HashMap<Variable,Integer>();
		List<Variable> keyvarmap = new ArrayList<Variable>();
		for (Variable v : retrievals.keySet()) { 
			keyvarmap.add(v);
			varkeymap.put(v, varkeymap.size());
		}
		for (Map.Entry<Variable, Integer> varpos : varkeymap.entrySet()) {
			int position = varpos.getValue();
			Variable var = varpos.getKey();
			List<Atom> atoms = retrievals.get(var);
			datas[position] = new HashMap<GroundTerm,GroundTerm[]>();
			assert atoms!=null;
			if (atoms.isEmpty()) {
				assert externalAtomVariables.contains(var);
				Set<Entity> entities = db.getEntities(vartypes.get(var));
				for (Entity e : entities) {
					datas[position].put(e, new GroundTerm[]{e});
				}
				argMap.put(var, new int[]{position,0});
			} else {
				Conjunction c = new Conjunction(atoms.toArray(new Atom[atoms.size()]));
				List<Variable> projectTo = new ArrayList<Variable>();
				projectTo.add(var);
				for (Atom a : atoms) for (Term t : a.getArguments())
					if (externalAtomVariables.contains(t) && !projectTo.contains(t)) projectTo.add((Variable)t);
				assert !projectTo.isEmpty();
				ResultList results = db.query(c, projectTo);
				for (int k=0;k<results.size();k++) {
					GroundTerm[] result = results.get(k);
					if (externalAtomVariables.contains(var)) datas[position].put(result[0], result);
					else datas[position].put(result[0], Arrays.copyOfRange(result, 1, result.length));
				}
				int start = externalAtomVariables.contains(var)?0:1;
				for (int i=start;i<projectTo.size();i++) {
					argMap.put(projectTo.get(i), new int[]{position,i-start});
				}
			}
		}
		
		GroundTerm[] args = new GroundTerm[head.getArity()];
		for (int i=0;i<head.getArity();i++) {
			if (!(head.getArguments()[i] instanceof Variable)) args[i]=(GroundTerm)head.getArguments()[i];
		}
		Predicate p = head.getPredicate();
		
		if (exe==Execution.Bulk) {
			AtomManager amanager = app.getAtomManager();
			Map<GroundTerm[],double[]> results = ((BulkExternalFunction)extFun).bulkCompute(argMap, datas);
			for (Map.Entry<GroundTerm[], double[]> entry : results.entrySet()) {
				GroundTerm[] result = entry.getKey();
				GroundTerm[] arguments = args.clone();
				//Fill in remaining args
				for (int i=0;i<head.getArity();i++) {
					if ((head.getArguments()[i] instanceof Variable)) 
						arguments[i]=result[varkeymap.get((Variable)head.getArguments()[i])];
				}
				GroundExternalInducer ei = new GroundExternalInducer(this,amanager.getAtom(p, arguments),entry.getValue());
				app.addGroundKernel(ei);
			}
		} else if (exe==Execution.Learn) {
			GroundTerm[] query = new GroundTerm[head.getArity()];
			for (int i=0;i<head.getArity();i++) {
				if (head.getArguments()[i] instanceof GroundTerm) query[i]=(GroundTerm)head.getArguments()[i];
			}
			ResultListValues results = db.getFacts(head.getPredicate(),query);

			Map<GroundTerm[],double[]> truth = new HashMap<GroundTerm[],double[]>();

			for (int k=0;k<results.size();k++) {
				truth.put(results.get(k), results.getValues(k));
			}
			((LearnableExternalFunction)extFun).learn(truth, argMap, datas);
		} else throw new UnsupportedOperationException(exe.toString());
	}
	
	@Override
	public void groundAll(ModelApplication app) {
		if (!(extFun instanceof BulkExternalFunction))
			throw new IllegalStateException("The provided external function is not learnable!");
		getDataAndExecute(app.getDatabase(),Execution.Learn,app);
	}
	


	@Override
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode, ModelApplication app) {
		if (AtomEventSets.NonDefaultFactEvent.subsumes(event)) {
			throw new UnsupportedOperationException("Changes to facts not yet supported!");
		} else if (AtomEventSets.ActivationEvent.subsumes(event)) {
			assert atom.getPredicate().equals(head.getPredicate());
			if (mode==GroundingMode.Backward) {
				//TODO: implement backward grounding of such rules
				throw new UnsupportedOperationException("Not yet implemented!");
			} //else do nothing
		} else throw new UnsupportedOperationException("Unsupported event encountered: " + event);
	}
	
	@Override
	public void registerForAtomEvents(AtomEventFramework framework, DatabaseAtomStoreQuery db) {
		validatePredicates(db);
		framework.registerAtomEventObserver(head.getPredicate(), AtomEventSets.DeOrActivationEvent, this);
		for (Predicate p : getRetrievalPredicates()) {
			framework.registerAtomEventObserver(p, AtomEventSets.NonDefaultFactEvent, this);
		}
	}
	
	@Override
	public void unregisterForAtomEvents(AtomEventFramework framework, DatabaseAtomStoreQuery db) {
		framework.unregisterAtomEventObserver(head.getPredicate(), AtomEventSets.DeOrActivationEvent, this);
		for (Predicate p : getRetrievalPredicates()) {
			framework.unregisterAtomEventObserver(p, AtomEventSets.NonDefaultFactEvent, this);
		}
	}
	
	@Override
	public String toString() {
		return "{" + weight.toString() + "} External Function: " + body.toString() + " => " + head.toString();
	}

	@Override
	public int hashCode() {
		return hashcode;
	}
}
