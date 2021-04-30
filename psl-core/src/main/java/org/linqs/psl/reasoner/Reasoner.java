/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.reasoner;

import org.linqs.psl.config.Options;
import org.linqs.psl.reasoner.term.TermStore;

/**
 * An oprimizer to minimize the total weighted incompatibility
 * of the terms provided by a TermStore.
 */
public abstract class Reasoner {
    protected double budget;

    protected boolean objectiveBreak;
    protected boolean runFullIterations;

    protected float tolerance;

    public Reasoner() {
        budget = 1.0;

        objectiveBreak = Options.REASONER_OBJECTIVE_BREAK.getBoolean();
        runFullIterations = Options.REASONER_RUN_FULL_ITERATIONS.getBoolean();

        tolerance = Options.REASONER_TOLERANCE.getFloat();
    }

    /**
     * Minimizes the total weighted incompatibility of the terms in the provided TermStore.
     * @return the objective the reasoner uses.
     */
    public abstract double optimize(TermStore termStore);

    /**
     * Releases all resources acquired by this Reasoner.
     */
    public abstract void close();

    /**
     * Set a budget (given as a proportion of the max budget).
     */
    public void setBudget(double budget) {
        this.budget = budget;
    }
}
