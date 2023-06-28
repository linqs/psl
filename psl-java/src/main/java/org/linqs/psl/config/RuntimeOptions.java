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
import org.linqs.psl.application.learning.weight.gradient.optimalvalue.StructuredPerceptron;
import org.linqs.psl.runtime.Runtime;

import org.linqs.psl.util.SystemUtils;

/**
 * Additional options for the PSL runtime.
 */
public class RuntimeOptions {
    public static final Option DB_H2_PATH = new Option(
        "runtime.db.h2.path",
        SystemUtils.getTempDir("psl_h2"),
        "Path for H2 database file."
    );

    public static final Option DB_H2_INMEMORY = new Option(
        "runtime.db.h2.inmemory",
        false,
        "Whether to put the H2 database in memory (true) or on disk (false)."
    );

    public static final Option DB_INT_IDS = new Option(
        "runtime.db.intids",
        false,
        "Assume all unique identifiers are integers (UniqueIntID) instead of strings (UniqueStringID)."
    );

    public static final Option DB_PG_NAME = new Option(
        "runtime.db.pg.name",
        "psl",
        "Name for the PostgreSQL database."
        + " Not compatible with H2 options."
    );

    public static final Option DB_SQLITE_PATH = new Option(
        "runtime.db.sqlite.path",
        SystemUtils.getTempDir("psl_sqlite"),
        "Path for SQLite database file."
    );

    public static final Option DB_SQLITE_INMEMORY = new Option(
        "runtime.db.sqlite.inmemory",
        true,
        "Whether to put the SQLite database in memory (true) or on disk (false)."
    );

    public static final Option DB_TYPE = new Option(
        "runtime.db.type",
        Runtime.DatabaseType.SQLite.toString(),
        "The type of database to use. See the Runtime.DatabaseType enum."
    );

    public static final Option HELP = new Option(
        "runtime.help",
        false,
        "Display help information to STDOUT and do not perform any additional tasks."
    );

    public static final Option INFERENCE = new Option(
        "runtime.inference",
        false,
        "Run inference."
    );

    public static final Option INFERENCE_COMMIT = new Option(
        "runtime.inference.commit",
        true,
        "Commit inferred values to the database."
    );

    public static final Option INFERENCE_METHOD = new Option(
        "runtime.inference.method",
        ADMMInference.class.getName(),
        "Use the specified InferenceApplication when running inference."
    );

    public static final Option INFERENCE_OUTPUT_RESULTS = new Option(
        "runtime.inference.output.results",
        true,
        "Whether to output the inferred atoms after inference."
    );

    public static final Option INFERENCE_OUTPUT_RESULTS_DIR = new Option(
        "runtime.inference.output.results.dir",
        null,
        "A directory for writing inference results (STDOUT is used otherwise)."
    );

    public static final Option INFERENCE_OUTPUT_GROUNDRULES_PATH = new Option(
        "runtime.inference.output.groundrules.path",
        null,
        "If ground rules are output, place them in at the specified path (or STDOUT if not specified)."
    );

    public static final Option INFERENCE_OUTPUT_GROUNDRULES = new Option(
        "runtime.inference.output.groundrules",
        false,
        "Whether to output ground rules before inference."
        + " The " + INFERENCE_OUTPUT_GROUNDRULES_PATH.name() + " option controls where ground rules are output."
    );

    public static final Option INFERENCE_CLEAR_RULES = new Option(
        "runtime.inference.clearrules",
        false,
        "Clear learning rules before inference. Useful when switching models between train and test."
    );

    public static final Option LEARN = new Option(
        "runtime.learn",
        false,
        "Run learning."
    );

    public static final Option VALIDATION = new Option(
        "runtime.validation",
        false,
        "Run validation while learning."
    );

    public static final Option LEARN_METHOD = new Option(
        "runtime.learn.method",
        StructuredPerceptron.class.getName(),
        "Use the specified WeightLearningApplication when running learning."
    );

    public static final Option LEARN_OUTPUT_MODEL_PATH = new Option(
        "runtime.learn.output.model.path",
        null,
        "Optional path to output a PSL model file with the learned rules."
    );

    public static final Option LOG_LEVEL = new Option(
        "runtime.log.level",
        "INFO",
        "The logging level."
    );

    public static final Option OUTPUT_ALL_ATOMS = new Option(
        "runtime.output.atoms.all",
        false,
        "Instead of just outputting relevant atoms, output all atoms in the atom store."
        + " The exact semantics depends on the process outputting atoms, e.g., inference of the grounding API."
    );

    public static final Option VERSION = new Option(
        "runtime.version",
        false,
        "Display version information to STDOUT and do not perform any additional tasks."
    );

    static {
        Options.addClassOptions(RuntimeOptions.class);
    }

    // Static only.
    private RuntimeOptions() {}

    public static void main(String[] args) {
        Options.main(args);
    }
}
