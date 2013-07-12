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
package edu.umd.cs.psl.ui.functions.textsimilarity;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.function.ExternalFunction;

/**
 * Implements the dice similarity measure (quick-n-dirty version).
 * Notes:
 *	- This is not the true dice similarity.
 * 	- A better implementation might not use strings for bigrams.
 *	- The tokenizer could probably be improved.
 */
public class DiceSimilarity implements ExternalFunction
{
	// similarity threshold (default=0.5)
	private double simThresh;
	
	// constructors
	public DiceSimilarity() {
		this.simThresh = 0.5;
	}
	public DiceSimilarity(double simThresh) {
		this.simThresh = simThresh;
	}
	
	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ArgumentType[] getArgumentTypes() {
		return new ArgumentType[] { ArgumentType.String, ArgumentType.String };
	}
	
	@Override
	public double getValue(ReadOnlyDatabase db, GroundTerm... args) {
		// Create two sets of character bigrams, one for each string.
		Set<String> s1 = splitIntoBigrams(args[0].toString());
		Set<String> s2 = splitIntoBigrams(args[1].toString());
		
		// Get the number of elements in each set.
		int n1 = s1.size();
		int n2 = s2.size();
				
		// Find the intersection, and get the number of elements in that set.
		s1.retainAll(s2);
		int nt = s1.size();
				
		// The coefficient is:
		// 
		//        2 ∙ | s1 ⋂ s2 |
		// D = ----------------------
		//        | s1 | + | s2 |
		// 
		double sim = (2.0 * (double)nt) / ((double)(n1 + n2));
		
		if (sim < simThresh) 
			return 0.0;
		else 
			return sim;
	}
 
    private static Set<String> splitIntoBigrams(String s) {
    	// tokenize the input
    	String[] tokens = s.split("\\s+");
    	// create bigrams from tokens
		ArrayList<String> bigrams = new ArrayList<String>();
		if (tokens.length == 1) {
			bigrams.add(tokens[0]);
		}
		else {
			for (int i = 1; i < tokens.length; i++) {
				StringBuilder sb = new StringBuilder();
				sb.append(tokens[i-1]);
				sb.append(",");
				sb.append(tokens[i]);
				bigrams.add(sb.toString());
			}
		}
		return new TreeSet<String>(bigrams);
    }
}
