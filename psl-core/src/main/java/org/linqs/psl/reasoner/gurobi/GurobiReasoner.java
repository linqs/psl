/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
package org.linqs.psl.reasoner.gurobi;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.gurobi.term.GurobiObjectiveTerm;
import org.linqs.psl.reasoner.gurobi.term.GurobiTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gurobi.GRBException;
import gurobi.GRBModel;

import java.util.List;

public class GurobiReasoner extends Reasoner<GurobiObjectiveTerm> {
    private static final Logger log = LoggerFactory.getLogger(GurobiReasoner.class);

    /**
     * The Gurobi method to use.
     * See <a href="https://www.gurobi.com/documentation/current/refman/method.html">the Gurobi documentation</a>
     * for more details.
     */
    public static enum GurobiMethod {
        AUTO,
        PRIMAL_SIMPLEX,
        DUAL_SIMPLEX,
        BARRIER,
        CONCURRENT,
        DETERMINISTIC_CONCURRENT,
        DETERMINISTIC_CONCURRENT_SIMPLEX
    }

    private final int workLimit;
    private final GurobiMethod method;


    public GurobiReasoner() {
        super();

        workLimit = Options.GUROBI_WORK_LIMIT.getInt();
        method = GurobiMethod.valueOf(Options.GUROBI_METHOD.getString().toUpperCase());
    }

    @Override
    public double optimize(TermStore<GurobiObjectiveTerm> termStore, List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        assert termStore instanceof GurobiTermStore;

        termStore.initForOptimization();
        int numAtoms = termStore.getNumVariables();

        ObjectiveResult objectiveResult = null;

        boolean hasDecisionVariables = false;
        GroundAtom[] variableAtoms = termStore.getAtomStore().getAtoms();
        for (int i = 0; i < numAtoms; i++) {
            if (!variableAtoms[i].isFixed()) {
                hasDecisionVariables = true;
                break;
            }
        }

        if (!hasDecisionVariables) {
            log.trace("No random variable atoms to optimize.");
            objectiveResult = parallelComputeObjective(termStore);
            return objectiveResult.objective;
        }

        long start = System.currentTimeMillis();

        log.trace("Building Gurobi model.");
        GRBModel model = ((GurobiTermStore) termStore).getModel();
        log.trace("Gurobi model built.");

        try {
            model.set("WorkLimit", Integer.toString(workLimit));
            model.set("Method", getMethodId());
            model.optimize();
            model.update();
        } catch (GRBException e) {
            throw new RuntimeException("Gurobi Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }

        ((GurobiTermStore) termStore).syncModelVariables();

        objectiveResult = parallelComputeObjective(termStore);

        optimizationComplete(termStore, objectiveResult, System.currentTimeMillis() - start);

        return objectiveResult.objective;
    }

    /**
     * Map the Gurobi method to its ID.
     */
    private String getMethodId() {
        switch (method) {
            case AUTO:
                return "-1";
            case PRIMAL_SIMPLEX:
                return "0";
            case DUAL_SIMPLEX:
                return "1";
            case BARRIER:
                return "2";
            case CONCURRENT:
                return "3";
            case DETERMINISTIC_CONCURRENT:
                return "4";
            case DETERMINISTIC_CONCURRENT_SIMPLEX:
                return "5";
            default:
                throw new IllegalArgumentException("Unrecognized Gurobi method: " + method);
        }
    }
}
