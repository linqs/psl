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
package org.linqs.psl.application.inference;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.ADMMTermGenerator;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Reflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public abstract class InferenceApplication implements ModelApplication {
    private static final Logger log = LoggerFactory.getLogger(InferenceApplication.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "inference";

    /**
     * The class to use for a reasoner.
     */
    public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
    public static final String REASONER_DEFAULT = ADMMReasoner.class.getName();

    /**
     * The class to use for ground rule storage.
     */
    public static final String GROUND_RULE_STORE_KEY = CONFIG_PREFIX + ".groundrulestore";
    public static final String GROUND_RULE_STORE_DEFAULT = MemoryGroundRuleStore.class.getName();

    /**
     * The class to use for term storage.
     * Should be compatible with REASONER_KEY.
     */
    public static final String TERM_STORE_KEY = CONFIG_PREFIX + ".termstore";
    public static final String TERM_STORE_DEFAULT = ADMMTermStore.class.getName();

    /**
     * The class to use for term generator.
     * Should be compatible with REASONER_KEY and TERM_STORE_KEY.
     */
    public static final String TERM_GENERATOR_KEY = CONFIG_PREFIX + ".termgenerator";
    public static final String TERM_GENERATOR_DEFAULT = ADMMTermGenerator.class.getName();

    protected Model model;
    protected Database db;
    protected Reasoner reasoner;

    protected GroundRuleStore groundRuleStore;
    protected TermStore termStore;
    protected TermGenerator termGenerator;
    protected PersistedAtomManager atomManager;

    public InferenceApplication(Model model, Database db) {
        this.model = model;
        this.db = db;

        initialize();
    }

    /**
     * Get objects ready for inference.
     * This will call into the abstract method completeInitialize().
     */
    protected void initialize() {
        log.debug("Creating persisted atom mannager.");
        atomManager = createAtomManager(db);
        log.debug("Atom manager initialization complete.");

        reasoner = createReasoner();
        termStore = createTermStore();
        groundRuleStore = createGroundRuleStore();
        termGenerator = createTermGenerator();

        termStore.ensureVariableCapacity(atomManager.getCachedRVACount());

        completeInitialize();
    }

    protected PersistedAtomManager createAtomManager(Database db) {
        return new PersistedAtomManager(db);
    }

    protected GroundRuleStore createGroundRuleStore() {
        return (GroundRuleStore)Config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);
    }

    protected Reasoner createReasoner() {
        return (Reasoner)Config.getNewObject(REASONER_KEY, REASONER_DEFAULT);
    }

    protected TermGenerator createTermGenerator() {
        return (TermGenerator)Config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);
    }

    protected TermStore createTermStore() {
        return (TermStore)Config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
    }

    /**
     * Complete the initialization process.
     * Most of the infrastructure will have been constructued.
     * The child is responsible for constructing the AtomManager
     * and populating the ground rule store.
     */
    protected void completeInitialize() {}

    /**
     * Alias for inference() with committing atoms.
     */
    public void inference() {
        inference(true);
    }

    /**
     * Minimizes the total weighted incompatibility of the GroundAtoms in the Database
     * according to the Model and commits the updated truth values back to the Database.
     *
     * All RandomVariableAtoms which the Model might access must be persisted in the Database.
     */
    public void inference(boolean commitAtoms) {
        log.info("Beginning inference.");
        internalInference();
        log.info("Inference complete.");

        // Commits the RandomVariableAtoms back to the Database.
        if (commitAtoms) {
            log.info("Writing results to Database.");
            atomManager.commitPersistedAtoms();
            log.info("Results committed to database.");
        }
    }

    /**
     * The implementation of the full inference by each class.
     */
    protected void internalInference() {
        reasoner.optimize(termStore);
    }

    public Reasoner getReasoner() {
        return reasoner;
    }

    public GroundRuleStore getGroundRuleStore() {
        return groundRuleStore;
    }

    public TermStore getTermStore() {
        return termStore;
    }

    public PersistedAtomManager getAtomManager() {
        return atomManager;
    }

    @Override
    public void close() {
        termStore.close();
        groundRuleStore.close();
        reasoner.close();

        termStore = null;
        groundRuleStore = null;
        reasoner = null;

        model = null;
        db = null;
    }

    /**
     * Construct an inference application given the data.
     * Look for a constructor like: (Model, Database).
     */
    public static InferenceApplication getInferenceApplication(String className, Model model, Database db) {
        className = Reflection.resolveClassName(className);

        Class<? extends InferenceApplication> classObject = null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends InferenceApplication> uncheckedClassObject = (Class<? extends InferenceApplication>)Class.forName(className);
            classObject = uncheckedClassObject;
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Could not find class: " + className, ex);
        }

        Constructor<? extends InferenceApplication> constructor = null;
        try {
            constructor = classObject.getConstructor(Model.class, Database.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("No sutible constructor found for inference application: " + className + ".", ex);
        }

        InferenceApplication inferenceApplication = null;
        try {
            inferenceApplication = constructor.newInstance(model, db);
        } catch (InstantiationException ex) {
            throw new RuntimeException("Unable to instantiate inference application (" + className + ")", ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Insufficient access to constructor for " + className, ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Error thrown while constructing " + className, ex);
        }

        return inferenceApplication;
    }
}
