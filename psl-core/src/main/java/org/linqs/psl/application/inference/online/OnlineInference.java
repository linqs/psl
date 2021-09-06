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
import org.linqs.psl.application.inference.online.messages.actions.model.AddAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.QueryAtom;
import org.linqs.psl.application.inference.online.messages.responses.ActionStatus;
import org.linqs.psl.application.inference.online.messages.responses.QueryAtomResponse;
import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.online.OnlineTermStore;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

// TODO: The TrainingMap can get outdated and should be updated.
public abstract class OnlineInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private OnlineServer server;
    private boolean modelUpdates;
    private boolean stopped;
    private double objective;

    // Optional evaluation resources.
    private List<Evaluator> evaluators;
    private TrainingMap trainingMap;
    private Set<StandardPredicate> evaluationPredicates;

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

        evaluators = null;
        trainingMap = null;
        evaluationPredicates = null;

        startServer();

        super.initialize();

        termStore.ensureVariableCapacity(atomManager.getCachedRVACount() + atomManager.getCachedObsCount());
    }

    @Override
    protected PersistedAtomManager createAtomManager(Database database) {
        return new OnlineAtomManager(database, this.initialValue);
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
        if (action.getClass() == AddAtom.class) {
            response = doAddAtom((AddAtom)action);
        } else if (action.getClass() == QueryAtom.class) {
            response = doQueryAtom((QueryAtom)action);
        } else if (action.getClass() == Stop.class) {
            response = doStop();
        } else {
            throw new IllegalArgumentException("Unsupported action: " + action.getClass().getName() + ".");
        }

        server.onActionExecution(action, new ActionStatus(action, true, response));
    }

    protected String doAddAtom(AddAtom action) {
        GroundAtom atom = null;

        if (atomManager.getDatabase().hasAtom(action.getPredicate(), action.getArguments())) {
            atom = ((OnlineAtomManager)atomManager).deleteAtom(action.getPredicate(), action.getArguments());
            ((OnlineTermStore)termStore).deleteLocalVariable(atom);
        }

        if (action.getPartitionName().equalsIgnoreCase("READ")) {
            atom = ((OnlineAtomManager)atomManager).addObservedAtom(action.getPredicate(), action.getValue(), action.getArguments());
        } else {
            atom = ((OnlineAtomManager)atomManager).addRandomVariableAtom(action.getPredicate(), action.getValue(), action.getArguments());
        }

        ((OnlineTermStore)termStore).createLocalVariable(atom);

        modelUpdates = true;
        return String.format("Added atom: %s", atom.toStringWithValue());
    }

    protected String doQueryAtom(QueryAtom action) {
        if (!((OnlineAtomManager)atomManager).hasAtom(action.getPredicate(), action.getArguments())) {
            server.onActionExecution(action, new QueryAtomResponse(action, -1.0));

            return String.format("Atom: %s(%s) not found.",
                    action.getPredicate(), StringUtils.join(", ", action.getArguments()));
        }

        optimize();

        double atomValue = atomManager.getAtom(action.getPredicate(), action.getArguments()).getValue();
        server.onActionExecution(action, new QueryAtomResponse(action, atomValue));

        return String.format("Atom: %s(%s) found. Returned to client.",
                action.getPredicate(), StringUtils.join(", ", action.getArguments()));
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
        objective = reasoner.optimize(termStore, evaluators, trainingMap, evaluationPredicates);
        log.trace("Optimization End");

        modelUpdates = false;
    }

    @Override
    public double internalInference(List<Evaluator> evaluators, TrainingMap trainingMap, Set<StandardPredicate> evaluationPredicates) {
        this.evaluators = evaluators;
        this.trainingMap = trainingMap;
        this.evaluationPredicates = evaluationPredicates;

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
