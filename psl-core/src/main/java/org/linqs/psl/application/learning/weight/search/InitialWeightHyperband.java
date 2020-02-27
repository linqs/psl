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
package org.linqs.psl.application.learning.weight.search;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Hyperband, but the weights chosen are used as initial weights for further weight learning.
 */
public class InitialWeightHyperband extends Hyperband {
    private static final Logger log = LoggerFactory.getLogger(InitialWeightHyperband.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "initialweighthyperband";

    /**
     * The internal weight learning application (WLA) to use.
     * Should actually be a VotedPerceptron.
     */
    public static final String INTERNAL_WLA_KEY = CONFIG_PREFIX + ".internalwla";
    public static final String INTERNAL_WLA_DEFAULT = MaxLikelihoodMPE.class.getName();

    private VotedPerceptron internalWLA;

    public InitialWeightHyperband(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public InitialWeightHyperband(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        // TODO(eriq): Can we generalizse to actual WLA?
        String wlaName = Config.getString(INTERNAL_WLA_KEY, INTERNAL_WLA_DEFAULT);
        this.internalWLA = (VotedPerceptron)WeightLearningApplication.getWLA(wlaName, rules, rvDB, observedDB);
    }

    @Override
    protected void postInitGroundModel() {
        super.postInitGroundModel();

        // Init the internal WLA.
        internalWLA.initGroundModel(
            this.reasoner,
            this.groundRuleStore,
            this.termStore,
            this.termGenerator,
            this.atomManager,
            this.trainingMap
        );
    }

    @Override
    public void setBudget(double budget) {
        internalWLA.setBudget(budget);
        super.setBudget(budget);
    }

    @Override
    protected double run(double[] weights) {
        // Just have the internal WLA learn and run (eval) in those learn weights.
        internalWLA.learn();

        // Save the learned weights.
        for (int i = 0; i < mutableRules.size(); i++) {
            weights[i] = mutableRules.get(i).getWeight();
        }

        return super.run(weights);
    }

    @Override
    public void close() {
        super.close();
        internalWLA.close();
    }
}
