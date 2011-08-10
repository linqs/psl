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

public class LevenshteinStringSimilarity implements AttributeSimilarityFunction {

	/**
	 * String for which the similarity computed by the metrics is below this
	 * threshold are considered to NOT be similar and hence 0 is returned as a
	 * truth value.
	 */
	private static final double defaultSimilarityThreshold = 0.5;

	private final double similarityThreshold;

	public LevenshteinStringSimilarity() {
		this(defaultSimilarityThreshold);
	}

	public LevenshteinStringSimilarity(double threshold) {
		similarityThreshold = threshold;
	}

	@Override
	public double similarity(String s1, String s2) {

		int maxLen = Math.max(s1.length(), s2.length());
		if (maxLen == 0)
			return 1.0;

		double ldist = StringUtils.getLevenshteinDistance(s1, s2);
		double sim = 1.0 - (ldist / maxLen);

		if (sim > similarityThreshold)
			return sim;

		return 0.0;
	}

	public String toString() {
		return "Levenstein String Similarity";
	}
}
