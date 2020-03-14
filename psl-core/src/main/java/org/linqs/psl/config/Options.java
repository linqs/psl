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
package org.linqs.psl.config;

import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.statistics.CategoricalEvaluator;
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.evaluation.statistics.RankingEvaluator;
import org.linqs.psl.reasoner.admm.ADMMReasoner;

import org.json.JSONArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The canonical place to keep all configuration options.
 * Options.fetchOptions() can be used to fetch all the options dynamically.
 * The main() method will collect all the options and write them out to stdout as JSON.
 */
public class Options {
    public static final Option ADMM_COMPUTE_PERIOD = new Option(
        "admmreasoner.computeperiod",
        50,
        "Compute some stats about the optimization and log them to TRACE once for each period."
        + " Note that gathering the information takes about an iteration's worth of time.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option ADMM_EPSILON_ABS = new Option(
        "admmreasoner.epsilonabs",
        1e-5f,
        "Absolute error component of stopping criteria.",
        Option.FLAG_POSITIVE
    );

    public static final Option ADMM_EPSILON_REL = new Option(
        "admmreasoner.epsilonrel",
        1e-3f,
        "Relative error component of stopping criteria.",
        Option.FLAG_POSITIVE
    );

    public static final Option ADMM_INITIAL_CONSENSUS_VALUE = new Option(
        "admmreasoner.initialconsensusvalue",
        ADMMReasoner.InitialValue.RANDOM.toString(),
        "The starting value for consensus variables (see ADMMReasoner.InitialValue)."
    );

    public static final Option ADMM_INITIAL_LOCAL_VALUE = new Option(
        "admmreasoner.initiallocalvalue",
        ADMMReasoner.InitialValue.RANDOM.toString(),
        "The starting value for local variables (see ADMMReasoner.InitialValue)."
    );

    public static final Option ADMM_MAX_ITER = new Option(
        "admmreasoner.maxiterations",
        25000,
        "The maximum number of iterations of ADMM to perform in a round of inference.",
        Option.FLAG_POSITIVE
    );

    public static final Option ADMM_OBJECTIVE_BREAK = new Option(
        "admmreasoner.objectivebreak",
        true,
        "Stop if the objective has not changed since the last logging period (see ADMM_COMPUTE_PERIOD)."
    );

    public static final Option ADMM_STEP_SIZE = new Option(
        "admmreasoner.stepsize",
        1.0f,
        "The size of steps to take for a random variable every iteration."
        + " Should be positive.",
        Option.FLAG_POSITIVE
    );

    public static final Option EVAL_CAT_CATEGORY_INDEXES = new Option(
        "categoricalevaluator.categoryindexes",
        "1",
        "The indexes (zero-indexed) of arguments in the predicate that indicate a category."
        + " The other arguments will be treaded as identifiers."
    );

    public static final Option EVAL_CAT_DEFAULT_PREDICATE = new Option(
        "categoricalevaluator.defaultpredicate",
        null,
        "The default predicate to use when none are supplied."
    );

    public static final Option EVAL_CAT_REPRESENTATIVE = new Option(
        "categoricalevaluator.representative",
        CategoricalEvaluator.RepresentativeMetric.ACCURACY.toString(),
        "The representative metric (see CategoricalEvaluator.RepresentativeMetric)."
    );

    public static final Option EVAL_CONT_REPRESENTATIVE = new Option(
        "continuousevaluator.representative",
        ContinuousEvaluator.RepresentativeMetric.MSE.toString(),
        "The representative metric (see Continuousevaluator.RepresentativeMetric)."
    );

    public static final Option EVAL_DISCRETE_REPRESENTATIVE = new Option(
        "discreteevaluator.representative",
        DiscreteEvaluator.RepresentativeMetric.F1.toString(),
        "The representative metric (see DiscreteEvaluator.RepresentativeMetric)."
    );

    public static final Option EVAL_DISCRETE_THRESHOLD = new Option(
        "discreteevaluator.threshold",
        0.5,
        "The truth threshold."
    );

    public static final Option EVAL_RANKING_REPRESENTATIVE = new Option(
        "rankingevaluator.representative",
        RankingEvaluator.RepresentativeMetric.AUROC.toString(),
        "The representative metric (see RankingEvaluator.RepresentativeMetric)."
    );

    public static final Option EVAL_RANKING_THRESHOLD = new Option(
        "rankingevaluator.threshold",
        0.5,
        "The truth threshold."
    );

    // Static only.
    private Options() {}

    /**
     * Reflexivley parse the options from this class.
     * Keys are suffixed with "_KEY",
     * default values are suffixed with "_DEFAULT",
     * and descriptions are suffixed with "_DESCRIPTION".
     */
    public static List<Option> fetchOptions() throws IllegalAccessException {
        List<Option> options = new ArrayList<Option>();

        for (Field field : Options.class.getFields()) {
            // We only care about public static fields.
            if ((field.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) == 0) {
                continue;
            }

            // We only want Option variables.
            if (field.getType() != Option.class) {
                continue;
            }

            options.add((Option)field.get(null));
        }

        return options;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws IllegalAccessException {
        JSONArray json = new JSONArray();

        for (Option option : fetchOptions()) {
            json.put(option.toJSON());
        }

        System.out.println(json.toString(4));
    }
}
