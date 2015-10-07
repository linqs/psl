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
package edu.umd.cs.psl.ui.functions.textsimilarity;

import edu.umd.cs.psl.model.argument.StringAttribute;
import org.apache.commons.lang.StringUtils;

import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.function.ExternalFunction;

public class LevenshteinSimilarity implements ExternalFunction {

	/**
	 * String for which the similarity computed by the metrics is below this
	 * threshold are considered to NOT be similar and hence 0 is returned as a
	 * truth value.
	 */
	private static final double defaultSimilarityThreshold = 0.5;

	private final double similarityThreshold;

	public LevenshteinSimilarity() {
		this(defaultSimilarityThreshold);
	}

	public LevenshteinSimilarity(double threshold) {
		similarityThreshold = threshold;
	}

	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ArgumentType[] getArgumentTypes() {
		return new ArgumentType[] {ArgumentType.String, ArgumentType.String};
	}

	@Override
	public double getValue(ReadOnlyDatabase db, GroundTerm... args) {

		String a = ((StringAttribute) args[0]).getValue();
		String b = ((StringAttribute) args[1]).getValue();

		int maxLen = Math.max(a.length(), b.length());
		if (maxLen == 0)
			return 1.0;

		double ldist = StringUtils.getLevenshteinDistance(a, b);
		double sim = 1.0 - (ldist / maxLen);

		if (sim > similarityThreshold)
			return sim;

		return 0.0;
	}

	public String toString() {
		return "Levenstein String Similarity";
	}
}
