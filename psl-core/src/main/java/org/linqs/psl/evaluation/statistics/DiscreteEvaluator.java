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
import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.util.MathUtils;

import java.util.Map;

/**
 * Compute various discrete statistics using a threshold.
 */
public class DiscreteEvaluator extends Evaluator {
    public enum RepresentativeMetric {
        F1,
        POSITIVE_PRECISION,
        NEGATIVE_PRECISION,
        POSITIVE_RECALL,
        NEGATIVE_RECALL,
        ACCURACY
    }

    private double threshold;
    private RepresentativeMetric representative;

    private int tp;
    private int fn;
    private int tn;
    private int fp;

    public DiscreteEvaluator() {
        this(Options.EVAL_DISCRETE_THRESHOLD.getDouble());
    }

    public DiscreteEvaluator(double threshold) {
        this(threshold, Options.EVAL_DISCRETE_REPRESENTATIVE.getString());
    }

    public DiscreteEvaluator(double threshold, String representative) {
        this(threshold, RepresentativeMetric.valueOf(representative.toUpperCase()));
    }

    public DiscreteEvaluator(double threshold, RepresentativeMetric representative) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threhsold must be in (0, 1). Found: " + threshold);
        }

        this.threshold = threshold;
        this.representative = representative;

        tp = 0;
        fn = 0;
        tn = 0;
        fp = 0;
    }

    @Override
    public void compute(TrainingMap trainingMap) {
        compute(trainingMap, null);
    }

    @Override
    public void compute(TrainingMap trainingMap, StandardPredicate predicate) {
        tp = 0;
        fn = 0;
        tn = 0;
        fp = 0;

        for (Map.Entry<GroundAtom, GroundAtom> entry : getMap(trainingMap)) {
            if (predicate != null && entry.getKey().getPredicate() != predicate) {
                continue;
            }

            boolean expected = (entry.getValue().getValue() >= threshold);
            boolean predicted = (entry.getKey().getValue() >= threshold);

            if (predicted && expected) {
                tp++;
            } else if (!predicted && expected) {
                fn++;
            } else if (predicted && !expected) {
                fp++;
            } else {
                tn++;
            }
        }
    }

    @Override
    public double getRepMetric() {
        switch (representative) {
            case F1:
                return f1();
            case POSITIVE_PRECISION:
                return positivePrecision();
            case NEGATIVE_PRECISION:
                return negativePrecision();
            case POSITIVE_RECALL:
                return positiveRecall();
            case NEGATIVE_RECALL:
                return negativeRecall();
            case ACCURACY:
                return accuracy();
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public double getBestRepScore() {
        switch (representative) {
            case F1:
            case POSITIVE_PRECISION:
            case NEGATIVE_PRECISION:
            case POSITIVE_RECALL:
            case NEGATIVE_RECALL:
            case ACCURACY:
                return 1.0;
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public boolean isHigherRepBetter() {
        return true;
    }

    public double getThreshold() {
        return threshold;
    }

    public double positivePrecision() {
        if (tp + fp == 0) {
            return 0.0;
        }

        return tp / (double)(tp + fp);
    }

    public double negativePrecision() {
        if (tn + fn == 0) {
            return 0.0;
        }

        return tn / (double)(tn + fn);
    }

    public double positiveRecall() {
        if (tp + fn == 0) {
            return 0.0;
        }

        return tp / (double)(tp + fn);
    }

    public double negativeRecall() {
        if (tn + fp == 0) {
            return 0.0;
        }

        return tn / (double)(tn + fp);
    }

    public double f1() {
        return fScore(1.0);
    }

    public double fScore(double beta) {
        double precision = positivePrecision();
        double recall = positiveRecall();

        double denom = (Math.pow(beta, 2) * precision) + recall;
        if (MathUtils.isZero(denom)) {
            return 0.0;
        }

        return (1.0 + Math.pow(beta, 2)) * (precision * recall) / denom;
    }

    public double accuracy() {
        int numAtoms = tp + tn + fp + fn;
        if (numAtoms == 0) {
            return 0.0;
        }

        return (tp + tn) / (double)numAtoms;
    }

    @Override
    public String getAllStats() {
        return String.format(
                "Accuracy: %f, F1: %f," +
                " Positive Class Precision: %f, Positive Class Recall: %f," +
                " Negative Class Precision: %f, Negative Class Recall: %f",
                accuracy(), f1(),
                positivePrecision(), positiveRecall(),
                negativePrecision(), negativeRecall());
    }
}
