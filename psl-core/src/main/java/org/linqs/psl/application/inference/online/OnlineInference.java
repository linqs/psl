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
import org.linqs.psl.application.inference.online.actions.DeleteAtom;
import org.linqs.psl.application.inference.online.actions.Stop;
import org.linqs.psl.application.inference.online.actions.UpdateObservation;
import org.linqs.psl.application.inference.online.actions.QueryAtom;
import org.linqs.psl.application.inference.online.actions.WriteInferredPredicates;
import org.linqs.psl.application.inference.online.actions.OnlineAction;
import org.linqs.psl.application.inference.online.actions.OnlineActionException;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.OnlineTermStore;

import org.linqs.psl.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

public abstract class OnlineInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private OnlineServer server;
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
        if (action.getClass() == AddAtom.class) {
            doAddAtom((AddAtom)action);
        } else if (action.getClass() == DeleteAtom.class) {
            doDeleteAtom((DeleteAtom)action);
        } else if (action.getClass() == Stop.class) {
            doStop((Stop)action);
        } else if (action.getClass() == UpdateObservation.class) {
            doUpdateObservation((UpdateObservation)action);
        } else if (action.getClass() == QueryAtom.class) {
            doQueryAtom((QueryAtom)action);
        } else if (action.getClass() == WriteInferredPredicates.class) {
            doWriteInferredPredicates((WriteInferredPredicates)action);
        } else {
            throw new OnlineActionException("Unknown action: " + action.getClass().getName() + ".");
        }
    }

    protected void doAddAtom(AddAtom action) {
        boolean readPartition = (action.getPartitionName().equalsIgnoreCase("READ"));
        ((OnlineTermStore)termStore).addAtom(action.getPredicate(), action.getArguments(), action.getValue(), readPartition);
    }

    protected void doDeleteAtom(DeleteAtom action) {
        ((OnlineTermStore)termStore).deleteAtom(action.getPredicate(), action.getArguments());
    }

    protected void doStop(Stop action) {
        stopped = true;
    }

    protected void doUpdateObservation(UpdateObservation action) {
        ((OnlineTermStore)termStore).updateAtom(action.getPredicate(), action.getArguments(), action.getValue());
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

    protected void doQueryAtom(QueryAtom action) {
        String queryString = null;

        log.trace("Optimization Start");
        objective = reasoner.optimize(termStore);
        log.trace("Optimization Start");

        // Write atom value to client.
        OutputStreamWriter outputWriter = action.getOutputWriter();
        GroundAtom atom = atomManager.getDatabase().getAtom(action.getPredicate(), false, action.getArguments());

        if (atom == null) {
            queryString = "Atom: " + action.getPredicate() +
                    "(" + StringUtils.join(",", action.getArguments()) + ")" + " does not exist.\n";
        } else {
            queryString = atom.toStringWithValue() + "\n";
        }

        try {
            outputWriter.write(queryString);
            outputWriter.flush();
        } catch (IOException e) {
            log.error("Exception writing queried atom {}", atom.toString());
        }
    }

    @Override
    public double internalInference() {
        // Initial round of inference.
        objective = reasoner.optimize(termStore);

        while (!stopped) {
            OnlineAction action = server.getAction();
            if (action == null) {
                continue;
            }

            try {
                executeAction(action);
            } catch (OnlineActionException ex) {
                log.warn("Exception when executing action: " + action, ex);
            } catch (RuntimeException ex) {
                throw new RuntimeException("Critically failed to run command. Last seen command: " + action, ex);
            }
        }

        return objective;
    }
}
