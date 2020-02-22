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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compute various ranking statistics.
 */
public class RankingEvaluator extends Evaluator {
    public enum RepresentativeMetric {
        AUROC,
        POSITIVE_AUPRC,
        NEGATIVE_AUPRC
    }

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "rankingevaluator";

    /**
     * The truth threshold.
     */
    public static final String THRESHOLD_KEY = CONFIG_PREFIX + ".threshold";
    public static final double DEFAULT_THRESHOLD = 0.5;

    /**
     * The representative metric.
     * Default to F1.
     * Must match a string from the RepresentativeMetric enum.
     */
    public static final String REPRESENTATIVE_KEY = CONFIG_PREFIX + ".representative";
    public static final String DEFAULT_REPRESENTATIVE = "AUROC";

    private double threshold;
    private RepresentativeMetric representative;

    // Both sorted DESC by truth value.
    private List<GroundAtom> truth;
    private List<GroundAtom> predicted;

    public RankingEvaluator() {
        this(
                Config.getDouble(THRESHOLD_KEY, DEFAULT_THRESHOLD),
                Config.getString(REPRESENTATIVE_KEY, DEFAULT_REPRESENTATIVE));
    }

    public RankingEvaluator(double threshold) {
        this(threshold, DEFAULT_REPRESENTATIVE);
    }

    public RankingEvaluator(double threshold, String representative) {
        this(threshold, RepresentativeMetric.valueOf(representative.toUpperCase()));
    }

    public RankingEvaluator(double threshold, RepresentativeMetric representative) {
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

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getLabelMap().entrySet()) {
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
    public double getRepresentativeMetric() {
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
    public boolean isHigherRepresentativeBetter() {
        return true;
    }

    public double getThreshold() {
        return threshold;
    }

    /**
     * Returns area under the precision recall curve.
     * This is a simple implementation that assumes all the ground truth is 0/1
     * and does not make any effort to approximate the first point.
     */
    public double positiveAUPRC() {
        // both lists are sorted
        int totalPositives = 0;
        for (GroundAtom atom : truth) {
            if (atom.getValue() > threshold) {
                totalPositives++;
            }
        }

        if (totalPositives == 0) {
            return 0.0;
        }

        double area = 0.0;
        int tp = 0;
        int fp = 0;

        // Precision is along the Y-axis.
        double prevY = 1.0;
        // Recall is along the X-axis.
        double prevX = 0.0;

        // Go through the atoms from highest truth value to lowest.
        for (GroundAtom atom : predicted) {
            Boolean label = getLabel(atom);
            if (label == null) {
                continue;
            }

            // Assume we predicted everything positive.
            if (label != null && label) {
                tp++;
            } else {
                fp++;
            }

            double newY = tp / (double)(tp + fp);
            double newX = tp / (double)totalPositives;

            area += 0.5 * (newX - prevX) * Math.abs(newY - prevY) + (newX - prevX) * newY;
            prevY = newY;
            prevX = newX;
        }

        // Add the final piece.
        area += 0.5 * (1.0 - prevX) * Math.abs(0.0 - prevY) + (1.0 - prevX) * 0.0;

        return area;
    }

    /**
     * Returns area under the precision recall curve for the negative class.
     * The same stipulations for AUPRC hold here.
     */
    public double negativeAUPRC() {
        // both lists are sorted
        int totalPositives = 0;
        for (GroundAtom atom : truth) {
            if (atom.getValue() > threshold) {
                totalPositives++;
            }
        }

        int totalNegatives = predicted.size() - totalPositives;
        if (totalNegatives == 0) {
            return 0.0;
        }

        double area = 0.0;
        // Assume we have already predicted everything false, and correct as we go.
        int fn = totalPositives;
        int tn = totalNegatives;

        // Precision is along the Y-axis.
        double prevY = tn / (double)(tn + fn);
        // Recall is along the X-axis.
        double prevX = 1.0;

        // Go through the atoms from highest truth value to lowest.
        for (GroundAtom atom : predicted) {
            Boolean label = getLabel(atom);
            if (label == null) {
                continue;
            }

            if (label != null && label) {
                fn--;
            } else {
                tn--;
            }

            double newY = 0.0;
            if (tn + fn > 0) {
                newY = tn / (double)(tn + fn);
            }

            double newX = tn / (double)totalNegatives;

            area += 0.5 * (prevX - newX) * Math.abs(newY - prevY) + (prevX - newX) * newY;
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
            if (label != null && label) {
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
