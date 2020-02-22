/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
package org.linqs.psl.evaluation.statistics;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Config;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compute various statistics on data known to be categorical.
 *
 * Categorical atoms are normal atoms that are interpreted to have two parts: the base and the category.
 * Ex: HasOS(x, 'Linux'), HasOS(x, 'BSD'), HasOS(x, 'Mac'), ...
 * Both the base and category can consist of more than one variable.
 * Ex: HasClimate(location, time, 'Cold', 'Rain'), HasClimate(location, time, 'Cold', 'Snow'), ...
 * However, each atom can only have one category.
 * Any arguments not in a category will be considered to be in the base.
 *
 * The best (highest truth value) category for each base will be chosen.
 * Then the truth database will be iterated over for atoms with a 1.0 truth value.
 * If the truth atom was chosen as the best category in the predicted data, then that is a hit.
 * Anything else is a miss.
 */
public class CategoricalEvaluator extends Evaluator {
    public enum RepresentativeMetric {
        ACCURACY
    }

    public static final String DELIM = ":";

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "categoricalevaluator";

    /**
     * The index of the arguments in the predicate  (delimited by colons).
     * The other arguments are treated as identifiers.
     * Zero-indexed.
     */
    public static final String CATEGORY_INDEXES_KEY = CONFIG_PREFIX + ".categoryindexes";
    public static final String DEFAULT_CATEGORY_INDEXES = "1";

    /**
     * The representative metric.
     * Default to accuracy.
     * Must match a string from the RepresentativeMetric enum.
     */
    public static final String REPRESENTATIVE_KEY = CONFIG_PREFIX + ".representative";
    public static final String DEFAULT_REPRESENTATIVE = "ACCURACY";

    /**
     * The default predicate to use when none are supplied.
     */
    public static final String DEFAULT_PREDICATE_KEY = CONFIG_PREFIX + ".defaultpredicate";

    private Set<Integer> categoryIndexes;
    private RepresentativeMetric representative;
    private String defaultPredicate;

    private int hits;
    private int misses;

    public CategoricalEvaluator() {
        this(RepresentativeMetric.valueOf(Config.getString(REPRESENTATIVE_KEY, DEFAULT_REPRESENTATIVE)),
                StringUtils.splitInt(Config.getString(CATEGORY_INDEXES_KEY, DEFAULT_CATEGORY_INDEXES), DELIM));
    }

    public CategoricalEvaluator(int... rawCategoryIndexes) {
        this(DEFAULT_REPRESENTATIVE, rawCategoryIndexes);
    }

    public CategoricalEvaluator(String representative, int... rawCategoryIndexes) {
        this(RepresentativeMetric.valueOf(representative.toUpperCase()), rawCategoryIndexes);
    }

    public CategoricalEvaluator(RepresentativeMetric representative, int... rawCategoryIndexes) {
        this.representative = representative;
        setCategoryIndexes(rawCategoryIndexes);

        defaultPredicate = Config.getString(DEFAULT_PREDICATE_KEY, null);

        hits = 0;
        misses = 0;
    }

    public void setCategoryIndexes(int... rawCategoryIndexes) {
        if (rawCategoryIndexes == null || rawCategoryIndexes.length == 0) {
            throw new IllegalArgumentException("Found no category indexes.");
        }

        categoryIndexes = new HashSet<Integer>(rawCategoryIndexes.length);
        for (int catIndex : rawCategoryIndexes) {
            if (catIndex < 0) {
                throw new IllegalArgumentException("Category indexes must be non-negative. Found: " + catIndex);
            }

            categoryIndexes.add(new Integer(catIndex));
        }
    }

    @Override
    public void compute(TrainingMap trainingMap) {
        if (defaultPredicate == null) {
            throw new UnsupportedOperationException("CategoricalEvaluators must have a default predicate set (through config).");
        }

        compute(trainingMap, StandardPredicate.get(defaultPredicate));
    }

    @Override
    public void compute(TrainingMap trainingMap, StandardPredicate predicate) {
        hits = 0;
        misses = 0;

        Set<GroundAtom> predictedCategories = getPredictedCategories(trainingMap, predicate);

        for (GroundAtom truthAtom : trainingMap.getLabelMap().values()) {
            if (predicate != null && truthAtom.getPredicate() != predicate) {
                continue;
            }

            if (truthAtom.getValue() < 1.0) {
                continue;
            }

            if (predictedCategories.contains(truthAtom)) {
                hits++;
            } else {
                misses++;
            }
        }
    }

    @Override
    public double getRepresentativeMetric() {
        switch (representative) {
            case ACCURACY:
                return accuracy();
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public boolean isHigherRepresentativeBetter() {
        return true;
    }

    public double accuracy() {
        if (hits + misses == 0) {
            return 0.0;
        }

        return hits / (double)(hits + misses);
    }

    @Override
    public String getAllStats() {
        return String.format("Categorical Accuracy: %f", accuracy());
    }

    /**
     * Build up a set that has all the atoms that represet the best categorical assignments.
     */
    private Set<GroundAtom> getPredictedCategories(TrainingMap trainingMap, StandardPredicate predicate) {
        int numArgs = predicate.getArity();

        // This map will be as deep as the number of category arguments.
        // The value will either be a GroundAtom representing the current best category,
        // or another Map<Constant, Object>, and so on.
        Map<Constant, Object> predictedCategories = null;

        for (GroundAtom atom : trainingMap.getLabelMap().keySet()) {
            if (atom.getPredicate() != predicate) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<Constant, Object> ignoreWarning = (Map<Constant, Object>)putPredictedCategories(predictedCategories, atom, 0);
            predictedCategories = ignoreWarning;
        }

        Set<GroundAtom> rtn = new HashSet<GroundAtom>();
        collectPredictedCategories(predictedCategories, rtn);

        return rtn;
    }

    /**
     * Recursively descend into the map and put the atom in if it is a best category.
     * Return what should be at the map where we descended (classic tree building style).
     */
    private Object putPredictedCategories(Object currentNode, GroundAtom atom, int argIndex) {
        assert(argIndex <= atom.getArity());

        // Skip this arg if it is a category.
        if (categoryIndexes.contains(argIndex)) {
            return putPredictedCategories(currentNode, atom, argIndex + 1);
        }

        // If we have coverd all the arguments, then we are either looking at a null
        // if there was no previous best or the previous best.
        if (argIndex == atom.getArity()) {
            if (currentNode == null) {
                return atom;
            }

            @SuppressWarnings("unchecked")
            GroundAtom oldBest = (GroundAtom)currentNode;

            if (atom.getValue() > oldBest.getValue()) {
                return atom;
            } else {
                return oldBest;
            }
        }

        // We still have further to descend.

        Map<Constant, Object> predictedCategories;
        if (currentNode == null) {
            predictedCategories = new HashMap<Constant, Object>();
        } else {
            @SuppressWarnings("unchecked")
            Map<Constant, Object> ignoreWarning = (Map<Constant, Object>)currentNode;
            predictedCategories = ignoreWarning;
        }

        Constant arg = atom.getArguments()[argIndex];
        predictedCategories.put(arg, putPredictedCategories(predictedCategories.get(arg), atom, argIndex + 1));

        return predictedCategories;
    }

    private void collectPredictedCategories(Map<Constant, Object> predictedCategories, Set<GroundAtom> result) {
        for (Object value : predictedCategories.values()) {
            if (value instanceof GroundAtom) {
                result.add((GroundAtom)value);
            } else {
                @SuppressWarnings("unchecked")
                Map<Constant, Object> ignoreWarning = (Map<Constant, Object>)value;
                collectPredictedCategories(ignoreWarning, result);
            }
        }
    }
}
