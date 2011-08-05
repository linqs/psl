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
package edu.umd.cs.psl.model.predicate;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.function.AttributeSimFunAdapter;
import edu.umd.cs.psl.model.function.AttributeSimilarityFunction;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.model.predicate.type.PredicateType;
import edu.umd.cs.psl.ui.functions.textsimilarity.CosineSimilarity;
import edu.umd.cs.psl.ui.functions.textsimilarity.LevenshteinStringSimilarity;
import edu.umd.cs.psl.util.dynamicclass.DynamicClassLoader;

public class PredicateFactory {

	private final Map<String,Predicate> predicateByName;
	

	public PredicateFactory() {
		predicateByName = new HashMap<String,Predicate>();
	}
	
	public StandardPredicate createStandardPredicate(String name, PredicateType type,  ArgumentType[] types, double[] activationParas) {
		StandardPredicate p = new StandardPredicate(name,type,types,activationParas);
		addPredicate(p,name);
		return p;
	}
	
	public FunctionalPredicate createFunctionalPredicate(String name, String definition) {
		return createFunctionalPredicate(name,parseDefinition(definition));
	}
	
	public FunctionalPredicate createFunctionalPredicate(String name, AttributeSimilarityFunction simFun) {
		return createFunctionalPredicate(name,new AttributeSimFunAdapter(simFun));
	}
	
	public FunctionalPredicate createFunctionalPredicate(String name, ExternalFunction simFun) {
		FunctionalPredicate p = new ExternalFunctionPredicate(name,simFun);
		addPredicate(p,name);
		return p;
	}
	
	private void addPredicate(Predicate p, String name) {
		if (predicateByName.containsKey(name)) throw new IllegalArgumentException("Predicate by that name has already been defined: " + name);
		predicateByName.put(name, p);

	}
	
	public Predicate getPredicate(String name) {
		if (!predicateByName.containsKey(name)) throw new IllegalArgumentException("Predicate is unkown: " + name);
		return predicateByName.get(name);
	}
	
	public boolean hasPredicate(String name) {
		return predicateByName.containsKey(name);
	}
	
	public boolean hasPredicate(Predicate p) {
		return hasPredicate(p.getName());
	}
	
	public Iterable<FunctionalPredicate> getFunctionalPredicates() {
		return Iterables.filter(predicateByName.values(), FunctionalPredicate.class);
	}
	
	public Iterable<StandardPredicate> getStandardPredicates() {
		return Iterables.filter(predicateByName.values(), StandardPredicate.class);
	}
	
	public Iterable<Predicate> getPredicates() {
		return predicateByName.values();
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (Predicate p : predicateByName.values()) {
			s.append(p.toString()).append("\n");
		}
		return s.toString();
	}
	
	//============== STATIC ============================
	
	public static final Map<String,Class<? extends AttributeSimilarityFunction>> definedAttributeSimFun = 
		new ImmutableMap.Builder<String,Class<? extends AttributeSimilarityFunction>>()
			.put("basicstring", LevenshteinStringSimilarity.class)
			.put("cosine", CosineSimilarity.class)
			.build();
	
	public static final AttributeSimilarityFunction parseDefinition(String definition) {
		try {
			return DynamicClassLoader.loadClassArbitraryArgs(definition, definedAttributeSimFun, AttributeSimilarityFunction.class);
		} catch (Throwable e) {
			e.printStackTrace();
			throw new AssertionError("Unknown similarity function: " + definition);
		}
		
	}
	
}
