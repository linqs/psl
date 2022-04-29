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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compute various area-under-curve statistics.
 */
public class AUCEvaluator extends Evaluator {
    public enum RepresentativeMetric {
        AUROC,
        POSITIVE_AUPRC,
        NEGATIVE_AUPRC
    }

    private double threshold;
    private RepresentativeMetric representative;

    // Both sorted DESC by truth value.
    private List<GroundAtom> truth;
    private List<GroundAtom> predicted;

    public AUCEvaluator() {
        this(Options.EVAL_AUC_THRESHOLD.getDouble());
    }

    public AUCEvaluator(double threshold) {
        this(threshold, Options.EVAL_AUC_REPRESENTATIVE.getString());
    }

    public AUCEvaluator(double threshold, String representative) {
        this(threshold, RepresentativeMetric.valueOf(representative.toUpperCase()));
    }

    public AUCEvaluator(double threshold, RepresentativeMetric representative) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threhsold must be in (0, 1). Found: " + threshold);
        }

        this.threshold = threshold;
        this.representative = representative;

        truth = new ArrayList<GroundAtom>();
        predicted = new ArrayList<GroundAtom>();
    }

    @Override
    public void compute(TrainingMap trainingMap) {
        compute(trainingMap, null);
    }

    @Override
    public void compute(TrainingMap trainingMap, StandardPredicate predicate) {
        truth = new ArrayList<GroundAtom>(trainingMap.getLabelMap().size());
        predicted = new ArrayList<GroundAtom>(trainingMap.getLabelMap().size());

        for (Map.Entry<GroundAtom, GroundAtom> entry : getMap(trainingMap)) {
            if (predicate != null && entry.getKey().getPredicate() != predicate) {
                continue;
            }

            truth.add(entry.getValue());
            predicted.add(entry.getKey());
        }

        Collections.sort(truth);
        Collections.sort(predicted);
    }

    @Override
    public double getRepMetric() {
        switch (representative) {
            case AUROC:
                return auroc();
            case POSITIVE_AUPRC:
                return positiveAUPRC();
            case NEGATIVE_AUPRC:
                return negativeAUPRC();
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public double getBestRepScore() {
        switch (representative) {
            case AUROC:
            case POSITIVE_AUPRC:
            case NEGATIVE_AUPRC:
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

    /**
     * Returns area under the precision recall curve.
     */
    public double positiveAUPRC() {
        return auprc(true);
    }

    /**
     * Returns area under the precision recall curve for the negative class.
     */
    public double negativeAUPRC() {
        return auprc(false);
    }

    private double auprc(boolean positiveIsTrue) {
        // Both lists are sorted.
        int totalPositives = 0;
        for (GroundAtom atom : truth) {
            if (!((atom.getValue() >= threshold) ^ positiveIsTrue)) {
                totalPositives++;
            }
        }

        if (totalPositives == 0) {
            return 0.0;
        }

        double area = 0.0;
        int tp = 0;
        int fp = 0;

        // Precision is along the Y-axis, and we always start at full precision.
        double prevY = 1.0;
        // Recall is along the X-axis, and we always start at no recall.
        double prevX = 0.0;

        // Go through the atoms from highest truth value to lowest.
        for (GroundAtom atom : predicted) {
            Boolean rawLabel = getLabel(atom);
            if (rawLabel == null) {
                continue;
            }

            boolean label = rawLabel.booleanValue();
            if (!positiveIsTrue) {
                label = !label;
            }

            // Assume we predicted everything positive.
            if (label) {
                tp++;
            } else {
                fp++;
            }

            double newY = tp / (double)(tp + fp);
            double newX = tp / (double)totalPositives;

            // Use trapezoids to compute the area.
            // Consider the area of the largest rectangle (highest y), and then cut out a triangle.
            area += ((newX - prevX) * Math.max(prevY, newY)) - 0.5 * ((newX - prevX) * Math.abs(newY - prevY));

            prevY = newY;
            prevX = newX;
        }

        return area;
    }

    /**
     * Returns area under ROC curve.
     * Assumes predicted GroundAtoms are hard truth values.
     */
    public double auroc() {
        int totalPositives = 0;
        for (GroundAtom atom : truth) {
            if (atom.getValue() > threshold) {
                totalPositives++;
            }
        }

        int totalNegatives = predicted.size() - totalPositives;

        if (totalPositives == 0) {
            return 0.0;
        } else if (totalNegatives == 0) {
            return 1.0;
        }

        double area = 0.0;
        int tp = 0;
        int fp = 0;

        // True positrive rate (TPR) is along the Y-axis.
        double prevY = 0.0;
        // False positive rate (FPR) is along the X-axis.
        double prevX = 0.0;

        // Go through the atoms from highest truth value to lowest.
        for (GroundAtom atom : predicted) {
            Boolean label = getLabel(atom);
            if (label == null) {
                continue;
            }

            // Assume we predicted everything positive.
            if (label.booleanValue()) {
                tp++;
            } else {
                fp++;
            }

            double newY = (double)tp / (double)totalPositives;
            double newX = (double)fp / (double)totalNegatives;

            area += 0.5 * (newX - prevX) * Math.abs(newY - prevY) + (newX - prevX) * newY;
            prevY = newY;
            prevX = newX;
        }

        // Add the final piece.
        area += 0.5 * (1.0 - prevX) * Math.abs(1.0 - prevY) + (1.0 - prevX) * 1.0;

        return area;
    }

    @Override
    public String getAllStats() {
        return String.format(
                "AUROC: %f, Positive Class AUPRC: %f, Negative Class AUPRC: %f",
                auroc(), positiveAUPRC(), negativeAUPRC());
    }

    /**
     * If the atom exists in the truth, return it's boolean value.
     * Otherwise return null.
     */
    private Boolean getLabel(GroundAtom atom) {
        int index = truth.indexOf(atom);
        if (index == -1) {
            return null;
        }

        return truth.get(index).getValue() > threshold;
    }
}
