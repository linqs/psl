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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.database.Database;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.sgd.SGDReasoner;
import org.linqs.psl.reasoner.sgd.term.SGDStreamingTermStore;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.server.OnlineServer;
import org.linqs.psl.server.actions.OnlineServerAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Use streaming grounding and inference with an SGD reasoner.
 */
public class SGDOnlineInference extends SGDStreamingInference {
    private static final Logger log = LoggerFactory.getLogger(SGDOnlineInference.class);
    // TODO: should be private but public for testing for now
    public OnlineServer server;

    public SGDOnlineInference(List<Rule> rules, Database db) {
        super(rules, db);
        server = new OnlineServer();
    }

    /**
     * Minimize the total weighted incompatibility of the atoms according to the rules,
     * and optionally commit the updated atoms back to the database.
     *
     * All RandomVariableAtoms which the model might access must be persisted in the Database.
     */
    @Override
    public void inference(boolean commitAtoms, boolean reset) {
        OnlineServerAction nextAction;

        if (reset) {
            initializeAtoms();

            if (termStore != null) {
                termStore.reset();
            }
        }

        log.info("Beginning Initial Round of Inference.");
        internalInference();
        log.info("Initial Round of Inference complete.");
        atomsCommitted = false;

        // Commits the RandomVariableAtoms back to the Database.
        if (commitAtoms) {
            commit();
        }

        log.info("Waiting for next action from server");
        nextAction = server.getNextAction();
        log.info("Got next action from server. Executing");

        while (!nextAction.close()) {
            nextAction.executeAction();
            // TODO: for testing clear next action
            server.clearNextAction();
            log.info("Executed Action. Starting New Round of Inference.");
            internalInference();
            log.info("Round of Inference Complete.");
            atomsCommitted = false;

            // Commits the RandomVariableAtoms back to the Database.
            if (commitAtoms) {
                commit();
            }

            log.info("Waiting for next action from server");
            nextAction = server.getNextAction();
            log.info("Got next action from server. Executing");
        }

    }
}
