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
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.OnlineTermStore;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Use streaming grounding and inference with an SGD reasoner.
 */
public abstract class OnlineInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    public OnlineServer server;
    private boolean close;
    private double objective;

    protected OnlineInference(List<Rule> rules, Database db) {
        super(rules, db);
    }

    protected OnlineInference(List<Rule> rules, Database db, boolean relaxHardConstraints) {
        super(rules, db, relaxHardConstraints);
    }

    /**
     * Get objects ready for inference.
     * This will call into the abstract method completeInitialize().
     */
    @Override
    protected void initialize() {
        startServer();

        log.debug("Creating persisted atom manager.");
        atomManager = createAtomManager(db);
        log.debug("Atom manager initialization complete.");

        initializeAtoms();

        reasoner = createReasoner();
        termStore = createTermStore();
        groundRuleStore = createGroundRuleStore();
        termGenerator = createTermGenerator();

        int atomCapacity = atomManager.getCachedRVACount() + atomManager.getCachedOBSCount();
        termStore.ensureVariableCapacity(atomCapacity);

        if (normalizeWeights) {
            normalizeWeights();
        }

        if (relaxHardConstraints) {
            relaxHardConstraints();
        }

        close = false;
        objective = 0;

        completeInitialize();
    }

    @Override
    protected OnlineTermStore createTermStore() {
        return (OnlineTermStore)Options.INFERENCE_TS.getNewObject();
    }

    @Override
    protected OnlineAtomManager createAtomManager(Database db) {
        return new OnlineAtomManager(db);
    }

    private void startServer() {
        try {
            server = new OnlineServer<OnlineAction>();
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.closeServer()));
        } catch (IOException e) {
            log.info("Failed to start server");
            close();
            System.exit(1);
        }
    }

    protected void executeAction(OnlineAction nextAction) throws IllegalArgumentException {
        if (nextAction.getClass() == UpdateObservation.class) {
            doUpdateObservation((UpdateObservation)nextAction);
        } else if (nextAction.getClass() == AddAtom.class) {
            doAddAtom((AddAtom)nextAction);
        } else if (nextAction.getClass() == DeleteAtom.class) {
            doDeleteAtom((DeleteAtom)nextAction);
        } else if (nextAction.getClass() == WriteInferredPredicates.class) {
            doWriteInferredPredicates((WriteInferredPredicates)nextAction);
        } else if (nextAction.getClass() == Close.class) {
            doClose((Close)nextAction);
        } else {
            throw new IllegalArgumentException("Action: " + nextAction.getClass().getName() + " Not Supported.");
        }
    }

    protected void doAddAtom(AddAtom nextAction) throws IllegalArgumentException {
        // Resolve Predicate
        Predicate registeredPredicate = Predicate.get(nextAction.getPredicateName());
        if (registeredPredicate == null) {
            throw new IllegalArgumentException("Predicate is not registered: " + nextAction.getPredicateName());
        }

        switch (nextAction.getPartitionName()) {
            case "READ":
                ((OnlineTermStore)termStore).addAtom(registeredPredicate, nextAction.getArguments(), nextAction.getValue(), true);
                break;
            case "WRITE":
                ((OnlineTermStore)termStore).addAtom(registeredPredicate, nextAction.getArguments(), nextAction.getValue(), false);
                break;
            default:
                throw new IllegalArgumentException("Add Atom Partition: " + nextAction.getPartitionName() + "Not Supported");
        }
    }

    protected void doDeleteAtom(DeleteAtom nextAction) throws IllegalArgumentException {
        // Resolve Predicate
        Predicate registeredPredicate = Predicate.get(nextAction.getPredicateName());
        if (registeredPredicate == null) {
            throw new IllegalArgumentException("Predicate is not registered: " + nextAction.getPredicateName());
        }

        ((OnlineTermStore)termStore).deleteAtom(registeredPredicate, nextAction.getArguments());
    }

    protected void doUpdateObservation(UpdateObservation nextAction) throws IllegalArgumentException {
        // Resolve Predicate
        Predicate registeredPredicate = Predicate.get(nextAction.getPredicateName());
        if (registeredPredicate == null) {
            throw new IllegalArgumentException("Predicate is not registered: " + nextAction.getPredicateName());
        }

        ((OnlineTermStore)termStore).updateAtom(registeredPredicate, nextAction.getArguments(), nextAction.getValue());
    }

    protected void doClose(Close nextAction) {
        close = true;
    }

    protected void doWriteInferredPredicates(WriteInferredPredicates nextAction) {
        long start;
        long stop;
        // Activate the atoms that were added
        log.trace("Partial Grounding Start");
        start = System.currentTimeMillis();
        ArrayList<GroundRule> groundRules = ((OnlineAtomManager)atomManager).activateAtoms(rules, (OnlineTermStore) termStore);
        stop = System.currentTimeMillis();
        log.trace("Partial Grounding Stop");
        log.trace("Partial Grounding Time: {}", (stop - start));

        log.trace("Term Generation Start");
        log.trace("Adding " + groundRules.size() + " ground rules to model");
        start = System.currentTimeMillis();
        int newTermCount = 0;
        for (GroundRule groundRule : groundRules) {
            ReasonerTerm newTerm = termGenerator.createTerm(groundRule, termStore);
            if (newTerm == null) {
                // Term was trivial
                continue;
            }
            newTermCount++;
            termStore.add(groundRule, newTerm);
        }
        stop = System.currentTimeMillis();
        log.trace("Added " + newTermCount + " terms to model");
        log.trace("Term Generation Stop");
        log.trace("Term Generation Time: {}", (stop - start));

        // Ensure we are in optimal state
        log.trace("Optimization Start");
        start = System.currentTimeMillis();
        objective = reasoner.optimize(termStore);
        stop = System.currentTimeMillis();
        log.trace("Optimization Start");
        log.trace("Optimization Time: {}", (stop - start));

        outputResults(nextAction.getOutputDirectoryPath());
    }

    private void outputResults(String outputDirectoryPath) {
        // TODO: (Charles) Duplicated code fragment from launcher.
        // Set of open predicates
        Set<StandardPredicate> registeredPredicates = db.getDataStore().getRegisteredPredicates();

        // Write to provided file name and location
        log.trace("Writing inferred predicates");
        if (outputDirectoryPath == null) {
            for (StandardPredicate openPredicate : registeredPredicates) {
                for (GroundAtom atom : db.getAllGroundRandomVariableAtoms(openPredicate)) {
                    System.out.println(atom.toString() + " = " + atom.getValue());
                }
            }
        } else {
            log.info("Writing inferred predicates to file: " + outputDirectoryPath);
            File outputDirectory = new File(outputDirectoryPath);

            // mkdir -p
            outputDirectory.mkdirs();

            for (StandardPredicate predicate : registeredPredicates) {
                if (db.getAllGroundRandomVariableAtoms(predicate).size() == 0) {
                    continue;
                }

                try {
                    FileWriter predFileWriter = new FileWriter(new File(outputDirectory, predicate.getName() + ".txt"));
                    BufferedWriter bufferedPredWriter = new BufferedWriter(predFileWriter);
                    StringBuilder row = new StringBuilder();

                    log.debug("Writing " + db.getAllGroundRandomVariableAtoms(predicate).size() + " Inferred predicates");

                    for (GroundAtom atom : db.getAllGroundRandomVariableAtoms(predicate)) {
                        row.setLength(0);

                        for (Constant term : atom.getArguments()) {
                            row.append(term.rawToString());
                            row.append("\t");
                        }
                        row.append(Double.toString(atom.getValue()));
                        row.append("\n");

                        bufferedPredWriter.write(row.toString());
                    }

                    bufferedPredWriter.close();
                } catch (IOException ex) {
                    log.error("Exception writing predicate {}", predicate);
                }
            }
        }
    }

    /**
     * Minimize the total weighted incompatibility of the atoms according to the rules,
     * TODO: (Charles) By overriding internal inference rather than inference() we are not committing the random
     *  variable atom values to the database after updates. Perhaps periodically update the database or add it
     *  as a part of action execution.
     */
    @Override
    public double internalInference() {
        // Initial round of inference
        objective = reasoner.optimize(termStore);

        OnlineAction nextAction;
        try {
            do {
                try {
                    nextAction = (OnlineAction) server.dequeClientInput();
                    executeAction(nextAction);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    log.debug("Exception when executing action.");
                    log.debug(e.getMessage());
                    log.debug(Arrays.toString(e.getStackTrace()));
                }
            } while (!close);
        } catch (InterruptedException e) {
            log.debug("Internal Inference Interrupted");
            log.debug(e.getMessage());
        } finally {
            server.closeServer();
        }
        return objective;
    }
}
