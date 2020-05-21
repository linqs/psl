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
package org.linqs.psl.application.inference.mpe.online;

import org.linqs.psl.application.inference.mpe.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.application.inference.mpe.online.actions.OnlineAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Use streaming grounding and inference with an SGD reasoner.
 */
public abstract class OnlineInference extends MPEInference {
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private OnlineServer server;

    protected OnlineInference(List<Rule> rules, Database db) {
        super(rules, db);
        startServer();
    }

    protected OnlineInference(List<Rule> rules, Database db, boolean relaxHardConstraints) {
        super(rules, db, relaxHardConstraints);
        startServer();
    }

    private void startServer() {
        try {
            server = new OnlineServer();
            server.start();
        } catch (IOException e) {
            log.info("Failed to start server");
            close();
            System.exit(1);
        }
    }

    /**
     * Minimize the total weighted incompatibility of the atoms according to the rules,
     * TODO: (Charles) By overriding internal inference rather than inference() we are not committing the random variable atom values to the data base after updates
     */
    @Override
    public void internalInference() {
        // Initial round of inference
        reasoner.optimize(termStore);

        OnlineAction nextAction;
        try {
        log.info("Waiting for next action from client");
        nextAction = server.dequeNextAction();
        log.info("Got next action from client. Executing");

        while (!nextAction.close()) {
            nextAction.executeAction();

            log.info("Executed Action. Starting New Round of Inference.");
            reasoner.optimize(termStore);
            log.info("Round of Inference Complete.");

            log.info("Waiting for next action from client");
            nextAction = server.dequeNextAction();
            log.info("Got next action from client. Executing");
        }

        } catch (InterruptedException ignored) {
        }

        server.closeServer();
    }
}