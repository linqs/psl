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
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.util.MathUtils;

import java.util.Map;

/**
 * Compute various continuous statistics using a threshold.
 */
public class ContinuousEvaluator extends Evaluator {
    public enum RepresentativeMetric {
        MAE,
        MSE
    }

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "continuousevaluator";

    /**
     * The representative metric.
     * Default to MSE.
     * Must match a string from the RepresentativeMetric enum.
     */
    public static final String REPRESENTATIVE_KEY = CONFIG_PREFIX + ".representative";
    public static final String DEFAULT_REPRESENTATIVE = "MSE";

    private RepresentativeMetric representative;

    private int count;
    private double absoluteError;
    private double squaredError;

    public ContinuousEvaluator() {
        this(Config.getString(REPRESENTATIVE_KEY, DEFAULT_REPRESENTATIVE));
    }

    public ContinuousEvaluator(String representative) {
        this(RepresentativeMetric.valueOf(representative.toUpperCase()));
    }

    public ContinuousEvaluator(RepresentativeMetric representative) {
        this.representative = representative;

        count = 0;
        absoluteError = 0.0;
        squaredError = 0.0;
    }

    @Override
    public void compute(TrainingMap trainingMap) {
        compute(trainingMap, null);
    }

    @Override
    public void compute(TrainingMap trainingMap, StandardPredicate predicate) {
        count = 0;
        absoluteError = 0.0;
        squaredError = 0.0;

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getLabelMap().entrySet()) {
            if (predicate != null && entry.getKey().getPredicate() != predicate) {
                continue;
            }

            count++;
            absoluteError += Math.abs(entry.getValue().getValue() - entry.getKey().getValue());
            squaredError += Math.pow(entry.getValue().getValue() - entry.getKey().getValue(), 2);
        }
    }

    @Override
    public double getRepresentativeMetric() {
        switch (representative) {
            case MAE:
                return mae();
            case MSE:
                return mse();
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public boolean isHigherRepresentativeBetter() {
        return false;
    }

    public double mae() {
        if (count == 0) {
            return 0.0;
        }

        return absoluteError / count;
    }

    public double mse() {
        if (count == 0) {
            return 0.0;
        }

        return squaredError / count;
    }

    @Override
    public String getAllStats() {
        return String.format("MAE: %f, MSE: %f", mae(), mse());
    }
}
