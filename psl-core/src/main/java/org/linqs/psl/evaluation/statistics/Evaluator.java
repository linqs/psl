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
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.UnmanagedAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.util.IteratorUtils;

import java.util.AbstractMap;
import java.util.Map;

/**
 * Compute some metric (or set of metrics) for some predicted and labeled data.
 * Every evaluator will have some "representative metric" that is usually set through config options.
 * For example, a discrete Evaluator may have F1 as its representative metric.
 * This is so that more automated methods like search-based weight learning and CLI evaluation
 * can easily access all types of metrics through a common interface.
 * The evaluator will also have to provide some indication as to if higher values for the
 * representative metric is better than lower values.
 * In addition to the representative metric, subclasses classes will usually
 * provide their own methods to access specific metrics.
 *
 * One of the compute methods must be called before attempting to get statistics.
 */
public abstract class Evaluator {
    protected boolean includeObserved;
    protected boolean closeTruth;

    protected Evaluator() {
        includeObserved = Options.EVAL_INCLUDE_OBS.getBoolean();
        closeTruth = Options.EVAL_CLOSE_TRUTH.getBoolean();
    }

    public boolean getIncludeObserved() {
        return includeObserved;
    }

    public void setIncludeObserved(boolean includeObserved) {
        this.includeObserved = includeObserved;
    }

    public boolean getCloseTruth() {
        return closeTruth;
    }

    public void setCloseTruth(boolean closeTruth) {
        this.closeTruth = closeTruth;
    }

    /**
     * One of the main computation method.
     * This must be called before any of the metric retrieval methods.
     * Only values in the TrainingMap are computed over.
     */
    public abstract void compute(TrainingMap data);

    /**
     * One of the main computation method.
     * This must be called before any of the metric retrieval methods.
     * Only values in the TrainingMap matching the given predicate are computed over.
     */
    public abstract void compute(TrainingMap data, StandardPredicate predicate);

    /**
     * The representative (rep) metric is the metric that was chosen to be the representative for this evaluator.
     * This metric is chosen via config.
     */
    public abstract double getRepMetric();

    /**
     * Is a higher value for the current representative metric better?
     */
    public abstract boolean isHigherRepBetter();

    /**
     * Combine getRepMetric() with isHigherRepBetter() so that higher values that come out of this method are always better.
     */
    public double getNormalizedRepMetric() {
        double value = getRepMetric();
        if (!isHigherRepBetter()) {
            value = -value;
        }

        return value;
    }

    /**
     * Combine getBestRepScore() with isHigherRepBetter() so that the values from getNormalizedRepMetric are always
     * less than or equal to this value.
     */
    public double getNormalizedMaxRepMetric() {
        double value = getBestRepScore();
        if (!isHigherRepBetter()) {
            value = -value;
        }

        return value;
    }

    /**
     * The maximum (i.e. the best) value that the representative (rep) can take for this evaluator.
     */
    public abstract double getBestRepScore();

    /**
     * Get a string that contains the full range of stats that this Evaluator can provide.
     * compute() should have been called first.
     */
    public abstract String getAllStats();

    /**
     * A convenience call for those who don't want to create a training map directly.
     * If the random variable database is already fully cached
     * (ie a PAM has already been used on it (like if it has been used in inference))
     * then don't rebuild the cache.
     */
    public void compute(Database rvDB, Database truthDB, StandardPredicate predicate, boolean rvDBCached) {
        PersistedAtomManager atomManager = new PersistedAtomManager(rvDB, rvDBCached);
        TrainingMap map = new TrainingMap(atomManager, truthDB);
        compute(map, predicate);
    }

    public void compute(Database rvDB, Database truthDB, StandardPredicate predicate) {
        compute(rvDB, truthDB, predicate, false);
    }

    /**
     * Get the full mapping of target atoms to truth atoms.
     * What constitutes a full mapping depends on includeObserved and closeTruth.
     *
     * Note that certain configurations may result in truth results being returned that include an UnmanagedAtom
     * (atoms that are not managed by an atom manager).
     * This is not an inherently bad or erroneous situation, the caller should just be concious of this.
     */
    public Iterable<Map.Entry<GroundAtom, GroundAtom>> getMap(TrainingMap trainingMap) {
        @SuppressWarnings("unchecked")
        Iterable<Map.Entry<GroundAtom, GroundAtom>> map = (Iterable)(trainingMap.getLabelMap().entrySet());

        if (includeObserved) {
            @SuppressWarnings("unchecked")
            Iterable<Map.Entry<GroundAtom, GroundAtom>> observedMap = (Iterable)(trainingMap.getObservedMap().entrySet());
            map = IteratorUtils.join(map, observedMap);
        }

        if (closeTruth) {
            @SuppressWarnings("unchecked")
            Iterable<GroundAtom> latentAtoms = (Iterable)trainingMap.getLatentVariables();

            Iterable<Map.Entry<GroundAtom, GroundAtom>> latentMap =
                IteratorUtils.map(latentAtoms, new IteratorUtils.MapFunction<GroundAtom, Map.Entry<GroundAtom, GroundAtom>>() {
                    @Override public Map.Entry<GroundAtom, GroundAtom> map(GroundAtom atom) {
                        GroundAtom truthAtom = new UnmanagedAtom(atom.getPredicate(), atom.getArguments(), 0.0f);
                        return new AbstractMap.SimpleEntry<GroundAtom, GroundAtom>(atom, truthAtom);
                    }
                });
            map = IteratorUtils.join(map, latentMap);
        }

        return map;
    }

    /**
     * Get the full collection of target atoms.
     * What constitutes a full collection depends on includeObserved.
     */
    public Iterable<GroundAtom> getTargets(TrainingMap trainingMap) {
        if (includeObserved) {
            return trainingMap.getAllTargets();
        } else {
            @SuppressWarnings("unchecked")
            Iterable<GroundAtom> targets = (Iterable)trainingMap.getAllPredictions();
            return targets;
        }
    }
}
