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
package edu.umd.cs.psl.ui.functions.textsimilarity;

import org.apache.commons.lang.StringUtils;

import edu.umd.cs.psl.model.function.AttributeSimilarityFunction;

public class LevensteinStringSimilarity implements AttributeSimilarityFunction {

	/**
	 * String for which the similarity computed by the metrics is below this
	 * threshold are considered to NOT be similar and hence 0 is returned as a
	 * truth value.
	 */
	private static final double defaultSimilarityThreshold = 0.5;

	private final double similarityThreshold;

	public LevensteinStringSimilarity() {
		this(defaultSimilarityThreshold);
	}

	public LevensteinStringSimilarity(double threshold) {
		similarityThreshold = threshold;
	}

	@Override
	public double similarity(String s1, String s2) {

		double ldist = StringUtils.getLevenshteinDistance(s1, s2);
		int maxLen = Math.max(s1.length(), s2.length());
		if (maxLen == 0)
			return 1.0;
		else
			return 1.0 - (ldist / maxLen);

		// double sim = 0;
		// double f_neg_w = 1.0;
		// String working1 = s1;
		// String working2 = s2;
		// sim += f_neg_w
		// * Math.pow(
		// StringUtils.getLevenshteinDistance(working1, working2),
		// 1);
		// if (sim > similarityThreshold)
		// return sim;
		// else
		// return 0.0;
	}

	public String toString() {
		return "Levenstein String Similarity";
	}
}
