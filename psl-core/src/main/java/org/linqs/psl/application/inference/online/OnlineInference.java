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
package org.linqs.psl.application.inference.online;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.online.messages.actions.AddAtom;
import org.linqs.psl.application.inference.online.messages.actions.ObserveAtom;
import org.linqs.psl.application.inference.online.messages.actions.DeleteAtom;
import org.linqs.psl.application.inference.online.messages.actions.Stop;
import org.linqs.psl.application.inference.online.messages.actions.Sync;
import org.linqs.psl.application.inference.online.messages.actions.UpdateObservation;
import org.linqs.psl.application.inference.online.messages.actions.QueryAtom;
import org.linqs.psl.application.inference.online.messages.actions.WriteInferredPredicates;
import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.application.inference.online.messages.responses.ActionStatus;
import org.linqs.psl.application.inference.online.messages.responses.QueryAtomResponse;
import org.linqs.psl.application.inference.online.messages.actions.OnlineActionException;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.online.OnlineTermStore;

import org.linqs.psl.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class OnlineInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private OnlineServer server;
    private boolean stopped;
    private boolean modelUpdates;
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

        if (!(termStore instanceof OnlineTermStore)) {
            throw new RuntimeException("Online inference requires an OnlineTermStore. Found " + termStore.getClass() + ".");
        }
        termStore.ensureVariableCapacity(atomManager.getCachedRVACount() + atomManager.getCachedObsCount());
    }

    @Override
    protected PersistedAtomManager createAtomManager(Database database) {
        return new OnlineAtomManager(database);
    }

    @Override
    public void close() {
        if (server != null) {
            server.close();
            server = null;
        }

        super.close();
    }

    private void startServer() {
        server = new OnlineServer();
        server.start();
    }

    protected void executeAction(OnlineAction action) {
        String response = null;

        if (action.getClass() == AddAtom.class) {
            response = doAddAtom((AddAtom)action);
        } else if (action.getClass() == ObserveAtom.class) {
            response = doObserveAtom((ObserveAtom)action);
        } else if (action.getClass() == DeleteAtom.class) {
            response = doDeleteAtom((DeleteAtom)action);
        } else if (action.getClass() == Stop.class) {
            response = doStop((Stop)action);
        } else if (action.getClass() == Sync.class) {
            response = doSync((Sync)action);
        } else if (action.getClass() == UpdateObservation.class) {
            response = doUpdateObservation((UpdateObservation)action);
        } else if (action.getClass() == QueryAtom.class) {
            response = doQueryAtom((QueryAtom)action);
        } else if (action.getClass() == WriteInferredPredicates.class) {
            response = doWriteInferredPredicates((WriteInferredPredicates)action);
        } else {
            throw new OnlineActionException("Unknown action: " + action.getClass().getName() + ".");
        }

        server.onActionExecution(action, new ActionStatus(action, true, response));
    }

    protected String doAddAtom(AddAtom action) {
        boolean readPartition = (action.getPartitionName().equalsIgnoreCase("READ"));
        GroundAtom atom = ((OnlineTermStore)termStore).addAtom(action.getPredicate(), action.getArguments(), action.getValue(), readPartition);

        modelUpdates = true;
        return String.format("Added atom: %s", atom.toStringWithValue());
    }

    protected String doObserveAtom(ObserveAtom action) {
        ObservedAtom atom = ((OnlineTermStore)termStore).observeAtom(action.getPredicate(), action.getArguments(), action.getValue());

        if (atom !=null) {
            modelUpdates = true;
            return String.format("Observed atom: %s", atom.toStringWithValue());
        } else {
            return String.format("Random Variable Atom: %s(%s) did not exist in model.",
                    action.getPredicate(), StringUtils.join(", ", action.getArguments()));
        }
    }

    protected String doDeleteAtom(DeleteAtom action) {
        GroundAtom atom = ((OnlineTermStore)termStore).deleteAtom(action.getPredicate(), action.getArguments());

        if (atom != null) {
            modelUpdates = true;
            return String.format("Deleted atom: %s", atom.toString());
        } else {
            return String.format("Atom: %s(%s) did not exist in model.",
                    action.getPredicate(), StringUtils.join(", ", action.getArguments()));
        }
    }

    protected String doStop(Stop action) {
        stopped = true;

        return "OnlinePSL inference stopped.";
    }

    protected String doSync(Sync action) {
        doOptimize();
        return "OnlinePSL inference synced.";
    }

    protected String doUpdateObservation(UpdateObservation action) {
        GroundAtom atom = ((OnlineTermStore)termStore).updateAtom(action.getPredicate(), action.getArguments(), action.getValue());

        if (atom !=null) {
            modelUpdates = true;
            return String.format("Updated atom: %s", atom.toStringWithValue());
        } else {
            return String.format("Atom: %s(%s) did not exist in model.",
                    action.getPredicate(), StringUtils.join(", ", action.getArguments()));
        }
    }

    protected String doWriteInferredPredicates(WriteInferredPredicates action) {
        String response = null;

        doOptimize();

        if (action.getOutputDirectoryPath() != null) {
            log.info("Writing inferred predicates to file: " + action.getOutputDirectoryPath());
            database.outputRandomVariableAtoms(action.getOutputDirectoryPath());
            response = "Wrote inferred predicates to file: " + action.getOutputDirectoryPath();
        } else {
            log.info("Writing inferred predicates to output stream.");
            database.outputRandomVariableAtoms();
            response = "Wrote inferred predicates to output stream.";
        }

        return response;
    }

    protected String doQueryAtom(QueryAtom action) {
        double atomValue = -1.0;

        doOptimize();

        if (((OnlineAtomManager)atomManager).hasAtom(action.getPredicate(), action.getArguments())) {
            atomValue = atomManager.getAtom(action.getPredicate(), action.getArguments()).getValue();
        }

        // TODO: Combine query response with status.
        server.onActionExecution(action, new QueryAtomResponse(action, atomValue));

        if (atomValue == -1.0) {
            return String.format("Atom: %s(%s) not found.",
                    action.getPredicate(), StringUtils.join(", ", action.getArguments()));
        } else {
            return String.format("Atom: %s(%s) found. Returned to client.",
                    action.getPredicate(), StringUtils.join(", ", action.getArguments()));
        }
    }

    /**
     * Optimize if there were any new or deleted atoms since last optimization.
     */
    private void doOptimize() {
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
        doOptimize();

        while (!stopped) {
            OnlineAction action = server.getAction();
            if (action == null) {
                continue;
            }

            try {
                executeAction(action);
            } catch (OnlineActionException ex) {
                server.onActionExecution(action, new ActionStatus(action, false, ex.getMessage()));
                log.warn("Exception when executing action: " + action, ex);
            } catch (RuntimeException ex) {
                server.onActionExecution(action, new ActionStatus(action, false, ex.getMessage()));
                throw new RuntimeException("Critically failed to run command. Last seen command: " + action, ex);
            }
        }

        return objective;
    }
}
