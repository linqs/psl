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

import org.linqs.psl.application.learning.weight.bayesian.GaussianProcessKernel;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.statistics.CategoricalEvaluator;
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.evaluation.statistics.RankingEvaluator;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.ADMMTermGenerator;

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
        + " The other arguments will be treated as identifiers."
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

    public static final Option WLA_CRGS_BASE_WEIGHT = new Option(
        "continuousrandomgridsearch.baseweight",
        0.40,
        "The base weight of a rule."
        + " The exact use of this value depends on UNIFORM_BASE."
        + " This will either be used as the mean of the Gaussian from which a weight will be sampled,"
        + " or as the smallest weight that all rules will be started from.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_CRGS_MAX_LOCATIONS = new Option(
        "continuousrandomgridsearch.maxlocations",
        250,
        "The max number of locations to search.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_CRGS_SCALE_ORDERS = new Option(
        "continuousrandomgridsearch.scaleorders",
        0,
        "If greater than 0, then various different scaled versions of the weights will be tested."
        + " For example, if set to 3 then 10x, 100x, and 1000x will also be tested."
        + " These additional tests DO NOT count against MAX_LOCATIONS_KEY,"
        + " i.e. MAX_LOCATIONS_KEY * (SCALE_ORDERS_KEY + 1) configurations will be tested.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_CRGS_UNIFORM_BASE = new Option(
        "continuousrandomgridsearch.uniformbase",
        true,
        "If true, then use the same base weight as the Gaussian's mean when sampling the weight."
        + " Otherwise, use different base weights depending on the inital satisfaction of each rule."
    );

    public static final Option WLA_CRGS_VARIANCE = new Option(
        "continuousrandomgridsearch.variance",
        0.20,
        "The variance used when sampling the weights from a Gaussian.",
        Option.FLAG_POSITIVE
    );

    public static final Option DCD_C = new Option(
        "dcd.C",
        10.0f,
        null,
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option DCD_MAX_ITER = new Option(
        "dcd.maxiterations",
        200,
        "The maximum number of iterations of DCD to perform in a round of inference.",
        Option.FLAG_POSITIVE
    );

    public static final Option DCD_OBJECTIVE_BREAK = new Option(
        "dcd.objectivebreak",
        true,
        "Stop if the objective has not changed since the last iteration."
    );

    public static final Option DCD_PRINT_INITIAL_OBJECTIVE = new Option(
        "dcd.printinitialobj",
        false,
        "Print the objective before any optimization."
        + " Note that this will require a pass through all the terms,"
        + " and therefore may affect performance."
        + " Has no effect if dcd.printobj is false."
    );

    public static final Option DCD_PRINT_OBJECTIVE = new Option(
        "dcd.printobj",
        true,
        null
    );

    public static final Option DCD_TOLERANCE = new Option(
        "dcd.tolerance",
        1e-6f,
        null,
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option DCD_TRUNCATE_EVERY_STEP = new Option(
        "dcd.truncateeverystep",
        false,
        null
    );

    public static final Option EVAL_DISCRETE_REPRESENTATIVE = new Option(
        "discreteevaluator.representative",
        DiscreteEvaluator.RepresentativeMetric.F1.toString(),
        "The representative metric (see DiscreteEvaluator.RepresentativeMetric)."
    );

    public static final Option EVAL_DISCRETE_THRESHOLD = new Option(
        "discreteevaluator.threshold",
        0.5,
        "The truth threshold.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_EM_ITERATIONS = new Option(
        "em.iterations",
        10,
        "The number of iterations of expectation maximization to perform.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_EM_TOLERANCE = new Option(
        "em.tolerance",
        1e-3,
        "The minimum absolute change in weights such that EM is considered converged.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GPP_EARLY_STOPPING = new Option(
        "gpp.earlyStopping",
        true,
        null
    );

    public static final Option WLA_GPP_EXPLORATION = new Option(
        "gpp.explore",
        2.0f,
        null,
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GPP_INITIAL_WEIGHT_STD = new Option(
        "gpp.initialweightstd",
        1.0f,
        null,
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GPP_INITIAL_WEIGHT_VALUE = new Option(
        "gpp.initialweightvalue",
        0.0f,
        null,
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_GPP_KERNEL = new Option(
        "gpp.kernel",
        GaussianProcessKernel.KernelType.SQUARED_EXP.toString(),
        null
    );

    public static final Option WLA_GPP_MAX_CONFIGS = new Option(
        "gpp.maxconfigs",
        1000000,
        null,
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GPP_MAX_ITERATIONS = new Option(
        "gpp.maxiterations",
        25,
        null,
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GPP_RANDOM_CONFIGS_ONLY = new Option(
        "gpp.randomConfigsOnly",
        true,
        null
    );

    public static final Option WLA_GPP_KERNEL_REL_DEP = new Option(
        "gppker.reldep",
        1.0f,
        "Smaller means longer dependence. Smaller better when number of rules large."
    );

    public static final Option WLA_GPP_KERNEL_SCALE = new Option(
        "gppker.scale",
        1.0f,
        "The kernel scale for GaussianProcessKernel."
    );

    public static final Option WLA_GPP_KERNEL_SPACE = new Option(
        "gppker.space",
        GaussianProcessKernel.Space.SS.toString(),
        "The search space for a GaussianProcessKernel."
    );

    public static final Option WLA_GS_POSSIBLE_WEIGHTS = new Option(
        "gridsearch.weights",
        "0.001:0.01:0.1:1:10",
        "A comma-separated list of possible weights. These weights should be in some sorted order."
    );

    public static final Option WLA_GRGS_EXPLORE_LOCATIONS = new Option(
        "guidedrandomgridsearch.explorelocations",
        10,
        "The number of initial seed locations to explore based off of whichever ones score the best.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GRGS_SEED_LOCATIONS = new Option(
        "guidedrandomgridsearch.seedlocations",
        25,
        "The number of locations to initially search.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_HEM_ADAGRAD = new Option(
        "hardem.adagrad",
        false,
        "Whether to use AdaGrad subgradient scaling, the adaptive subgradient algorithm of Duchi et al., 2010."
    );

    public static final Option WLA_HB_BRACKET_SIZE = new Option(
        "hyperband.basebracketsize",
        10,
        "The base number of weight configurations for each brackets.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_HB_NUM_BRACKETS = new Option(
        "hyperband.numbrackets",
        4,
        "The number of brackets to consider.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_HB_SURVIVAL = new Option(
        "hyperband.survival",
        4,
        "The proportion of configs that survive each round in a brancket.",
        Option.FLAG_POSITIVE
    );

    public static final Option HYPERPLANE_TG_INVERT_NEGATIVE_WEIGHTS = new Option(
        "hyperplanetermgenerator.invertnegativeweights",
        false,
        "If true, then invert negative weight rules into their positive weight counterparts."
    );

    public static final Option WLA_IWHB_WLA = new Option(
        "initialweighthyperband.internalwla",
        MaxLikelihoodMPE.class.getName(),
        "The internal weight learning application (WLA) to use (should be a VotedPerceptron)."
    );

    public static final Option WLA_LMLE_MAX_ROUNDS = new Option(
        "lazymaxlikelihoodmpe.maxgrowrounds",
        100,
        "The maximum number of rounds of lazy growing.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_MPPLE_NUM_SAMPLES = new Option(
        "maxpiecewisepseudolikelihood.numsamples",
        100,
        "The number of samples MPPLE will use to approximate expectations.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_MPLE_BOOL = new Option(
        "maxspeudolikelihood.bool",
        false,
        "If true, MaxPseudoLikelihood will treat RandomVariableAtoms as boolean valued."
        + " This restricts the types of constraints supported."
    );

    public static final Option WLA_MPLE_MIN_WIDTH = new Option(
        "maxspeudolikelihood.minwidth",
        1e-2,
        "Minimum width for bounds of integration.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_MPLE_NUM_SAMPLES = new Option(
        "maxspeudolikelihood.numsamples",
        10,
        "The number of samples MPLE will use to approximate the integrals in the marginal computation.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_PDL_ADMM_STEPS = new Option(
        "pairedduallearner.admmsteps",
        1,
        "The number of ADMM steps to run for each inner objective before each gradient iteration (parameter N in the ICML paper).",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_PDL_WARMUP_ROUNDS = new Option(
        "pairedduallearner.warmuprounds",
        0,
        "The number of rounds of paired-dual learning to run before beginning to update the weights (parameter K in the ICML paper).",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_RGS_MAX_LOCATIONS = new Option(
        "randomgridsearch.maxlocations",
        150,
        "The max number of locations to search.",
        Option.FLAG_POSITIVE
    );

    public static final Option EVAL_RANKING_REPRESENTATIVE = new Option(
        "rankingevaluator.representative",
        RankingEvaluator.RepresentativeMetric.AUROC.toString(),
        "The representative metric (see RankingEvaluator.RepresentativeMetric)."
    );

    public static final Option EVAL_RANKING_THRESHOLD = new Option(
        "rankingevaluator.threshold",
        0.5,
        "The truth threshold.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_RS_SCALING_FACTORS = new Option(
        "ranksearch.scalingfactors",
        "1:2:10:100",
        "A comma-separated list of scaling factors."
    );

    public static final Option SGD_LEARNING_RATE = new Option(
        "sgd.learningrate",
        1.0f,
        null,
        Option.FLAG_POSITIVE
    );

    public static final Option SGD_MAX_ITER = new Option(
        "sgd.maxiterations",
        200,
        "The maximum number of iterations of SGD to perform in a round of inference.",
        Option.FLAG_POSITIVE
    );

    public static final Option SGD_OBJECTIVE_BREAK = new Option(
        "sgd.objectivebreak",
        true,
        "Stop if the objective has not changed since the last iteration."
    );

    public static final Option SGD_PRINT_INITIAL_OBJECTIVE = new Option(
        "sgd.printinitialobj",
        false,
        "Print the objective before any optimization."
        + " Note that this will require a pass through all the terms,"
        + " and therefore may affect performance."
        + " Has no effect if sgd.printobj is false."
    );

    public static final Option SGD_PRINT_OBJECTIVE = new Option(
        "sgd.printobj",
        true,
        null
    );

    public static final Option SGD_TOLERANCE = new Option(
        "sgd.tolerance",
        1e-5f,
        null,
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_VP_AVERAGE_STEPS = new Option(
        "votedperceptron.averagesteps",
        false,
        "Whether to average all visited weights together for final output."
    );

    public static final Option WLA_VP_CLIP_NEGATIVE_WEIGHTS = new Option(
        "votedperceptron.clipnegativeweights",
        true,
        "If true, then weights will not be allowed to go negative."
    );

    public static final Option WLA_VP_CUT_OBJECTIVE = new Option(
        "votedperceptron.cutobjective",
        false,
        "If true, then cut the step size in half whenever the objective increases."
    );

    public static final Option WLA_VP_INERTIA = new Option(
        "votedperceptron.inertia",
        0.0,
        "The inertia that is used for adaptive step sizes.",
        Option.FLAG_NON_NEGATIVE | Option.FLAG_LT_ONE
    );

    public static final Option WLA_VP_L1 = new Option(
        "votedperceptron.l1regularization",
        0.0,
        "The L1 regularizer.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_VP_L2 = new Option(
        "votedperceptron.l2regularization",
        0.0,
        "The L2 regularizer.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_VP_NUM_STEPS = new Option(
        "votedperceptron.numsteps",
        25,
        "The number of steps VotedPerceptron will take.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_VP_SCALE_GRADIENT = new Option(
        "votedperceptron.scalegradient",
        true,
        "Whether to scale the gradient by the number of groundings."
    );

    public static final Option WLA_VP_STEP = new Option(
        "votedperceptron.stepsize",
        0.2,
        "The gradient step size.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_VP_SCALE_STEP = new Option(
        "votedperceptron.scalestepsize",
        true,
        "If true, then scale the step size down by the iteration."
    );

    public static final Option WLA_VP_ZERO_INITIAL_WEIGHTS = new Option(
        "votedperceptron.zeroinitialweights",
        false,
        "If true, then start all weights at zero."
    );

    public static final Option WLA_EVAL = new Option(
        "weightlearning.evaluator",
        ContinuousEvaluator.class.getName(),
        "The evaluator to use during weight learning."
        + " Not all weight learning methods will use the evaluator for decision making,"
        + " but even those will typically output an evaluator score each iteration."
    );

    public static final Option WLA_GRS = new Option(
        "weightlearning.groundrulestore",
        MemoryGroundRuleStore.class.getName(),
        "The ground rule storage to use during weight learning."
    );

    public static final Option WLA_RANDOM_WEIGHTS = new Option(
        "weightlearning.randomweights",
        false,
        "Randomize weights before running."
        + " The randomization will happen during ground model initialization."
    );

    public static final Option WLA_REASONER = new Option(
        "weightlearning.reasoner",
        ADMMReasoner.class.getName(),
        "The reasoner used for inference during weight learning."
    );

    public static final Option WLA_TG = new Option(
        "weightlearning.termgenerator",
        ADMMTermGenerator.class.getName(),
        "The term generator to use during weight learning."
    );

    public static final Option WLA_TS = new Option(
        "weightlearning.termstore",
        ADMMTermStore.class.getName(),
        "The term storage to use during weight learning."
    );

    // Static only.
    private Options() {}

    /**
     * Reflexively parse the options from this class.
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
