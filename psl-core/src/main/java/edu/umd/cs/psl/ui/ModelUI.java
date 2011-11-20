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
package edu.umd.cs.psl.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.application.FullConfidenceAnalysis;
import edu.umd.cs.psl.application.FullInference;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.inference.MaintainedMemoryFullInference;
import edu.umd.cs.psl.application.inference.MemoryFullConfidenceAnalysis;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.config.PSLCoreConfiguration;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.PredicateDBType;
import edu.umd.cs.psl.evaluation.result.FullConfidenceAnalysisResult;
import edu.umd.cs.psl.evaluation.result.FullInferenceResult;
import edu.umd.cs.psl.evaluation.resultui.UIFullInferenceResult;
import edu.umd.cs.psl.evaluation.resultui.UIFullConfidenceAnalysisResult;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.function.AttributeSimilarityFunction;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.type.PredicateType;
import edu.umd.cs.psl.model.predicate.type.PredicateTypes;

public class ModelUI {

	public static final double defaultActivationParameter = 0.1;
	
	private final Model model;
	private final PredicateFactory predicateFactory;
	protected final Map<String,PredicateInfo> predicates;
	protected final Map<String,PredicateInfo> aggregatePredicate;
	protected final Map<String,PredicateInfo> simFunctions;
	private double activationParameter;
	
	public ModelUI() {
		activationParameter = defaultActivationParameter;
		model=new Model();
		predicateFactory = new PredicateFactory();
		predicates = new HashMap<String,PredicateInfo>();
		aggregatePredicate = new HashMap<String,PredicateInfo>();
		simFunctions = new HashMap<String,PredicateInfo>();
	}
	
	public Model getModel() {
		return model;
	}
	
	public void setDefaultActivationParameter(double para) {
		Preconditions.checkArgument(para>=0.0 && para<=1.0);
		activationParameter=para;
	}
	
	public StandardPredicate addBasicPredicate(String name, PredicateType type, ArgumentType[] types, List<String> argNames, boolean open) {
		if (types.length!=argNames.size()) throw new IllegalArgumentException("Expected equal number of argument types and names!");
		double[] activeParas = new double[]{activationParameter};
		if (type==PredicateTypes.BooleanTruth) {
			activeParas = new double[0];
		} else if (type==PredicateTypes.Gaussian) {
			throw new UnsupportedOperationException();
		}
		StandardPredicate p = predicateFactory.createStandardPredicate(name, type, types,activeParas);
		predicates.put(name, new PredicateInfo(p,argNames,(open?PredicateDBType.Open:PredicateDBType.Closed) ));
		return p;
	}
	
	public StandardPredicate addBasicPredicate(String name, ArgumentType[] types, List<String> argNames, boolean open) {
		return addBasicPredicate(name,PredicateTypes.SoftTruth,types,argNames,open);
	}
	
	public StandardPredicate addAggregatePredicate(String name, ArgumentType[] types, List<String> argNames) {
		if (types.length!=argNames.size()) throw new IllegalArgumentException("Expected equal number of argument types and names!");
		StandardPredicate p = predicateFactory.createStandardPredicate(name, PredicateTypes.SoftTruth, types, new double[]{activationParameter});
		predicates.put(name, new PredicateInfo(p,argNames,PredicateDBType.Aggregate));
		return p;
	}

//	public FunctionalPredicate addFunctionalPredicate(String name, List<String> argNames, AttributeSimilarityFunction simFun) {
//		if (argNames.size()!=2) throw new IllegalArgumentException("Expected 2 argument names but was given: " + argNames.size());
//		FunctionalPredicate p = model.getPredicateFactory().createFunctionalPredicate(name, simFun);
//		simFunctions.put(name, new PredicateInfo(p,argNames));
//		return p;
//	}
	
	public FunctionalPredicate addFunctionalPredicate(String name, List<String> argNames, ExternalFunction extFun) {
		if (argNames.size()!=extFun.getArity()) throw new IllegalArgumentException("Arity does not match: " + argNames.size());
		FunctionalPredicate p = predicateFactory.createFunctionalPredicate(name, extFun);
		simFunctions.put(name, new PredicateInfo(p,argNames));
		return p;
	}
	
	public void addKernel(Kernel et) {
		model.addKernel(et);
	}
	
	public UIFullInferenceResult mapInference(Database db)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		return mapInference(db, new EmptyBundle());
	}
	
	public UIFullInferenceResult mapInference(Database db, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		FullInference app = new MaintainedMemoryFullInference(model,db, config);
		FullInferenceResult stats = app.runInference();
		return new UIFullInferenceResult(app.getDatabase(),stats);
	}
	
	public UIFullConfidenceAnalysisResult marginalInference(Database db)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		return marginalInference(db,new PSLCoreConfiguration());
	}
	
	public UIFullConfidenceAnalysisResult marginalInference(Database db, PSLCoreConfiguration config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		FullConfidenceAnalysis app = new MemoryFullConfidenceAnalysis(model,db,config);
		FullConfidenceAnalysisResult stats = app.runConfidenceAnalysis();
		return new UIFullConfidenceAnalysisResult(app.getDatabase(),stats);
	}
	
	public String toString() {
		return model.toString();
	}
	
	public void registerPredicates(DataStore db) {
		for (PredicateInfo predinfo : predicates.values()) {
			db.registerPredicate(predinfo.definition, predinfo.argumentNames, predinfo.type);
		}
		for (PredicateInfo predinfo : aggregatePredicate.values()) {
			db.registerPredicate(predinfo.definition, predinfo.argumentNames, predinfo.type);
		}
	}
	
	public Predicate getPredicate(String predname) {
		if (predicates.containsKey(predname)) {
			return predicates.get(predname).definition;
		} else throw new IllegalArgumentException("Predicate does not exist in model: " + predname);
	}
	
	protected class PredicateInfo {
		protected Predicate definition;
		protected List<String> argumentNames;
		protected PredicateDBType type;
		
		public PredicateInfo(Predicate def, List<String> argnames, PredicateDBType t) {
			definition = def;
			argumentNames = argnames;
			type = t;
		}
		
		public PredicateInfo(Predicate def, List<String> argnames) {
			this(def,argnames,PredicateDBType.Closed);
		}
	}
	
}
