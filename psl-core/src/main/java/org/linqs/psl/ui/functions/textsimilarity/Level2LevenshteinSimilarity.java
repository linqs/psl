/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.ui.functions.textsimilarity;

import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.StringAttribute;

import com.wcohen.ss.BasicStringWrapper;
import com.wcohen.ss.Level2Levenstein;

/**
 * Wraps the Level 2 Levenshtein string similarity from the Second String library.
 * Level 2 means that tokens are broken up before comparison.
 * If the similarity is below a threshold (default=0.5) it returns 0.
 */
class Level2LevenshteinSimilarity implements ExternalFunction {
	// similarity threshold (default=0.5)
	private double simThresh;

	// constructors
	public Level2LevenshteinSimilarity() {
		this.simThresh = 0.5;
	}
	public Level2LevenshteinSimilarity(double simThresh) {
		this.simThresh = simThresh;
	}
	
	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ConstantType[] getArgumentTypes() {
		return new ConstantType[] { ConstantType.String, ConstantType.String };
	}
	
	@Override
	public double getValue(ReadOnlyDatabase db, Constant... args) {
		String a = ((StringAttribute) args[0]).getValue();
		String b = ((StringAttribute) args[1]).getValue();
		BasicStringWrapper aWrapped = new BasicStringWrapper(a);
		BasicStringWrapper bWrapped = new BasicStringWrapper(b);
		
		Level2Levenstein leven = new Level2Levenstein();
		double sim = leven.score(aWrapped, bWrapped);
		
		if (sim < simThresh) 
			return 0.0;
		else 
			return sim;
    }
}
