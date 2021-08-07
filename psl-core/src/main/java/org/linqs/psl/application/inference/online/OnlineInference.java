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
package org.linqs.psl.application.inference.online;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.application.inference.online.messages.actions.controls.Stop;
import org.linqs.psl.application.inference.online.messages.responses.ActionStatus;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class OnlineInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private OnlineServer server;
    private boolean modelUpdates;
    private boolean stopped;
    private double objective;

    protected OnlineInference(List<Rule> rules, Database database) {
        super(rules, database);
    }

    protected OnlineInference(List<Rule> rules, Database database, boolean relaxHardConstraints) {
        super(rules, database, relaxHardConstraints);
    }

    @Override
    protected void initialize() {
        stopped = false;
        modelUpdates = true;
        objective = 0.0;

        startServer();

        super.initialize();

        termStore.ensureVariableCapacity(atomManager.getCachedRVACount() + atomManager.getCachedObsCount());
    }

    @Override
    public void close() {
        stopped = true;
        closeServer();
        super.close();
    }

    private void closeServer() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    private void startServer() {
        server = new OnlineServer();
        server.start();
    }

    protected void executeAction(OnlineMessage action) {
        String response = null;

        // Switch on action class and execute.
        // All supported actions except for Exit should have a corresponding method that is called here.
        if (action.getClass() == Stop.class) {
            response = doStop();
        } else {
            throw new IllegalArgumentException("Unsupported action: " + action.getClass().getName() + ".");
        }

        server.onActionExecution(action, new ActionStatus(action, true, response));
    }

    protected String doStop() {
        stopped = true;
        return "OnlinePSL inference stopped.";
    }

    /**
     * Optimize if there were any modelUpdates since the last optimization.
     */
    private void optimize() {
        if (!modelUpdates) {
            return;
        }

        log.trace("Optimization Start");
        objective = reasoner.optimize(termStore);
        log.trace("Optimization End");

        modelUpdates = false;
    }

    @Override
    public double internalInference() {
        // Initial round of inference.
        optimize();

        while (!stopped) {
            OnlineMessage action = server.getAction();
            if (action == null) {
                continue;
            }

            try {
                log.trace(String.format("Executing action: %s", action));
                executeAction(action);
            } catch (IllegalArgumentException ex) {
                server.onActionExecution(action, new ActionStatus(action, false, ex.getMessage()));
            } catch (RuntimeException ex) {
                server.onActionExecution(action, new ActionStatus(action, false, ex.getMessage()));
                throw new RuntimeException(String.format("Critically failed to execute action: %s", action), ex);
            }
        }
        closeServer();

        return objective;
    }
}
