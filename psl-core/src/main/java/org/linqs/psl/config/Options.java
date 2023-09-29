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
package org.linqs.psl.config;

import org.linqs.psl.application.inference.mpe.ADMMInference;
import org.linqs.psl.application.learning.weight.gradient.GradientDescent;
import org.linqs.psl.application.learning.weight.search.bayesian.GaussianProcessKernel;
import org.linqs.psl.grounding.collective.CandidateGeneration;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.statistics.CategoricalEvaluator;
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.evaluation.statistics.AUCEvaluator;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.reasoner.gradientdescent.GradientDescentReasoner;
import org.linqs.psl.reasoner.sgd.SGDReasoner;
import org.linqs.psl.util.SystemUtils;

import org.json.JSONArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.Math;
import java.util.ArrayList;
import java.util.List;

/**
 * The canonical place to keep all configuration options.
 * Options.getOptions() can be used to fetch all the options dynamically.
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

    public static final Option ATOM_STORE_OVERALLOCATION_FACTOR = new Option(
        "atomstore.overallocation",
        0.20,
        "The degreee of overallocation for atom storage. 0.0 means no overallocation.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option ADMM_PRIMAL_DUAL_BREAK = new Option(
        "admmreasoner.primaldualbreak",
        true,
        "Stop ADMM when the primal dual stopping criterion is satisfied."
    );

    public static final Option ATOM_STORE_STORE_ALL_ATOMS = new Option(
        "atomstore.storeallatoms",
        false,
        "Store all seen atoms in the atom store, even unmanaged atoms."
    );

    public static final Option EVAL_CAT_CATEGORY_INDEXES = new Option(
        "categoricalevaluator.categoryindexes",
        "-1",
        "The indexes (zero-indexed, " + CategoricalEvaluator.DELIM + " separated)"
        + " of arguments in the predicate that indicate a category."
        + " The other arguments will be treated as identifiers."
        + " Negative indexes are accepted, with -1 referring to the last element."
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

    public static final Option DUAL_LCQP_COMPUTE_PERIOD = new Option(
        "duallcqp.computeperiod",
        10,
        "Compute some stats about the optimization to log and use them for stopping criterion once for each period.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option DUAL_LCQP_MAX_ITER = new Option(
        "duallcqp.maxiterations",
        5000,
        "The maximum number of iterations a Dual LCQP reasoner can take to perform inference.",
        Option.FLAG_POSITIVE
    );

    public static final Option DUAL_LCQP_PRIMAL_DUAL_BREAK = new Option(
        "duallcqp.primaldualbreak",
        true,
        "Stop the dual LCQP reasoner when the primal dual gap is less than duallcqp.primaldualthreshold."
    );

    public static final Option DUAL_LCQP_PRIMAL_DUAL_THRESHOLD = new Option(
        "duallcqp.primaldualthreshold",
        0.01,
        "Dual LCQP reasoners stop when the primal dual gap is less than this threshold.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option DUAL_LCQP_REGULARIZATION = new Option(
        "duallcqp.regularizationparameter",
        0.01,
        "The regularization parameter for the dual lcqp problem.",
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

    public static final Option WLA_GRADIENT_DESCENT_BATCH_GENERATOR = new Option(
        "gradientdescent.batchgenerator",
        "FullBatchGenerator",
        "The batch generator to use for gradient descent weight learning. The default is FullBatchGenerator."
    );

    public static final Option WLA_GRADIENT_DESCENT_CLIP_GRADIENT = new Option(
        "gradientdescent.clipweightgradient",
        true,
        "Clip weight gradients with a p norm greater than the maximum gradient magnitude."
    );

    public static final Option WLA_GRADIENT_DESCENT_EXTENSION = new Option(
    "gradientdescent.extension",
        GradientDescent.GDExtension.MIRROR_DESCENT.toString(),
        "The gradient descent extension to use for gradient descent weight learning."
        + " MIRROR_DESCENT (Default): Mirror descent / normalized exponentiated gradient descent over the unit simplex."
        + " If this option is chosen then gradientdescent.negativelogregularization must be positive."
        + " PROJECTED_GRADIENT: Projected gradient descent over the unit simplex."
        + " NONE: Gradient descent over non-negative orthant."
    );

    public static final Option WLA_GRADIENT_DESCENT_L2_REGULARIZATION = new Option(
        "gradientdescent.l2regularization",
        0.0f,
        "The L2 regularization parameter of gradient descent weight learning.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_MAX_GRADIENT = new Option(
        "gradientdescent.maxgradientmagnitude",
        25.0f,
        "Gradient with a magnitude larger than this value are clipped"
        + " to avoid overflow in gradient descent weight learning.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_MAX_GRADIENT_NORM = new Option(
        "gradientdescent.maxgradientnorm",
        Float.POSITIVE_INFINITY,
        "The p-norm used to measure the magnitude of gradients for clipping in gradient descent weight learning.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_LOG_REGULARIZATION = new Option(
        "gradientdescent.negativelogregularization",
        1.0f,
        "The negative log regularization parameter of gradient descent weight learning."
        + " If this is not 0.0 then mirror descent gradient extension must be used.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_ENTROPY_REGULARIZATION = new Option(
        "gradientdescent.negativeentropyregularization",
        10.0f,
        "The negative entropy regularization parameter of gradient descent weight learning."
        + " If this is not 0.0 then mirror descent gradient extension must be used.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_MOVEMENT_BREAK = new Option(
        "gradientdescent.movementbreak",
        true,
        "When the parameter movement between iterates is below the tolerance "
        + " set by gradientdescent.movementtolerance, gradient descent weight learning is stopped."
    );

    public static final Option WLA_GRADIENT_DESCENT_MOVEMENT_TOLERANCE = new Option(
        "gradientdescent.movementtolerance",
        1.0e-3f,
        "If gradientdescent.runfulliterations=false and gradientdescent.movementbreak=true,"
        + " then when the parameter movement between iterates is below this tolerance "
        + " gradient descent weight learning is stopped.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_NUM_STEPS = new Option(
        "gradientdescent.numsteps",
        500,
        "The number of steps the gradient descent weight learner will take.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_TRAINING_STOP_COMPUTE_PERIOD = new Option(
        "gradientdescent.stopcomputeperiod",
        10,
        "The period at which the gradient descent weight learner will measure the stopping criterion.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_CONNECTED_COMPONENT_BATCH_SIZE = new Option(
        "connectedcomponents.batchsize",
        32,
        "The number of connected components to include in a batch.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_RUN_FULL_ITERATIONS = new Option(
        "gradientdescent.runfulliterations",
        false,
        "Ignore all other stopping criteria and run until the maximum number of iterations"
        + " is reached for gradient descent weight learning."
    );

    public static final Option WLA_GRADIENT_DESCENT_SAVE_BEST_VALIDATION_WEIGHTS = new Option(
        "gradientdescent.savevalidationweights",
        false,
        "Save the weights that obtained the best validation evaluation."
        + " If true, then gradientdescent.runvalidation must be true."
    );


    public static final Option WLA_GRADIENT_DESCENT_SCALE_STEP = new Option(
        "gradientdescent.scalestepsize",
        true,
        "If true, then scale the step size by the iteration, i.e., at iteration k,"
        + " the step size is alpha / k, where alpha is the base step size of the gradient descent weight learner."
    );

    public static final Option WLA_GRADIENT_DESCENT_STEP_SIZE = new Option(
        "gradientdescent.stepsize",
        0.1f,
        "The gradient descent weight learner step size.",
        Option.FLAG_POSITIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_STOPPING_GRADIENT_NORM = new Option(
        "gradientdescent.stoppinggradientnorm",
        Float.POSITIVE_INFINITY,
        "The p-norm used to measure the magnitude of gradients for stopping criterion if gradientdescent.extension=NONE.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option WLA_GRADIENT_DESCENT_TRAINING_COMPUTE_PERIOD = new Option(
        "gradientdescent.trainingcomputeperiod",
        1,
        "Compute training evaluation every this many iterations of gradient descent weight learning."
    );

    public static final Option WLA_GRADIENT_DESCENT_FULL_MAP_EVALUATION_BREAK = new Option(
        "gradientdescent.trainingevaluationbreak",
        false,
        "Break gradient descent weight learning when the training evaluation stops improving."
    );

    public static final Option WLA_GRADIENT_DESCENT_FULL_MAP_EVALUATION_PATIENCE = new Option(
        "gradientdescent.trainingevaluationpatience",
        25,
        "Break gradient descent weight learning when the training evaluation stops improving after this many epochs."
    );

    public static final Option WLA_GRADIENT_DESCENT_VALIDATION_BREAK = new Option(
        "gradientdescent.validationbreak",
        false,
        "Break gradient descent weight learning when the validation evaluation stops improving."
    );

    public static final Option WLA_GRADIENT_DESCENT_VALIDATION_COMPUTE_PERIOD = new Option(
        "gradientdescent.validationcomputeperiod",
        1,
        "Compute validation evaluation every this many iterations of gradient descent weight learning."
    );

    public static final Option WLA_GRADIENT_DESCENT_VALIDATION_PATIENCE = new Option(
        "gradientdescent.validationpatience",
        25,
        "Break gradient descent weight learning when the validation evaluation stops improving after this many epochs."
    );

    public static final Option WLA_GS_POSSIBLE_WEIGHTS = new Option(
        "gridsearch.weights",
        "0.001:0.01:0.1:1:10",
        "A comma-separated list of possible weights. These weights should be in some sorted order."
    );

    public static final Option GROUNDING_COLLECTIVE = new Option(
        "grounding.collective",
        false,
        "Ground rules collectively instead of independently."
    );

    public static final Option GROUNDING_COLLECTIVE_BATCH_SIZE = new Option(
        "grounding.collective.batchsize",
        2500,
        "The batch size for grounding instantiation workers."
    );

    public static final Option GROUNDING_COLLECTIVE_CANDIDATE_COUNT = new Option(
        "grounding.collective.candidate.count",
        3,
        "The maximum number of candidates to generate per rule."
    );

    public static final Option GROUNDING_COLLECTIVE_CANDIDATE_SEARCH_BUDGET = new Option(
        "grounding.collective.candidate.search.budget",
        7,
        "How many explains the candidate search will use before choosing the final candidates."
    );

    public static final Option GROUNDING_COLLECTIVE_CANDIDATE_SEARCH_TYPE = new Option(
        "grounding.collective.candidate.search.type",
        CandidateGeneration.SearchType.BoundedDFS.toString(),
        "The type of search to use when generating candidates."
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
        "The proportion of configs that survive each round in a bracket.",
        Option.FLAG_POSITIVE
    );

    public static final Option INFERENCE_INITIAL_VARIABLE_VALUE = new Option(
        "inference.initialvalue",
        InitialValue.RANDOM.toString(),
        "The starting value for atoms and any local variables during inference."
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
        100.0f,
        "When relaxing a hard constraint into a soft one, the weight of the rule is set to this value times the largest weight seen.",
        Option.FLAG_POSITIVE
    );

    public static final Option INFERENCE_RELAX_SQUARED = new Option(
        "inference.relax.squared",
        false,
        "When relaxing a hard constraint into a soft one, this determines if the resulting weighted rule is squared."
    );

    public static final Option INFERENCE_SKIP_INFERENCE = new Option(
        "inference.skip",
        false,
        "Skip the reasoning portion of inference."
        + " Variables will be set to their specified initial values, but no reasoning will take place."
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

    public static final Option MINIMIZER_ENERGY_LOSS_COEFFICIENT = new Option(
        "minimizer.energylosscoefficient",
        1.0f,
        "The coefficient of the energy loss term in the augmented Lagrangian minimizer-based learning framework.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MINIMIZER_FINAL_PARAMETER_MOVEMENT_CONVERGENCE_TOLERANCE = new Option(
        "minimizer.finalparametermovementconvergencetolerance",
        0.01f,
        "Minimizer based learning is stopped when the amount of parameter movement drops below this tolerance.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MINIMIZER_INITIAL_LINEAR_PENALTY = new Option(
        "minimizer.initiallinearpenalty",
        0.1f,
        "The initial value for the linear penalty parameter in the augmented Lagrangian minimizer-based learning framework.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MINIMIZER_INITIAL_SQUARED_PENALTY = new Option(
        "minimizer.initialsquaredpenalty",
        10.0f,
        "The initial value for the squared penalty parameter in the augmented Lagrangian minimizer-based learning framework.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MINIMIZER_NUM_INTERNAL_ITERATIONS = new Option(
        "minimizer.numinternaliterations",
        100,
        "The number of internal iterations to perform before updating the augmented Lagrangian parameters.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MINIMIZER_OBJECTIVE_DIFFERENCE_TOLERANCE = new Option(
        "minimizer.objectivedifferencetolerance",
        0.01f,
        "The tolerance of the violation of value of the lower level objective function difference constraint"
        + " in the augmented Lagrangian minimizer-based learning framework.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MINIMIZER_PROX_VALUE_STEP_SIZE = new Option(
        "minimizer.proxvaluestepsize",
        0.01f,
        "The step size of the proximity values for the augmented inference subproblem.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MINIMIZER_PROX_RULE_WEIGHT = new Option(
        "minimizer.proxruleweight",
        0.01f,
        "The weight of the proximity rules added to the objective function for augmented inference subproblem.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MINIMIZER_SQUARED_PENALTY_INCREASE_RATE = new Option(
        "minimizer.squaredpenaltyincreaserate",
        2.0f,
        "The rate to increase the squared penalty coefficient.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option MODEL_PREDICATE_BATCH_SIZE = new Option(
        "modelpredicate.batchsize",
        32,
        "The maximum size of batches for model updates.",
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

    public static final Option PARALLEL_NUM_THREADS = new Option(
        "parallel.numthreads",
        Runtime.getRuntime().availableProcessors(),
        "The number of threads to use for parallel tasks.",
        Option.FLAG_POSITIVE
    );

    public static final Option PAM_THROW_ACCESS_EXCEPTION = new Option(
        "pam.throw",
        true,
        "Whether or not to throw an exception on illegal access to target atoms."
        + " Note that in most cases, this indicates incorrectly formed data."
        + " This should only be set to false when the user understands why these"
        + " exceptions are thrown and the grounding implications of"
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

    public static final Option POSTGRES_USER = new Option(
        "postgres.user",
        "",
        "The Postgres user to connect with (when not explicitly specified)."
    );

    public static final Option PREDICATE_DEEP_PYTHON_PORT = new Option(
        "predicate.deep.python.port",
        12345,
        "The port to connect to the Python model wrapper server.",
        Option.FLAG_POSITIVE
    );

    public static final Option PREDICATE_DEEP_PYTHON_WRAPPER_MODULE = new Option(
        "predicate.deep.python.module",
        "pslpython.deeppsl.server",
        "The Python module to invoke for the deep wrapper."
    );

    public static final Option PREDICATE_DEEP_SHARED_MEMORY_PATH = new Option(
        "predicate.deep.sharedmemory.path",
        SystemUtils.getTempDir("deep_shared_memory.bin"),
        "Where the place shared memory."
    );

    public static final Option PROJECT_VERSION = new Option(
        "project.version",
        "UNKNOWN",
        "The current version of PSL."
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

    public static final Option EVAL_AUC_REPRESENTATIVE = new Option(
        "aucevaluator.representative",
        AUCEvaluator.RepresentativeMetric.AUROC.toString(),
        "The representative metric (see AUCEvaluator.RepresentativeMetric)."
    );

    public static final Option EVAL_AUC_THRESHOLD = new Option(
        "aucevaluator.threshold",
        0.5,
        "The truth threshold.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option RDBMS_FETCH_SIZE = new Option(
        "rdbmsdatabase.fetchsize",
        500,
        "The number of records to fetch from the database at a time.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option REASONER_EVALUATE = new Option(
        "reasoner.evaluate",
        false,
        "If true, run the suite of evaluators specified for the post-inference evaluation stage at regular intervals during inference."
    );

    public static final Option GRADIENT_DESCENT_EXTENSION = new Option(
        "reasoner.gradientdescent.extension",
        GradientDescentReasoner.GradientDescentExtension.NONE.toString(),
        "The GD extension to use for GD reasoning."
        + " NONE (Default): The standard GD optimizer takes steps in the direction of the negative gradient scaled by the learning rate."
        + " MOMENTUM: Modify the descent direction with a momentum term."
        + " NESTEROV_ACCELERATION: Use the Nesterov accelerated gradient method."
    );

    public static final Option GRADIENT_DESCENT_FIRST_ORDER_BREAK = new Option(
        "reasoner.gradientdescent.firstorderbreak",
        true,
        "Stop gradient descent when the norm of the gradient is less than reasoner.gradientdescent.firstorderthreshold."
    );

    public static final Option GRADIENT_DESCENT_FIRST_ORDER_NORM = new Option(
        "reasoner.gradientdescent.firstordernorm",
        Float.POSITIVE_INFINITY,
        "The p-norm used to measure the first order optimality condition."
        + " Default is the infinity-norm which is the absolute value of the maximum component of the gradient vector."
        + " Note that the infinity-norm can be explicitly set with the string literal: 'Infinity'.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option GRADIENT_DESCENT_FIRST_ORDER_THRESHOLD = new Option(
        "reasoner.gradientdescent.firstorderthreshold",
        0.01f,
        "Gradient descent stops when the norm of the gradient is less than this threshold.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option GRADIENT_DESCENT_INVERSE_TIME_EXP = new Option(
        "reasoner.gradientdescent.inversescaleexp",
        1.0f,
        "If GradientDescent is using the STEPDECAY learning schedule, then this value is the negative"
        + " exponent of the iteration count which scales the gradient step using:"
        + " (learning_rate / ( iteration ^ - GRADIENT_DESCENT_INVERSE_TIME_EXP)).",
        Option.FLAG_POSITIVE
    );

    public static final Option GRADIENT_DESCENT_LEARNING_RATE = new Option(
        "reasoner.gradientdescent.learningrate",
        0.1f,
        "The learning rate for gradient descent inference.",
        Option.FLAG_POSITIVE
    );

    public static final Option GRADIENT_DESCENT_LEARNING_SCHEDULE = new Option(
        "reasoner.gradientdescent.learningschedule",
        GradientDescentReasoner.GradientDescentLearningSchedule.CONSTANT.toString(),
        "The learning schedule of the GradientDescent inference reasoner changes the learning rate during learning."
        + " STEPDECAY (Default): Decay the learning rate like: learningRate / (n_epoch^p) where p is set by reasoner.gradientdescent.inversescaleexp."
        + " CONSTANT: The learning rate is constant during learning."
    );

    public static final Option GRADIENT_DESCENT_MAX_ITER = new Option(
        "reasoner.gradientdescent.maxiterations",
        2500,
        "The maximum number of iterations of Gradient Descent to perform in a round of inference.",
        Option.FLAG_POSITIVE
    );

    public static final Option REASONER_RUN_FULL_ITERATIONS = new Option(
        "reasoner.runfulliterations",
        false,
        "Ignore all other stopping criteria and run until the maximum number of iterations."
    );

    public static final Option REASONER_OBJECTIVE_BREAK = new Option(
        "reasoner.objectivebreak",
        false,
        "Stop if the objective has not changed since the last iteration (or logging period)."
    );

    public static final Option REASONER_OBJECTIVE_TOLERANCE = new Option(
        "reasoner.objectivetolerance",
        1e-5f,
        "How close two objective values need to be to be considered the same.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option REASONER_VARIABLE_MOVEMENT_BREAK = new Option(
        "reasoner.variablemovementbreak",
        false,
        "Stop reasoner if two consecutive iterates are within reasoner.variablemovementtolerance distance."
    );

    public static final Option REASONER_VARIABLE_MOVEMENT_NORM = new Option(
        "reasoner.variablemovementnorm",
        Float.POSITIVE_INFINITY,
        "The p-norm used to measure the variable movement optimality condition."
        + " Default is the infinity-norm which is the absolute value of the maximum component of the movement vector."
        + " Note that the infinity-norm can be explicitly set with the string literal: 'Infinity'.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option REASONER_VARIABLE_MOVEMENT_TOLERANCE = new Option(
        "reasoner.variablemovementtolerance",
        1e-4f,
        "How close two iterates need to be to be considered the same.",
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

    public static final Option SGD_ADAM_BETA_1 = new Option(
        "sgd.adambeta1",
        0.9f,
        "The beta1 parameter for Adam optimization."
        + " This parameter controls the exponential decay rate of the first moment estimate. "
        + " See paper for details: https://arxiv.org/pdf/1412.6980.pdf"
    );

    public static final Option SGD_ADAM_BETA_2 = new Option(
        "sgd.adambeta2",
        0.999f,
        "The beta2 parameter for Adam optimization."
        + " This parameter controls the exponential decay rate of the second moment estimate. "
        + " See paper for details: https://arxiv.org/pdf/1412.6980.pdf"
    );

    public static final Option SGD_COORDINATE_STEP = new Option(
        "sgd.coordinatestep",
        false,
        "Take coordinate steps during sgd."
    );

    public static final Option SGD_EXTENSION = new Option(
        "sgd.extension",
        SGDReasoner.SGDExtension.NONE.toString(),
        "The SGD extension to use for SGD reasoning."
        + " NONE (Default): The standard SGD optimizer takes steps in the direction of the negative gradient scaled by the learning rate."
        + " ADAGRAD: Update the learning rate using the Adaptive Gradient (AdaGrad) algorithm."
        + " ADAM: Update the learning rate using the Adaptive Moment Estimation (Adam) algorithm."
    );

    public static final Option SGD_FIRST_ORDER_BREAK = new Option(
        "sgd.firstorderbreak",
        true,
        "Stop stochastic gradient descent when the norm of the gradient is less than sgd.firstorderthreshold."
    );

    public static final Option SGD_FIRST_ORDER_NORM = new Option(
        "sgd.firstordernorm",
        Float.POSITIVE_INFINITY,
        "The p-norm used to measure the first order optimality condition."
        + " Default is the infinity-norm which is the absolute value of the maximum component of the gradient vector."
        + " Note that the infinity-norm can be explicitly set with the string literal: 'Infinity'.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option SGD_FIRST_ORDER_THRESHOLD = new Option(
        "sgd.firstorderthreshold",
        0.01f,
        "Stochastic gradient descent stops when the norm of the gradient is less than this threshold.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option SGD_INVERSE_TIME_EXP = new Option(
        "sgd.inversescaleexp",
        1.0f,
        "If SGD is using the STEPDECAY learning schedule, then this value is the negative"
        + " exponent of the iteration count which scales the gradient step using:"
        + " (learning_rate / ( iteration ^ - SGD_INVERSE_TIME_EXP)).",
        Option.FLAG_POSITIVE
    );

    public static final Option SGD_LEARNING_RATE = new Option(
        "sgd.learningrate",
        1.0f,
        null,
        Option.FLAG_POSITIVE
    );

    public static final Option SGD_LEARNING_SCHEDULE = new Option(
        "sgd.learningschedule",
        SGDReasoner.SGDLearningSchedule.STEPDECAY.toString(),
        "The learning schedule of the SGD inference reasoner changes the learning rate during learning."
        + " STEPDECAY (Default): Decay the learning rate like: learningRate / (n_epoch^p) where p is set by sgd.inversescaleexp."
        + " CONSTANT: The learning rate is constant during learning."
    );

    public static final Option SGD_MAX_ITER = new Option(
        "sgd.maxiterations",
        200,
        "The maximum number of iterations of SGD to perform in a round of inference.",
        Option.FLAG_POSITIVE
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

    private static List<Option> options = new ArrayList<Option>();

    static {
        addClassOptions(Options.class);
    }

    // Static only.
    private Options() {}

    public static void addOption(Option option) {
        options.add(option);
    }

    public static void addClassOptions(Class targetClass) {
        for (Option option : fetchClassOptions(targetClass)) {
            addOption(option);
        }
    }

    public static List<Option> getOptions() {
        return options;
    }

    /**
     * Reflexively parse the options from a class.
     */
    public static List<Option> fetchClassOptions(Class targetClass) {
        List<Option> classOptions = new ArrayList<Option>();

        try {
            for (Field field : targetClass.getFields()) {
                // We only care about public static fields.
                if ((field.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) == 0) {
                    continue;
                }

                // We only want Option variables.
                if (field.getType() != Option.class) {
                    continue;
                }

                classOptions.add((Option)field.get(null));
            }
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }

        return classOptions;
    }

    /**
     * Clean all the known options.
     * Mainly used for testing.
     */
    public static void clearAll() {
        clearAll(false);
    }

    public static void clearAll(boolean force) {
        for (Option option : getOptions()) {
            // Leave a carve-out for special options.
            if (!force && option == PROJECT_VERSION) {
                continue;
            }

            option.clear();
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        JSONArray json = new JSONArray();
        for (Option option : getOptions()) {
            json.put(option.toJSON());
        }

        System.out.println(json.toString(4));
    }
}
