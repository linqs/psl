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
package org.linqs.psl.evaluation;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.predicate.StandardPredicate;

/**
 * A container that represents a desired evaluation instance.
 */
public class EvaluationInstance {
    private StandardPredicate predicate;
    private Evaluator evaluator;
    private boolean primary;

    public EvaluationInstance(StandardPredicate predicate, Evaluator evaluator, boolean primary) {
        this.predicate = predicate;
        this.evaluator = evaluator;
        this.primary = primary;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public StandardPredicate getPredicate() {
        return predicate;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public void compute(TrainingMap trainingMap) {
        evaluator.compute(trainingMap, predicate);
    }

    public void compute(Database targets, Database truth) {
        evaluator.compute(targets, truth, predicate);
    }

    public double getNormalizedRepMetric() {
        return evaluator.getNormalizedRepMetric();
    }

    public double getNormalizedMaxRepMetric() {
        return evaluator.getNormalizedMaxRepMetric();
    }

    public String getOutput() {
        return String.format("Evaluator: %s, Predicate: %s, Results -- %s",
                evaluator.getClass().getSimpleName(), predicate.getName(), evaluator.getAllStats());
    }

    public String toString() {
        return String.format("Evaluator: %s, Predicate: %s", evaluator.getClass().getSimpleName(), predicate.getName());
    }
}
