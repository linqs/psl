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
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.model.predicate.StandardPredicate;

/**
 * Compute some metric (or set of matrics) for some predicted and labeled data.
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
    /**
     * One of the main computation method.
     * This must be called before any of the metric retrival methods.
     * Only values in the TrainingMap are computed over.
     */
    public abstract void compute(TrainingMap data);

    /**
     * One of the main computation method.
     * This must be called before any of the metric retrival methods.
     * Only values in the TrainingMap matching the given predicate are computed over.
     */
    public abstract void compute(TrainingMap data, StandardPredicate predicate);

    public abstract double getRepresentativeMetric();

    public abstract boolean isHigherRepresentativeBetter();

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
}
