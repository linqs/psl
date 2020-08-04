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
import org.linqs.psl.application.inference.online.actions.AddAtom;
import org.linqs.psl.application.inference.online.actions.Close;
import org.linqs.psl.application.inference.online.actions.DeleteAtom;
import org.linqs.psl.application.inference.online.actions.UpdateObservation;
import org.linqs.psl.application.inference.online.actions.WriteInferredPredicates;
import org.linqs.psl.application.inference.online.actions.OnlineAction;
import org.linqs.psl.application.inference.online.actions.OnlineActionException;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.OnlineTermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public abstract class OnlineInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private OnlineServer server;
    private boolean closed;
    private double objective;

    protected OnlineInference(List<Rule> rules, Database database) {
        super(rules, database);
    }

    protected OnlineInference(List<Rule> rules, Database database, boolean relaxHardConstraints) {
        super(rules, database, relaxHardConstraints);
    }

    @Override
    protected void initialize() {
        closed = false;
        objective = 0.0;
        termStore.ensureVariableCapacity(atomManager.getCachedRVACount() + atomManager.getCachedObsCount());

        startServer();

        super.initialize();

        if (!(termStore instanceof OnlineTermStore)) {
            throw new RuntimeException("Online inference requires an OnlineTermStore. Found " + termStore.getClass() + ".");
        }
    }

    @Override
    protected PersistedAtomManager createAtomManager(Database database) {
        return new OnlineAtomManager(database);
    }

    private void startServer() {
        // TODO(eriq): This should not throw.
        try {
            server = new OnlineServer();
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    server.close();
                }
            });
        } catch (IOException ex) {
            throw new RuntimeException("Failed to start online server.", ex);
        }
    }

    protected void executeAction(OnlineAction action) {
        if (action.getClass() == UpdateObservation.class) {
            doUpdateObservation((UpdateObservation)action);
        } else if (action.getClass() == AddAtom.class) {
            doAddAtom((AddAtom)action);
        } else if (action.getClass() == DeleteAtom.class) {
            doDeleteAtom((DeleteAtom)action);
        } else if (action.getClass() == WriteInferredPredicates.class) {
            doWriteInferredPredicates((WriteInferredPredicates)action);
        } else if (action.getClass() == Close.class) {
            doClose((Close)action);
        } else {
            throw new OnlineActionException("Action: " + action.getClass().getName() + " not Supported.");
        }
    }

    protected void doAddAtom(AddAtom action) {
        // Resolve Predicate
        Predicate registeredPredicate = Predicate.get(action.getPredicateName());
        if (registeredPredicate == null) {
            throw new OnlineActionException("Predicate is not registered: " + action.getPredicateName());
        }

        switch (action.getPartitionName()) {
            case "READ":
                ((OnlineTermStore)termStore).addAtom(registeredPredicate, action.getArguments(), action.getValue(), true);
                break;
            case "WRITE":
                ((OnlineTermStore)termStore).addAtom(registeredPredicate, action.getArguments(), action.getValue(), false);
                break;
            default:
                throw new OnlineActionException("Add Atom Partition: " + action.getPartitionName() + " not Supported");
        }
    }

    protected void doDeleteAtom(DeleteAtom action) {
        // Resolve Predicate
        Predicate registeredPredicate = Predicate.get(action.getPredicateName());
        if (registeredPredicate == null) {
            throw new OnlineActionException("Predicate is not registered: " + action.getPredicateName());
        }

        ((OnlineTermStore)termStore).deleteAtom(registeredPredicate, action.getArguments());
    }

    protected void doUpdateObservation(UpdateObservation action) {
        // Resolve Predicate
        Predicate registeredPredicate = Predicate.get(action.getPredicateName());
        if (registeredPredicate == null) {
            throw new OnlineActionException("Predicate is not registered: " + action.getPredicateName());
        }

        ((OnlineTermStore)termStore).updateAtom(registeredPredicate, action.getArguments(), action.getValue());
    }

    protected void doClose(Close action) {
        closed = true;
    }

    protected void doWriteInferredPredicates(WriteInferredPredicates action) {
        log.trace("Optimization Start");
        objective = reasoner.optimize(termStore);
        log.trace("Optimization Start");

        if (action.getOutputDirectoryPath() != null) {
            log.info("Writing inferred predicates to file: " + action.getOutputDirectoryPath());
            database.outputRandomVariableAtoms(action.getOutputDirectoryPath());
        } else {
            log.info("Writing inferred predicates to output stream.");
            database.outputRandomVariableAtoms();
        }
    }

    /**
     * Minimize the total weighted incompatibility of the atoms according to the rules.
     * TODO(Charles): By overriding internal inference rather than inference() we are not committing the random
     *  variable atom values to the database after updates. Perhaps periodically update the database or add it
     *  as a part of action execution.
     */
    @Override
    public double internalInference() {
        // Initial round of inference
        objective = reasoner.optimize(termStore);

        OnlineAction action = null;
        try {
            do {
                action = server.dequeClientInput();
                if (action == null) {
                    continue;
                }

                try {
                    executeAction(action);
                } catch (OnlineActionException ex) {
                    log.warn(String.format("Exception when executing action: %s", action), ex);
                } catch (RuntimeException ex) {
                    throw new RuntimeException("Critically failed to run command. Last seen command: " + action, ex);
                }
            } while (!closed);
        } finally {
            server.close();
        }

        return objective;
    }
}
