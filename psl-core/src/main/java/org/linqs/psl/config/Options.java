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

import org.linqs.psl.application.inference.mpe.ADMMInference;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.application.learning.weight.search.bayesian.GaussianProcessKernel;
import org.linqs.psl.database.rdbms.QueryRewriter;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.statistics.CategoricalEvaluator;
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.evaluation.statistics.RankingEvaluator;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.ADMMTermGenerator;
import org.linqs.psl.util.SystemUtils;

import org.json.JSONArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.Math;
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
        1e-5,
        "Absolute error component of stopping criteria.",
        Option.FLAG_POSITIVE
    );

    public static final Option ADMM_EPSILON_REL = new Option(
        "admmreasoner.epsilonrel",
        1e-3,
        "Relative error component of stopping criteria.",
        Option.FLAG_POSITIVE
    );

    public static final Option ADMM_MAX_ITER = new Option(
        "admmreasoner.maxiterations",
        25000,
        "The maximum number of iterations of ADMM to perform in a round of inference.",
        Option.FLAG_POSITIVE
    );

    public static final Option ADMM_STEP_SIZE = new Option(
        "admmreasoner.stepsize",
        1.0f,
        "The size of steps to take for a random variable every iteration."
        + " Should be positive.",
        Option.FLAG_POSITIVE
    );

    public static final Option BOOLEAN_MAXWALKSAT_MAX_FLIPS = new Option(
        "booleanmaxwalksat.maxflips",
        50000,
        "The maximum number of flips to try during optimization.",
        Option.FLAG_POSITIVE
    );

    public static final Option BOOLEAN_MAXWALKSAT_NOISE = new Option(
        "booleanmaxwalksat.noise",
        0.01,
        "The probability of randomly perturbing an atom in a randomly chosen potential.",
        Option.FLAG_POSITIVE
    );

    public static final Option BOOLEAN_MCSAT_NUM_BURNIN = new Option(
        "booleanmcsat.numburnin",
        500,
        "Number of burn-in samples.",
        Option.FLAG_POSITIVE
    );

    public static final Option BOOLEAN_MCSAT_NUM_SAMPLES = new Option(
        "booleanmcsat.numsamples",
        2500,
        "Length of the Markov chain.",
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

    public static final Option WLA_CRGS_MAX_LOCATIONS = new Option(
        "continuousrandomgridsearch.maxlocations",
        250,
        "The max number of locations to search.",
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

    public static final Option EVAL_CLOSE_TRUTH = new Option(
        "eval.closetruth",
        false,
        "Include in evaluation latent target atoms (using the closed world assumption for truth atoms)."
    );

    public static final Option EVAL_INCLUDE_OBS = new Option(
        "eval.includeobs",
        false,
        "Include in evaluation observed target atoms that match against a truth atom."
    );

    public static final Option EXECUTABLE_CLEAN_INPUT = new Option(
        "executablereasoner.cleanupinput",
        true,
        "Whether to delete the input file to the external reasoner on close."
    );

    public static final Option EXECUTABLE_CLEAN_OUTPUT = new Option(
        "executablereasoner.cleanupoutput",
        true,
        "Whether to delete the output file from the external reasoner on close."
    );

    public static final Option EXECUTABLE_REASONER_PATH = new Option(
        "executablereasoner.executablepath",
        "",
        "The path for the reasoner executable to invoke."
    );

    public static final Option GIT_COMMIT_SHORT = new Option(
        "git.commit.id.abbrev",
        "xxxxxxx",
        "The git commit of the current build of PSL."
    );

    public static final Option GIT_DIRTY = new Option(
        "git.dirty",
        false,
        "Whether the current build of PSL was made in a dirty git repository."
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

    public static final Option WLA_GPP_USE_PROVIDED_WEIGHT = new Option(
        "gpp.useProvidedWeight",
        true,
        "Whether the weight configuration in the user provided model file should be used as the initial"
        + " sample point in GPP."
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

    public static final Option GROUNDING_REWRITE_QUERY = new Option(
        "grounding.rewritequeries",
        false,
        "Potentially rewrite the grounding queries."
    );

    public static final Option GROUNDING_SERIAL = new Option(
        "grounding.serial",
        false,
        "Whether or not queries are being rewritten, perform the grounding queries one at a time."
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

    public static final Option INFERENCE_GRS = new Option(
        "inference.groundrulestore",
        MemoryGroundRuleStore.class.getName(),
        "The ground rule store to use for inference."
    );

    public static final Option INFERENCE_INITIAL_VARIABLE_VALUE = new Option(
        "inference.initialvalue",
        InitialValue.RANDOM.toString(),
        "The starting value for atoms and any local variables during inference."
    );

    public static final Option INFERENCE_REASONER = new Option(
        "inference.reasoner",
        ADMMReasoner.class.getName(),
        "The reasoner to use for inference."
    );

    public static final Option INFERENCE_NORMALIZE_WEIGHTS = new Option(
        "inference.normalize",
        true,
        "Normalize weights to be in [0, 1]. Normalization will be done by dividing all weights by the largest weight."
    );

    public static final Option INFERENCE_RELAX = new Option(
        "inference.relax",
        false,
        "Relax hard constraints into soft ones."
    );

    public static final Option INFERENCE_RELAX_MULTIPLIER = new Option(
        "inference.relax.multiplier",
        100,
        "When relaxing a hard constraint into a soft one, the weight of the rule is set to this value times the largest weight seen.",
        Option.FLAG_POSITIVE
    );

    public static final Option INFERENCE_RELAX_SQUARED = new Option(
        "inference.relax.squared",
        true,
        "When relaxing a hard constraint into a soft one, this determines if the resulting weighted rule is squared."
    );

    public static final Option INFERENCE_TG = new Option(
        "inference.termgenerator",
        ADMMTermGenerator.class.getName(),
        "The term generator to use for inference."
    );

    public static final Option INFERENCE_TS = new Option(
        "inference.termstore",
        ADMMTermStore.class.getName(),
        "The term store to use for inference."
    );

    public static final Option WLA_IWHB_WLA = new Option(
        "initialweighthyperband.internalwla",
        MaxLikelihoodMPE.class.getName(),
        "The internal weight learning application (WLA) to use (should be a VotedPerceptron)."
    );

    public static final Option LAM_ACTIVATION_THRESHOLD = new Option(
        "lazyatommanager.activation",
        0.01,
        "The minimum value an atom must take for it to be activated.",
        Option.FLAG_POSITIVE | Option.FLAG_LTE_ONE
    );

    public static final Option WLA_LMLE_MAX_ROUNDS = new Option(
        "lazymaxlikelihoodmpe.maxgrowrounds",
        100,
        "The maximum number of rounds of lazy growing.",
        Option.FLAG_POSITIVE
    );

    public static final Option LAZY_INFERENCE_MAX_ROUNDS = new Option(
        "lazympeinference.maxrounds",
        100,
        "The maximum number of rounds of lazy inference.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_MPPLE_NUM_SAMPLES = new Option(
        "maxpiecewisepseudolikelihood.numsamples",
        100,
        "The number of samples MPPLE will use to approximate expectations.",
        Option.FLAG_POSITIVE
    );

    public static final Option MEMORY_TS_INITIAL_SIZE = new Option(
        "memorytermstore.initialsize",
        10000l,
        "The initial size for the memory store.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MEMORY_VTS_DEFAULT_SIZE = new Option(
        "memoryvariabletermstore.defaultsize",
        1000,
        "The default size in terms of number of variables.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MEMORY_VTS_SHUFFLE = new Option(
        "memoryvariabletermstore.shuffle",
        true,
        "Shuffle the terms before each return of iterator()."
    );

    public static final Option MODEL_PREDICATE_BATCH_SIZE = new Option(
        "modelpredicate.batchsize",
        32,
        "The size of batches for model updates.",
        Option.FLAG_POSITIVE
    );

    public static final Option MODEL_PREDICATE_ENTITY_ARGS = new Option(
        "modelpredicate.entityargs",
        "0",
        "A comma separated list of indexes to the predicate arguments that identity the data point (as opposed to the target label)."
    );

    public static final Option MODEL_PREDICATE_ITERATIONS = new Option(
        "modelpredicate.iterations",
        100,
        "The number of iterations for the internal model to go through for updates.",
        Option.FLAG_POSITIVE
    );

    public static final Option MODEL_PREDICATE_LABEL_ARGS = new Option(
        "modelpredicate.labelargs",
        "1",
        "A comma separated list of indexes to the predicate arguments that identity the target label (as opposed to the identity of the data point)."
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

    public static final Option PARALLEL_NUM_THREADS = new Option(
        "parallel.numthreads",
        Runtime.getRuntime().availableProcessors(),
        "The number of threads to use for parallel tasks.",
        Option.FLAG_POSITIVE
    );

    public static final Option PAM_THROW_ACCESS_EXCEPTION = new Option(
        "persistedatommanager.throwaccessexception",
        true,
        "Whether or not to throw an exception on illegal access."
        + " Note that in most cases, this indicates incorrectly formed data."
        + " This should only be set to false when the user understands why these"
        + " exceptions are thrown in the first place and the grounding implications of"
        + " not having the atom initially in the database."
    );

    public static final Option POSTGRES_HOST = new Option(
        "postgres.host",
        "localhost",
        "The Postgres host to connect to (when not explicitly specified)."
    );

    public static final Option POSTGRES_PASSWORD = new Option(
        "postgres.password",
        "",
        "The Postgres password to connect with (when not explicitly specified)."
    );

    public static final Option POSTGRES_PORT = new Option(
        "postgres.port",
        "5432",
        "The Postgres port to connect to (when not explicitly specified)."
    );

    public static final Option POSTGRES_STATS_PERCENTAGE = new Option(
        "postgres.statspercentage",
        0.25,
        "The percentage of possible effort to have Postgres spend on statistics collection.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option POSTGRES_USER = new Option(
        "postgres.user",
        "",
        "The Postgres user to connect with (when not explicitly specified)."
    );

    public static final Option PROJECT_VERSION = new Option(
        "project.version",
        "UNKNOWN",
        "The current version of PSL."
    );

    public static final Option QR_ALLOWED_STEP_INCREASE = new Option(
        "queryrewriter.allowedsteocostincrease",
        1.5,
        "How much we allow the query cost (number of rows) to increase at each step."
    );

    public static final Option QR_ALLOWED_TOTAL_INCREASE = new Option(
        "queryrewriter.allowedtotalcostincrease",
        2.0,
        "How much we allow the query cost (number of rows) to for new plans."
    );

    public static final Option QR_COST_ESTIMATOR = new Option(
        "queryrewriter.costestimator",
        QueryRewriter.CostEstimator.HISTOGRAM.toString(),
        "The method to use when estimating join size."
    );

    public static final Option RANDOM_SEED = new Option(
        "random.seed",
        (long)(Math.random() * 100000000l),
        "The random seed to use for PSL."
        + " If not explicitly set, the seed is chosen by the system."
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

    public static final Option RDBMS_FETCH_SIZE = new Option(
        "rdbmsdatabase.fetchsize",
        500,
        "The number of records to fetch from the database at a time.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option REASONER_OBJECTIVE_BREAK = new Option(
        "reasoner.objectivebreak",
        true,
        "Stop if the objective has not changed since the last iteration (or logging period)."
    );

    public static final Option REASONER_PRINT_INITIAL_OBJECTIVE = new Option(
        "reasoner.printinitialobj",
        false,
        "Print the objective before any optimization."
        + " Note that this will require a pass through all the terms,"
        + " and therefore may affect performance."
        + " Has no effect if logging is not set to TRACE."
    );

    public static final Option REASONER_RUN_FULL_ITERATIONS = new Option(
        "reasoner.runfulliterations",
        false,
        "Ignore all other stopping criteria and run until the maximum number of iterations."
    );

    public static final Option REASONER_TOLERANCE = new Option(
        "reasoner.tolerance",
        1e-5f,
        "How close towo objective values need to be to be considered the same.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option RUNTIME_STATS_COLLECT = new Option(
        "runtimestats.collect",
        false,
        "Whether to periodically collect stats on the JVM."
    );

    public static final Option RUNTIME_COLLECTION_PERIOD = new Option(
        "runtimestats.period",
        250l,
        "The period (in ms) of stats collection."
    );

    public static final Option WLA_SEARCH_DIRICHLET = new Option(
        "search.dirichlet",
        true,
        "Whether or not to perform search based weight learning using Dirichlet distributed weights."
        + " Note that setting this option to false will increase the likelihood of repeated weight configuration samples."
    );

    public static final Option WLA_SEARCH_DIRICHLET_ALPHA = new Option(
        "search.dirichletalpha",
        0.05,
        "The alpha parameter for the dirichlet distribution of the weight sampler."
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

    public static final Option SGD_MOVEMENT = new Option(
        "sgd.movement",
        true,
        "Keep track of the mean movement of the random variables. Do not stop optimization if that value is greater than some threshold."
    );

    public static final Option SGD_MOVEMENT_THRESHOLD = new Option(
        "sgd.movement.threshold",
        0.05f,
        "If movement watching is enabled, don't stop optimization if the mean random variable movement is greater than this threshold.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option STREAMING_TS_PAGE_LOCATION = new Option(
        "streamingtermstore.pagelocation",
        SystemUtils.getTempDir("streaimg_term_cache_pages"),
        "Where on disk to write term pages"
    );

    public static final Option STREAMING_TS_PAGE_SIZE = new Option(
        "streamingtermstore.pagesize",
        10000,
        "The number of terms in a single page.",
        Option.FLAG_POSITIVE
    );

    public static final Option STREAMING_TS_RANDOMIZE_PAGE_ACCESS = new Option(
        "streamingtermstore.randomizepageaccess",
        true,
        "Whether to pick up pages in a random order."
    );

    public static final Option STREAMING_TS_SHUFFLE_PAGE = new Option(
        "streamingtermstore.shufflepage",
        true,
        "Whether to shuffle within a page when it is picked up."
    );

    public static final Option STREAMING_TS_WARN_RULES = new Option(
        "streamingtermstore.warnunsupportedrules",
        true,
        "Warn on rules the streaming term store can't handle."
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

    public static final Option WLA_RANDOM_WEIGHTS = new Option(
        "weightlearning.randomweights",
        false,
        "Randomize weights before running."
        + " The randomization will happen during ground model initialization."
    );

    public static final Option WLA_INFERENCE = new Option(
        "weightlearning.inference",
        ADMMInference.class.getName(),
        "The inference application used during weight learning."
    );

    private static List<Option> additionalOptions = new ArrayList<Option>();

    // Static only.
    private Options() {}

    public static void addOption(Option option) {
        additionalOptions.add(option);
    }

    /**
     * Reflexively parse the options from this class.
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

        for (Option option : additionalOptions) {
            json.put(option.toJSON());
        }

        System.out.println(json.toString(4));
    }
}
