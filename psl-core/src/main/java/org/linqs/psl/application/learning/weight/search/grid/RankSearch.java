/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight.search.grid;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

/**
 * A grid seach-like method that searchs over the possible rankings of rules.
 */
public class RankSearch extends BaseGridSearch {
    private static final Logger log = LoggerFactory.getLogger(RankSearch.class);

    /**
     * The delimiter to separate rule weights (and lication ids).
     * Note that we cannot use ',' because our configuration infrastructure will try
     * interpret it as a list of strings.
     */
    public static final String DELIM = ":";

    private Iterator<int[]> permutationIterator;

    private int[] scaleFactors;
    private int scaleIndex;

    public RankSearch(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public RankSearch(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        scaleFactors = StringUtils.splitInt(Options.WLA_RS_SCALING_FACTORS.getString(), DELIM);
        if (scaleFactors.length == 0) {
            throw new IllegalArgumentException("No scaling factors provided.");
        }
        scaleIndex = 0;

        maxNumLocations = (int)MathUtils.smallFactorial(mutableRules.size()) * scaleFactors.length;
        numLocations = maxNumLocations;

        permutationIterator = IteratorUtils.permutations(mutableRules.size());
    }

    @Override
    protected void getWeights(float[] weights) {
        int[] ranks = StringUtils.splitInt(currentLocation, DELIM);
        assert(ranks.length == (mutableRules.size() + 1));

        int scale = ranks[0];

        for (int i = 0; i < mutableRules.size(); i++) {
            // Add one because the permutation iterator starts at 0.
            weights[i] = scale * (1.0f + ranks[i + 1]);
        }
    }

    @Override
    protected boolean chooseNextLocation() {
        if (!permutationIterator.hasNext()) {
            if (scaleIndex == scaleFactors.length - 1) {
                return false;
            }

            scaleIndex++;
            permutationIterator = IteratorUtils.permutations(mutableRules.size());
        }

        int[] indexes = permutationIterator.next();
        currentLocation = "" + scaleIndex + DELIM + StringUtils.join(DELIM, indexes);

        return true;
    }
}
