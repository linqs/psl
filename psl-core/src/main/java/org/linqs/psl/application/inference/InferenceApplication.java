/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.DeepPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * All the tools necessary to perform inference.
 * An inference application owns the ground atoms (Database/AtomStore), the terms (TermStore),
 * and how inference is actually performed (Reasoner).
 * As such, the inference application is the top level authority for these items and methods.
 * For example, inference may set the value of the random variables on construction.
 */
public abstract class InferenceApplication implements ModelApplication {
    private static final Logger log = Logger.getLogger(InferenceApplication.class);

    protected List<Rule> rules;
    protected Database database;
    protected Reasoner reasoner;
    protected InitialValue initialValue;

    protected boolean skipInference;
    protected boolean normalizeWeights;
    protected boolean relaxHardConstraints;
    protected float relaxationMultiplier;
    protected boolean relaxationSquared;

    protected TermStore termStore;

    private boolean atomsCommitted;

    protected InferenceApplication(List<Rule> rules, Database database) {
        this(rules, database, Options.INFERENCE_RELAX.getBoolean());
    }

    protected InferenceApplication(List<Rule> rules, Database database, boolean relaxHardConstraints) {
        this.rules = new ArrayList<Rule>(rules);
        this.database = database;
        this.atomsCommitted = false;

        this.initialValue = InitialValue.valueOf(Options.INFERENCE_INITIAL_VARIABLE_VALUE.getString());
        this.skipInference = Options.INFERENCE_SKIP_INFERENCE.getBoolean();
        this.normalizeWeights = Options.INFERENCE_NORMALIZE_WEIGHTS.getBoolean();
        this.relaxHardConstraints = relaxHardConstraints;
        this.relaxationMultiplier = Options.INFERENCE_RELAX_MULTIPLIER.getFloat();
        this.relaxationSquared = Options.INFERENCE_RELAX_SQUARED.getBoolean();

        initialize();
    }

    /**
     * Get objects ready for inference.
     * This will call into the abstract method completeInitialize().
     */
    protected void initialize() {
        initializeAtoms();

        if (normalizeWeights) {
            normalizeWeights();
        }

        if (relaxHardConstraints) {
            relaxHardConstraints();
        }

        reasoner = createReasoner();
        termStore = createTermStore();

        completeInitialize();
    }

    protected Reasoner createReasoner() {
        return new ADMMReasoner();
    }

    public TermStore createTermStore() {
        return new ADMMTermStore(database.getAtomStore());
    }

    /**
     * Complete the initialization process.
     * Most of the infrastructure will have been constructed.
     * The child is responsible for populating the ground rule store.
     */
    protected void completeInitialize() {
        log.info("Grounding out model.");
        long termCount = Grounding.groundAll(rules, termStore, database);
        log.info("Grounding complete.");
        log.debug("Generated {} terms.", termCount);
    }

    public void loadDeepPredicates(String application) {
        log.info("Loading deep predicates.");
        for (Predicate predicate : Predicate.getAll()) {
            if (predicate instanceof DeepPredicate) {
                ((DeepPredicate)predicate).initDeepPredicate(database.getAtomStore(), application);
                ((DeepPredicate)predicate).predictDeepModel(application.equals("learning"));
            }
        }
    }

    /**
     * Alias for inference() with committing atoms.
     */
    public double inference() {
        return inference(true, false);
    }

    /**
     * Alias for inference() without evaluation.
     */
    public double inference(boolean commitAtoms, boolean reset) {
        return inference(commitAtoms, reset, null, null);
    }

    /**
     * Minimize the total weighted incompatibility of the atoms according to the rules,
     * and optionally commit the updated atoms back to the database.
     *
     * All RandomVariableAtoms which the model might access must be persisted in the Database.
     *
     * If available, the evaluations and database (converted into a TrainingMap)
     * will be presented to reasoners to use during optimization.
     *
     * @return the final objective of the reasoner.
     */
    public double inference(boolean commitAtoms, boolean reset, List<EvaluationInstance> evaluations, Database truthDatabase) {
        if (reset) {
            initializeAtoms();

            if (termStore != null) {
                termStore.reset();
            }
        }

        if (skipInference) {
            log.info("Skipping inference.");
            return -1.0;
        }

        TrainingMap trainingMap = null;
        if (truthDatabase != null && evaluations != null && evaluations.size() > 0) {
            trainingMap = new TrainingMap(database, truthDatabase);
        }

        log.info("Beginning inference.");
        double objective = internalInference(evaluations, trainingMap);
        log.info("Inference complete.");
        atomsCommitted = false;

        // Commits the RandomVariableAtoms back to the Database.
        if (commitAtoms) {
            commit();
        }

        return objective;
    }

    /**
     * The implementation of the full inference by each class.
     *
     * @return the final objective of the reasoner.
     */
    @SuppressWarnings("unchecked")
    protected double internalInference(List<EvaluationInstance> evaluations, TrainingMap trainingMap) {
        return reasoner.optimize(termStore, evaluations, trainingMap);
    }

    public Reasoner getReasoner() {
        return reasoner;
    }

    public TermStore getTermStore() {
        return termStore;
    }

    public void setTermStore(TermStore termStore) {
        this.termStore = termStore;
    }

    public Database getDatabase() {
        return database;
    }

    /**
     * Set a budget (given as a proportion of the max budget).
     */
    public void setBudget(double budget) {
        reasoner.setBudget(budget);
    }

    /**
     * Set all the random variable atoms to the initial value for this inference application.
     */
    public void initializeAtoms() {
        for (RandomVariableAtom atom : database.getAtomStore().getRandomVariableAtoms()) {
            atom.setValue(initialValue.getVariableValue(atom));
        }
    }

    public void setInitialValue(InitialValue initialValue) {
        this.initialValue = initialValue;
    }

    /**
     * Commit the results of inference to the database.
     */
    public void commit() {
        if (atomsCommitted) {
            return;
        }

        log.info("Writing results to Database.");
        database.getAtomStore().commit();
        log.info("Results committed to database.");

        atomsCommitted = true;
    }

    @Override
    public void close() {
        if (termStore != null) {
            termStore.close();
            termStore = null;
        }

        if (reasoner != null) {
            reasoner.close();
            reasoner = null;
        }

        rules = null;
        database = null;
    }

    /**
     * Normalize all weights to be in [0, 1].
     */
    protected void normalizeWeights() {
        float max = 0.0f;
        boolean hasWeightedRule = false;

        for (WeightedRule rule : IteratorUtils.filterClass(rules, WeightedRule.class)) {
            float weight = rule.getWeight();
            if (!hasWeightedRule || weight > max) {
                max = weight;
                hasWeightedRule = true;
            }
        }

        if (!hasWeightedRule) {
            return;
        }

        for (WeightedRule rule : IteratorUtils.filterClass(rules, WeightedRule.class)) {
            float oldWeight = rule.getWeight();

            float newWeight = 1.0f;
            if (!MathUtils.isZero(max)) {
                newWeight = oldWeight / max;
            }

            log.debug("Normalizing rule weight (old weight: {}, new weight: {}): {}", oldWeight, newWeight, rule);
            rule.setWeight(newWeight);
        }
    }

    /**
     * Relax hard constraints into weighted rules.
     */
    protected void relaxHardConstraints() {
        float largestWeight = 0.0f;
        boolean hasUnweightedRule = false;

        for (Rule rule : rules) {
            if (rule instanceof WeightedRule) {
                float weight = ((WeightedRule)rule).getWeight();
                if (weight > largestWeight) {
                    largestWeight = weight;
                }
            } else {
                hasUnweightedRule = true;
            }
        }

        if (!hasUnweightedRule) {
            return;
        }

        float weight = Math.max(1.0f, largestWeight * relaxationMultiplier);

        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i) instanceof UnweightedRule) {
                log.debug("Relaxing hard constraint (weight: {}, squared: {}): {}", weight, relaxationSquared, rules.get(i));
                rules.set(i, ((UnweightedRule)rules.get(i)).relax(weight, relaxationSquared));
            }
        }
    }

    /**
     * Construct an inference application given the data.
     * Look for a constructor like: (List<Rule>, Database).
     */
    public static InferenceApplication getInferenceApplication(String className, List<Rule> rules, Database database) {
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
            constructor = classObject.getConstructor(List.class, Database.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("No suitable constructor (List<Rules>, Database) found for inference application: " + className + ".", ex);
        }

        InferenceApplication inferenceApplication = null;
        try {
            inferenceApplication = constructor.newInstance(rules, database);
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
