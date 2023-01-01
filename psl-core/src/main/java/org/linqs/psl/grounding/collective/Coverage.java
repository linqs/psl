/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
package org.linqs.psl.grounding.collective;

import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.MathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static utilities for computing rule coverage given candidates.
 */
public class Coverage {
    private static final Logger log = LoggerFactory.getLogger(Coverage.class);

    // Static only.
    private Coverage() {}

    public static Set<CandidateQuery> compute(List<Rule> collectiveRules, Set<CandidateQuery> candidates) {
        Containment.computeContainement(collectiveRules, candidates);

        // TODO(eriq): Select using option.
        // Set<CandidateQuery> coverage = greedyNaiveCoverage(collectiveRules, candidates);
        Set<CandidateQuery> coverage = greedySmartCoverage(collectiveRules, candidates);

        if (coverage != null) {
            return coverage;
        }

        // This should never happen.
        throw new IllegalStateException(String.format(
                "Could not compute coverage. Collective Rules: %s, Candidates: %s.",
                collectiveRules, candidates));
    }

    private static Set<CandidateQuery> greedySmartCoverage(List<Rule> collectiveRules, Set<CandidateQuery> candidates) {
        Set<CandidateQuery> coverage = new HashSet<CandidateQuery>();
        Set<Rule> coveredRules = new HashSet<Rule>();

        // First, get the best score for each rule.
        // This is approximate, so we don't care what other rules the best scoring candidate contains.
        Map<Rule, Double> bestRuleScores = new HashMap<Rule, Double>();
        for (CandidateQuery candidate : candidates) {
            for (Rule rule : candidate.getCoveredRules()) {
                if (!bestRuleScores.containsKey(rule) || candidate.getScore() < bestRuleScores.get(rule).doubleValue()) {
                    bestRuleScores.put(rule, Double.valueOf(candidate.getScore()));
                }
            }
        }

        double lowestScore = 0.0f;
        CandidateQuery bestCandidate = null;

        while (coveredRules.size() != collectiveRules.size()) {
            lowestScore = 0.0f;
            bestCandidate = null;

            // Get the candidate with the best score.
            // Score = baseScore - (SUM[best score for each contained rule]).
            // Do not consider condidates already used or rules already covered.
            for (CandidateQuery candidate : candidates) {
                if (coverage.contains(candidate)) {
                    continue;
                }

                double score = candidate.getScore();
                boolean hasUncoveredRule = false;

                for (Rule rule : candidate.getCoveredRules()) {
                    if (!coveredRules.contains(rule)) {
                        score -= bestRuleScores.get(rule);
                        hasUncoveredRule = true;
                    }
                }

                if (hasUncoveredRule && (bestCandidate == null || score < lowestScore)) {
                    lowestScore = score;
                    bestCandidate = candidate;
                }
            }

            coverage.add(bestCandidate);
            coveredRules.addAll(bestCandidate.getCoveredRules());

            if (coveredRules.size() == collectiveRules.size()) {
                return coverage;
            }
        }

        return null;
    }

    private static Set<CandidateQuery> greedyNaiveCoverage(List<Rule> collectiveRules, Set<CandidateQuery> candidates) {
        List<CandidateQuery> orderedCandidates = new ArrayList<CandidateQuery>(candidates);
        Collections.sort(orderedCandidates, new Comparator<CandidateQuery>() {
            @Override
            public int compare(CandidateQuery a, CandidateQuery b) {
                return MathUtils.compare(a.getScore(), b.getScore());
            }
        });

        Set<CandidateQuery> coverage = new HashSet<CandidateQuery>();
        Set<Rule> coveredRules = new HashSet<Rule>();

        for (CandidateQuery candidate : orderedCandidates) {
            if (coveredRules.containsAll(candidate.getCoveredRules())) {
                continue;
            }

            coverage.add(candidate);
            coveredRules.addAll(candidate.getCoveredRules());

            if (coveredRules.size() == collectiveRules.size()) {
                return coverage;
            }
        }

        return null;
    }
}
