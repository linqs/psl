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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.ADMMTermGenerator;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.Reflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract class for learning the weights of weighted mutableRules from data for a model.
 * All non-abstract children should have a constructor that takes:
 * (List<Rule>, Database (rv), Database (observed)).
 */
public abstract class WeightLearningApplication implements ModelApplication {
    private static final Logger log = LoggerFactory.getLogger(WeightLearningApplication.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "weightlearning";

    /**
     * The class to use for inference.
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

    /**
     * An evalautor capable of producing a score for the current weight configuration.
     * Child methods may use this at their own discrection.
     * This is only used for logging/information, and not for gradients.
     */
    public static final String EVALUATOR_KEY = CONFIG_PREFIX + ".evaluator";
    public static final String EVALUATOR_DEFAULT = ContinuousEvaluator.class.getName();

    /**
     * Randomize weights before running.
     * The randomization will happen during ground model initialization.
     */
    public static final String RANDOM_WEIGHTS_KEY = CONFIG_PREFIX + ".randomweights";
    public static final boolean RANDOM_WEIGHTS_DEFAULT = false;
    public static final int MAX_RANDOM_WEIGHT = 100;

    public static final int MIN_ADMM_STEPS = 3;

    protected Database rvDB;
    protected Database observedDB;

    /**
     * An atom manager on top of the rvDB.
     */
    protected PersistedAtomManager atomManager;

    protected List<Rule> allRules;
    protected List<WeightedRule> mutableRules;

    /**
     * Corresponds 1-1 with mutableRules.
     */
    protected double[] observedIncompatibility;
    protected double[] expectedIncompatibility;

    protected TrainingMap trainingMap;

    protected Reasoner reasoner;
    protected GroundRuleStore groundRuleStore;
    protected TermGenerator termGenerator;
    protected TermStore termStore;

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

        observedIncompatibility = new double[mutableRules.size()];
        expectedIncompatibility = new double[mutableRules.size()];

        groundModelInit = false;
        inMPEState = false;

        evaluator = (Evaluator)Config.getNewObject(EVALUATOR_KEY, EVALUATOR_DEFAULT);
    }

    /**
     * Learns new weights.
     * The {@link RandomVariableAtom RandomVariableAtoms} in the distribution are those
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
        if (reasoner instanceof ADMMReasoner) {
            int maxIterations = Config.getInt(ADMMReasoner.MAX_ITER_KEY, ADMMReasoner.MAX_ITER_DEFAULT);
            int iterations = (int)Math.ceil(maxIterations * budget);
            ((ADMMReasoner)reasoner).setMaxIter((int)Math.max(MIN_ADMM_STEPS, iterations));

            if (termStore instanceof ADMMTermStore) {
                ((ADMMTermStore)termStore).resetLocalVairables();
            }
        }
    }

    public GroundRuleStore getGroundRuleStore() {
        return groundRuleStore;
    }

    /**
     * Initialize all the infrastructure dealing with the ground model.
     * Children should favor overriding postInitGroundModel() instead of this.
     */
    protected void initGroundModel() {
        if (groundModelInit) {
            return;
        }

        PersistedAtomManager atomManager = createAtomManager();

        // Ensure all targets from the observed (truth) database exist in the RV database.
        ensureTargets(atomManager);

        GroundRuleStore groundRuleStore = (GroundRuleStore)Config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);

        log.info("Grounding out model.");
        int groundCount = Grounding.groundAll(allRules, atomManager, groundRuleStore);

        initGroundModel(atomManager, groundRuleStore);
    }

    /**
     * Init the ground model using an already populated ground rule store.
     * All the targets from the obserevd database should already exist in the RV database
     * before this ground rule store was populated.
     * This means that this variant will not call ensureTargets() (unlike the no parameter variant).
     */
    public void initGroundModel(GroundRuleStore groundRuleStore) {
        if (groundModelInit) {
            return;
        }

        initGroundModel(createAtomManager(), groundRuleStore);
    }

    private void initGroundModel(PersistedAtomManager atomManager, GroundRuleStore groundRuleStore) {
        if (groundModelInit) {
            return;
        }

        TermStore termStore = (TermStore)Config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
        TermGenerator termGenerator = (TermGenerator)Config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);

        log.debug("Initializing objective terms for {} ground rules.", groundRuleStore.size());
        termStore.ensureVariableCapacity(atomManager.getCachedRVACount());
        @SuppressWarnings("unchecked")
        int termCount = termGenerator.generateTerms(groundRuleStore, termStore);
        log.debug("Generated {} objective terms from {} ground rules.", termCount, groundRuleStore.size());

        TrainingMap trainingMap = new TrainingMap(atomManager, observedDB);
        Reasoner reasoner = (Reasoner)Config.getNewObject(REASONER_KEY, REASONER_DEFAULT);

        initGroundModel(reasoner, groundRuleStore, termStore, termGenerator, atomManager, trainingMap);
    }

    /**
     * Pass in all the ground model infrastructure.
     * The caller should be careful calling this method instead of the other variant.
     * Children should favor overriding postInitGroundModel() instead of this.
     */
    public void initGroundModel(
            Reasoner reasoner, GroundRuleStore groundRuleStore,
            TermStore termStore, TermGenerator termGenerator,
            PersistedAtomManager atomManager, TrainingMap trainingMap) {
        if (groundModelInit) {
            return;
        }

        this.reasoner = reasoner;
        this.groundRuleStore = groundRuleStore;
        this.termStore = termStore;
        this.termGenerator = termGenerator;
        this.atomManager = atomManager;
        this.trainingMap = trainingMap;

        if (Config.getBoolean(RANDOM_WEIGHTS_KEY, RANDOM_WEIGHTS_DEFAULT)) {
            initRandomWeights();
        }

        postInitGroundModel();

        groundModelInit = true;
    }

    private void initRandomWeights() {
        log.trace("Randomly Weighted Rules:");
        for (WeightedRule rule : mutableRules) {
            rule.setWeight(RandUtils.nextInt(MAX_RANDOM_WEIGHT) + 1);
            log.trace("    " + rule.toString());
        }
    }

    /**
     * A convenient place for children to do additional ground model initialization.
     */
    protected void postInitGroundModel() {}

    @SuppressWarnings("unchecked")
    protected void computeMPEState() {
        if (inMPEState) {
            return;
        }

        termStore.clear();
        termStore.ensureVariableCapacity(atomManager.getCachedRVACount());
        termGenerator.generateTerms(groundRuleStore, termStore);

        reasoner.optimize(termStore);

        inMPEState = true;
    }

    /**
     * Compute the incompatibility in the model using the labels (truth values) from the observed (truth) database.
     * This method is responsible for filling the observedIncompatibility member variable.
     * This may call setLabeledRandomVariables() and not reset any ground atoms to their original value.
     *
     * The default implementation just calls setLabeledRandomVariables() and sums the incompatibility for each rule.
     */
    protected void computeObservedIncompatibility() {
        setLabeledRandomVariables();

        // Zero out the observed incompatibility first.
        for (int i = 0; i < observedIncompatibility.length; i++) {
            observedIncompatibility[i] = 0.0;
        }

        // Sums up the incompatibilities.
        for (int i = 0; i < mutableRules.size(); i++) {
            for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
                observedIncompatibility[i] += ((WeightedGroundRule)groundRule).getIncompatibility();
            }
        }
    }

    /**
     * Compute the incompatibility in the model.
     * This method is responsible for filling the expectedIncompatibility member variable.
     *
     * The default implementation is the total incompatibility in the MPE state.
     * IE, just calls computeMPEState() and then sums the incompatibility for each rule.
     */
    protected void computeExpectedIncompatibility() {
        computeMPEState();

        // Zero out the expected incompatibility first.
        for (int i = 0; i < expectedIncompatibility.length; i++) {
            expectedIncompatibility[i] = 0.0;
        }

        // Sums up the incompatibilities.
        for (int i = 0; i < mutableRules.size(); i++) {
            for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
                expectedIncompatibility[i] += ((WeightedGroundRule)groundRule).getIncompatibility();
            }
        }
    }

    /**
     * Internal method for computing the loss at the current point before taking a step.
     * Child methods may override.
     *
     * The default implementation just sums the product of the difference between the expected and observed incompatibility.
     *
     * @return current learning loss
     */
    public double computeLoss() {
        double loss = 0.0;
        for (int i = 0; i < mutableRules.size(); i++) {
            loss += mutableRules.get(i).getWeight() * (observedIncompatibility[i] - expectedIncompatibility[i]);
        }

        return loss;
    }

    @Override
    public void close() {
        if (groundRuleStore != null) {
            groundRuleStore.close();
            groundRuleStore = null;
        }

        if (termStore != null) {
            termStore.close();
            termStore = null;
        }

        if (reasoner != null) {
            reasoner.close();
            reasoner = null;
        }

        termGenerator = null;
        trainingMap = null;
        atomManager = null;
        rvDB = null;
        observedDB = null;
    }

    /**
     * Set RandomVariableAtoms with training labels to their observed values.
     */
    protected void setLabeledRandomVariables() {
        inMPEState = false;

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getLabelMap().entrySet()) {
            entry.getKey().setValue(entry.getValue().getValue());
        }
    }

    /**
     * Set all RandomVariableAtoms we know of to their default values.
     */
    protected void setDefaultRandomVariables() {
        inMPEState = false;

        for (RandomVariableAtom atom : trainingMap.getLabelMap().keySet()) {
            atom.setValue(0.0f);
        }

        for (RandomVariableAtom atom : trainingMap.getLatentVariables()) {
            atom.setValue(0.0f);
        }
    }

    /**
     * Create an atom manager on top of the RV database.
     * This allows an opportunity for subclasses to create a special manager.
     */
    protected PersistedAtomManager createAtomManager() {
        return new PersistedAtomManager(rvDB);
    }

    /**
     * Make sure that all targets from the observed database exist in the RV database.
     */
    private void ensureTargets(PersistedAtomManager atomManager) {
        // Iterate through all of the registered predicates in the observed.
        for (StandardPredicate predicate : observedDB.getDataStore().getRegisteredPredicates()) {
            // Ignore any closed predicates.
            if (observedDB.isClosed(predicate)) {
                continue;
            }

            // Commit the atoms into the RV databse with the default value.
            for (ObservedAtom observedAtom : observedDB.getAllGroundObservedAtoms(predicate)) {
                GroundAtom otherAtom = atomManager.getAtom(observedAtom.getPredicate(), observedAtom.getArguments());

                if (otherAtom instanceof ObservedAtom) {
                    continue;
                }

                RandomVariableAtom rvAtom = (RandomVariableAtom)otherAtom;
                rvAtom.setValue(0.0f);
            }
        }

        atomManager.commitPersistedAtoms();
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
            throw new IllegalArgumentException("No sutible constructor found for weight learner: " + className + ".", ex);
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
