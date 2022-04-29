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
package org.linqs.psl.evaluation.statistics;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;;
import org.linqs.psl.model.atom.GroundAtom;
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

    private RepresentativeMetric representative;

    private int count;
    private double absoluteError;
    private double squaredError;

    public ContinuousEvaluator() {
        this(Options.EVAL_CONT_REPRESENTATIVE.getString());
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

        for (Map.Entry<GroundAtom, GroundAtom> entry : getMap(trainingMap)) {
            if (predicate != null && entry.getKey().getPredicate() != predicate) {
                continue;
            }

            count++;
            absoluteError += Math.abs(entry.getValue().getValue() - entry.getKey().getValue());
            squaredError += Math.pow(entry.getValue().getValue() - entry.getKey().getValue(), 2);
        }
    }

    @Override
    public double getRepMetric() {
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
    public double getBestRepScore() {
        switch (representative) {
            case MAE:
            case MSE:
                return 0.0;
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public boolean isHigherRepBetter() {
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
        double mse = mse();
        return String.format("MAE: %f, MSE: %f, RMSE: %f", mae(), mse, Math.sqrt(mse));
    }
}
