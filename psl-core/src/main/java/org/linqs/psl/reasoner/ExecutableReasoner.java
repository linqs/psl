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
import org.linqs.psl.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
public abstract class ExecutableReasoner extends Reasoner {
    private static final Logger log = LoggerFactory.getLogger(ExecutableReasoner.class);

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
        this.executablePath = Options.EXECUTABLE_REASONER_PATH.getString();
        this.cleanupInput = Options.EXECUTABLE_CLEAN_INPUT.getBoolean();
        this.cleanupOutput = Options.EXECUTABLE_CLEAN_OUTPUT.getBoolean();
    }

    public ExecutableReasoner(String executablePath,
            String executableInputPath, String executableOutputPath,
            String... args) {
        this.executablePath = executablePath;
        this.executableInputPath = executableInputPath;
        this.executableOutputPath = executableOutputPath;
        this.args = args;

        this.cleanupInput = Options.EXECUTABLE_CLEAN_INPUT.getBoolean();
        this.cleanupOutput = Options.EXECUTABLE_CLEAN_OUTPUT.getBoolean();
    }

    @Override
    public double optimize(TermStore termStore) {
        log.debug("Writing model file: " + executableInputPath);
        File modelFile = new File(executableInputPath);

        try (BufferedWriter modelWriter = FileUtils.getBufferedWriter(modelFile)) {
            writeModel(modelWriter, termStore);
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
        try (BufferedReader resultsReader = FileUtils.getBufferedReader(resultsFile)) {
            readResults(resultsReader, termStore);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read results file: " + executableOutputPath, ex);
        }

        log.debug("Finished reading results file.");
        return -1.0;
    }

    protected void callReasoner() throws IOException {
        // Need extra allocation so the list will be mutable.
        List<String> command = new ArrayList<String>(Arrays.asList(args));
        command.add(0, executablePath);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        BufferedReader stdout = FileUtils.getBufferedReader(proc.getInputStream());
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
            FileUtils.delete(executableInputPath);
        }

        if (cleanupOutput) {
            FileUtils.delete(executableOutputPath);
        }
    }

    protected abstract void writeModel(BufferedWriter modelWriter, TermStore termStore) throws IOException;
    protected abstract void readResults(BufferedReader resultsReader, TermStore termStore) throws IOException;
}
