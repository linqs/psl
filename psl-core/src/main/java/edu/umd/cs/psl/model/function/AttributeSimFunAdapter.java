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
package edu.umd.cs.psl.model.function;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.TextAttribute;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;
import edu.umd.cs.psl.model.predicate.type.PredicateType;
import edu.umd.cs.psl.model.predicate.type.PredicateTypes;
import edu.umd.cs.psl.optimizer.NumericUtilities;

public class AttributeSimFunAdapter implements ExternalFunction, BulkExternalFunction {

	public static final double defaultThreshold = 0.1;
	private static final ArgumentType[] argumenttypes = new ArgumentType[]{ArgumentTypes.Text,ArgumentTypes.Text};
	
	private final AttributeSimilarityFunction function;
	
	public AttributeSimFunAdapter(AttributeSimilarityFunction fct, double thres) {
		function = fct;
		//threshold = thres;
	}
	
	public AttributeSimFunAdapter(AttributeSimilarityFunction fct) {
		this(fct,defaultThreshold);
	}
	
	@Override
	public ArgumentType[] getArgumentTypes() {
		return argumenttypes;
	}

	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public PredicateType getPredicateType() {
		return PredicateTypes.SoftTruth;
	}
	
	private static final String[] args2String(GroundTerm... args) {
		if (args.length!=2) throw new IllegalArgumentException("Expectecd 2 arguments, but was given: " + args);
		String[] s = new String[2];
		for (int i=0;i<2;i++) {
			GroundTerm arg = args[i];
			if (!(arg instanceof TextAttribute)) throw new IllegalArgumentException("Argument "+i+" is not of type text, but: " + arg);
			s[i] = ((TextAttribute)arg).getAttribute();
		}
		return s;
	}
	
	@Override
	public double[] getValue(GroundTerm... args) {
		String[] strs = args2String(args);
		return new double[]{function.similarity(strs[0], strs[1])};
	}


	@Override
	public Map<GroundTerm[], double[]> bulkCompute(Map<Variable, int[]> argMap,
			Map<GroundTerm, GroundTerm[]>... args) {
		//TODO: this is an EXTREMELY naive implementation of the bulk string similarity function.
		//This should be re-implemented more intelligently.
		Preconditions.checkArgument(args.length==2);
		Map<GroundTerm[], double[]> result = new HashMap<GroundTerm[],double[]>();
		for (Map.Entry<GroundTerm, GroundTerm[]> outer : args[0].entrySet()) {
			assert outer.getValue().length==1;
			for (Map.Entry<GroundTerm, GroundTerm[]> inner : args[1].entrySet()) {
				assert inner.getValue().length==1;
				String[] strs = args2String(outer.getValue()[0],inner.getValue()[0]);
				double val = function.similarity(strs[0],strs[1]);
				if (!NumericUtilities.equals(val,getPredicateType().getDefaultValues()[0])) {
					result.put(new GroundTerm[]{outer.getKey(),inner.getKey()}, new double[]{val});
				}
			}
		}
		
		return null;
	}




}
