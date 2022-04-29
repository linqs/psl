/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.Reflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class for learning the weights of weighted mutableRules from data for a model.
 * All non-abstract children should have a constructor that takes:
 * (List<Rule>, Database (rv), Database (observed)).
 */
public abstract class WeightLearningApplication implements ModelApplication {
    /**
     * The delimiter to separate rule weights (and location ids).
     * Note that we cannot use ',' because our configuration infrastructure
     * will try to interpret it as a list of strings.
     */
    public static final String DELIM = ":";

    private static final Logger log = LoggerFactory.getLogger(WeightLearningApplication.class);

    protected Database rvDB;
    protected Database observedDB;

    protected List<Rule> allRules;
    protected List<WeightedRule> mutableRules;

    protected TrainingMap trainingMap;

    protected InferenceApplication inference;
    protected Evaluator evaluator;

    private boolean groundModelInit;

    /**
     * Flags to track if the current variable configuration is an MPE state.
     * This will get set to true when computeMPEState is called,
     * but besides that it is up to children to set to false when weights are changed.
     */
    protected boolean inMPEState;

    public WeightLearningApplication(List<Rule> rules, Database rvDB, Database observedDB) {
        this.rvDB = rvDB;
        this.observedDB = observedDB;

        allRules = new ArrayList<Rule>();
        mutableRules = new ArrayList<WeightedRule>();

        for (Rule rule : rules) {
            allRules.add(rule);

            if (rule instanceof WeightedRule) {
                mutableRules.add((WeightedRule)rule);
            }
        }

        groundModelInit = false;
        inMPEState = false;

        evaluator = (Evaluator)Options.WLA_EVAL.getNewObject();
    }

    /**
     * Learns new weights.
     * The RandomVariableAtoms in the distribution are those
     * persisted in the random variable Database when this method is called. All
     * RandomVariableAtoms which the Model might access must be persisted in the Database.
     */
    public void learn() {
        // Sets up the ground model.
        initGroundModel();

        // Learns new weights.
        doLearn();
    }

    /**
     * Do the actual learning procedure.
     */
    protected abstract void doLearn();

    /**
     * Set a budget (give as a proportion of the max budget).
     * Child implementations should make sure to override this and call up the super chain.
     */
    public void setBudget(double budget) {
        inference.setBudget(budget);
    }

    public InferenceApplication getInferenceApplication() {
        return inference;
    }

    /**
     * Initialize all the infrastructure dealing with the ground model.
     * Children should favor overriding postInitGroundModel() instead of this.
     */
    protected void initGroundModel() {
        if (groundModelInit) {
            return;
        }

        InferenceApplication inference = InferenceApplication.getInferenceApplication(Options.WLA_INFERENCE.getString(), allRules, rvDB);
        initGroundModel(inference);
    }

    private void initGroundModel(InferenceApplication inference) {
        if (groundModelInit) {
            return;
        }

        TrainingMap trainingMap = new TrainingMap(inference.getAtomManager(), observedDB);
        initGroundModel(inference, trainingMap);
    }

    /**
     * Pass in all the ground model infrastructure.
     * The caller should be careful calling this method instead of the other variants.
     * Children should favor overriding postInitGroundModel() instead of this.
     */
    public void initGroundModel(InferenceApplication inference, TrainingMap trainingMap) {
        if (groundModelInit) {
            return;
        }

        this.inference = inference;
        this.trainingMap = trainingMap;

        if (Options.WLA_RANDOM_WEIGHTS.getBoolean()) {
            initRandomWeights();
        }

        postInitGroundModel();

        groundModelInit = true;
    }

    private void initRandomWeights() {
        log.trace("Randomly Weighted Rules:");
        for (WeightedRule rule : mutableRules) {
            rule.setWeight(RandUtils.nextFloat());
            log.trace("    " + rule.toString());
        }
    }

    /**
     * A convenient place for children to do additional ground model initialization.
     */
    protected void postInitGroundModel() {}

    /**
     * Run inference.
     * Note that atoms will not be committed to the database until weight learning is closed.
     * A explicit call can be made to the inference application to override this functionality.
     */
    @SuppressWarnings("unchecked")
    protected void computeMPEState() {
        if (inMPEState) {
            return;
        }

        inference.inference(false, true);
        inMPEState = true;
    }

    @Override
    public void close() {
        if (inference != null) {
            inference.commit();
            inference.close();
            inference = null;
        }

        trainingMap = null;
        rvDB = null;
        observedDB = null;
    }

    /**
     * Construct a weight learning application given the data.
     * Look for a constructor like: (List<Rule>, Database (rv), Database (observed)).
     */
    public static WeightLearningApplication getWLA(String name, List<Rule> rules,
            Database randomVariableDatabase, Database observedTruthDatabase) {
        String className = Reflection.resolveClassName(name);
        if (className == null) {
            throw new IllegalArgumentException("Could not find class: " + name);
        }

        Class<? extends WeightLearningApplication> classObject = null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends WeightLearningApplication> uncheckedClassObject = (Class<? extends WeightLearningApplication>)Class.forName(className);
            classObject = uncheckedClassObject;
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Could not find class: " + className, ex);
        }

        Constructor<? extends WeightLearningApplication> constructor = null;
        try {
            constructor = classObject.getConstructor(List.class, Database.class, Database.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("No suitable constructor found for weight learner: " + className + ".", ex);
        }

        WeightLearningApplication wla = null;
        try {
            wla = constructor.newInstance(rules, randomVariableDatabase, observedTruthDatabase);
        } catch (InstantiationException ex) {
            throw new RuntimeException("Unable to instantiate weight learner (" + className + ")", ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Insufficient access to constructor for " + className, ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Error thrown while constructing " + className, ex);
        }

        return wla;
    }
}
