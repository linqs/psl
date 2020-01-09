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
package org.linqs.psl.reasoner;

import org.linqs.psl.config.Config;
import org.linqs.psl.reasoner.term.TermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An abstract superclass for reasoners implemented as command-line executables.
 *
 * Ground models are provided to the executable and results are read via temporary files.
 */
public abstract class ExecutableReasoner implements Reasoner {
    private static final Logger log = LoggerFactory.getLogger(ExecutableReasoner.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "executablereasoner";

    /**
     * Key for int property for the path of the executable.
     */
    public static final String EXECUTABLE_PATH_KEY = CONFIG_PREFIX + ".executablepath";

    /**
     * Key for boolean property for whether to delete the input file to external the reasoner on close.
     */
    public static final String CLEANUP_INPUT_KEY = CONFIG_PREFIX + ".cleanupinput";
    public static final boolean CLEANUP_INPUT_DEFAULT = true;

    /**
     * Key for boolean property for whether to delete the output file to external the reasoner on close.
     */
    public static final String CLEANUP_OUTPUT_KEY = CONFIG_PREFIX + ".cleanupoutput";
    public static final boolean CLEANUP_OUTPUT_DEFAULT = true;

    /**
     * The file that PSL will write for the reasoner.
     */
    protected String executableInputPath;

    /**
     * The file that the reasoner will write before temination.
     */
    protected String executableOutputPath;

    /**
     * The path the to executable to call.
     */
    protected String executablePath;

    protected boolean cleanupInput;
    protected boolean cleanupOutput;

    protected String[] args;

    public ExecutableReasoner() {
        this.executablePath = Config.getString(EXECUTABLE_PATH_KEY, "");
        this.cleanupInput = Config.getBoolean(CLEANUP_INPUT_KEY, CLEANUP_INPUT_DEFAULT);
        this.cleanupOutput = Config.getBoolean(CLEANUP_OUTPUT_KEY, CLEANUP_OUTPUT_DEFAULT);
    }

    public ExecutableReasoner(String executablePath,
            String executableInputPath, String executableOutputPath,
            String... args) {
        this.executablePath = executablePath;
        this.executableInputPath = executableInputPath;
        this.executableOutputPath = executableOutputPath;
        this.args = args;

        this.cleanupInput = Config.getBoolean(CLEANUP_INPUT_KEY, CLEANUP_INPUT_DEFAULT);
        this.cleanupOutput = Config.getBoolean(CLEANUP_OUTPUT_KEY, CLEANUP_OUTPUT_DEFAULT);
    }

    @Override
    public void optimize(TermStore termStore) {
        log.debug("Writing model file: " + executableInputPath);
        File modelFile = new File(executableInputPath);

        try {
            BufferedWriter modelWriter = new BufferedWriter(new FileWriter(modelFile));
            writeModel(modelWriter, termStore);
            modelWriter.close();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to write model file: " + executableInputPath, ex);
        }

        log.debug("Finished writing model file. Calling reasoner: " + executablePath);
        try {
            callReasoner();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to call external reasoner: " + executablePath, ex);
        }

        log.debug("Reasoner finished. Reading results file: " + executableOutputPath);
        File resultsFile = new File(executableOutputPath);
        try {
            BufferedReader resultsReader = new BufferedReader(new FileReader(resultsFile));
            readResults(resultsReader, termStore);
            resultsReader.close();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read results file: " + executableOutputPath, ex);
        }
        log.debug("Finished reading results file.");
    }

    protected void callReasoner() throws IOException {
        // Need extra allocation so the list will be mutable.
        List<String> command = new ArrayList<String>(Arrays.asList(args));
        command.add(0, executablePath);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line;
        while ((line = stdout.readLine()) != null) {
            log.debug(line);
        }
        stdout.close();

        int exitValue = -1;
        try {
            exitValue = proc.waitFor();
        } catch (InterruptedException ex) {
            throw new RuntimeException("Failed to wait for executable reasoner.", ex);
        }

        if (exitValue != 0) {
            throw new RuntimeException("Executable exited with unexpected value: " + exitValue);
        }
    }

    @Override
    public void close() {
        if (cleanupInput) {
            (new File(executableInputPath)).delete();
        }

        if (cleanupOutput) {
            (new File(executableOutputPath)).delete();
        }
    }

    protected abstract void writeModel(BufferedWriter modelWriter, TermStore termStore) throws IOException;
    protected abstract void readResults(BufferedReader resultsReader, TermStore termStore) throws IOException;
}
