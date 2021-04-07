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
package org.linqs.psl.evaluation.statistics;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.StringUtils;

import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.linqs.psl.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

/**
 * Compute ranking-based statistics.
 */

public class RankingEvaluator extends Evaluator {
    private static final Logger log = LoggerFactory.getLogger(RankingEvaluator.class);

    public enum RepresentativeMetric {
        MRR
    }

    private RepresentativeMetric representative;
    private String defaultPredicate;
    private int categoryIndex;
    private double threshold;

    // These lists keep track of all atoms with predicted values
    // and all atoms in the truth database

    private Iterable<RandomVariableAtom> predictedAtoms;
    private Iterable<GroundAtom> truthAtoms;

    // we compute a map from truth atoms to
    // their corresponding target atoms
    private Map<GroundAtom, GroundAtom> truthTargetMap;

    // We partition all unobserved target atoms
    // by their categories, into ArrayLists.
    // categoryMap maps the category constants
    // to these ArrayLists.
    private Map<Constant, List<GroundAtom>> categoryMap;

    public RankingEvaluator() {
        this(RepresentativeMetric.MRR);
    }

    public RankingEvaluator(RepresentativeMetric representative) {
        this.representative = representative;
        this.categoryIndex = Options.EVAL_RANK_CATEGORY_INDEX.getInt();
        this.threshold = Options.EVAL_RANK_THRESHOLD.getDouble();
        this.categoryMap = new HashMap<Constant, List<GroundAtom>>();
        defaultPredicate = Options.EVAL_RANK_DEFAULT_PREDICATE.getString();
    }

    /*
    Grab the Constant argument of an atom in a specified position.
    */

    public Constant getArgAtPosition(GroundAtom atom, int position) {
        Constant[] args = atom.getArguments();
        return args[position];
    }

    /*
    This method filters an Iterable of atoms so that
    it only contains atoms with a specified predicate.
    */

    public static <E extends GroundAtom> Iterable<E> getPredicateAtoms(Iterable<E> iterable, StandardPredicate predicate) {
        Iterator<E> itr = iterable.iterator();
        while (itr.hasNext()) {
            if (itr.next().getPredicate() != predicate) {
                itr.remove();
            }
        }
        return iterable;
    }



    @Override
    public void compute(TrainingMap trainingMap) {
        if (defaultPredicate == null) {
            throw new UnsupportedOperationException("RankingEvaluators must have a default predicate set (through config).");
        }

        compute(trainingMap, StandardPredicate.get(defaultPredicate));
    }

    @Override
    public void compute(TrainingMap trainingMap, StandardPredicate predicate) {
        /*
        Filter the target/truth atom Iterables for later use.
        */

        predictedAtoms = getPredicateAtoms(trainingMap.getAllPredictions(), predicate);
        truthAtoms = getPredicateAtoms(trainingMap.getAllTruths(), predicate);

        /*
        Create the map from truth atoms to corresponding target
        atoms by reversing the label map from trainingMap.
        */

        truthTargetMap = new HashMap<GroundAtom, GroundAtom>();

        for (Map.Entry<GroundAtom, GroundAtom> entry : getMap(trainingMap)) {
            if (entry.getKey().getPredicate() == predicate) {
                truthTargetMap.put(entry.getValue(), entry.getKey());
            }
        }

        /*
        Partition the target atoms into ArrayLists
        according to what category they belong to.
        */

        for (GroundAtom atom : predictedAtoms) {
            Constant category = getArgAtPosition(atom, categoryIndex);

            if (!categoryMap.containsKey(category)) {
                ArrayList<GroundAtom> categoryAtomList = new ArrayList<GroundAtom>();
                categoryMap.put(category, categoryAtomList);
            }

            categoryMap.get(category).add(atom);
        }


        /*
        Sort each ArrayList and fill in our
        category-to-list-of-predicted-atoms
        map.
        */

        for (Constant category : categoryMap.keySet()) {
            Collections.sort(categoryMap.get(category));
            categoryMap.put(category, categoryMap.get(category));
        }
    }

    /*
    Gets the rank of a target atom given
    its corresponding truth atom.

    Returns 0 if a matching atom isn't
    found in the targets.
    */

    public int getAtomRank(GroundAtom atom, List<GroundAtom> atomList) {
        GroundAtom targetAtom = truthTargetMap.get(atom);
        int position = atomList.indexOf(targetAtom);

        return position + 1;
    }

    public double mrr() {

        /*
        The numerator and denominator in the
        MRR.

        rr keeps a running sum of reciprocal ranks,
        and rankedAtomCount keeps track of how many
        atoms are being ranked in this evaluation.
        */

        double rr = 0;
        int rankedAtomCount = 0;

        /*
        Iterate through the full truth set.

        If a truth atom has a value above some
        threshold, then compute the reciprocal
        rank of its matching target atom.

        Sum these reciprocal ranks and take the mean.
        */

        for(GroundAtom atom : truthAtoms) {
            if (atom.getValue() < threshold) {
                continue;
            }

            rankedAtomCount++;

            Constant category = getArgAtPosition(atom, categoryIndex);
            int rank = getAtomRank(atom, categoryMap.get(category));

            // If a truth atom doesn't have a corresponding
            // target atom, skip it

            if (rank == 0) {
                continue;
            }

            rr += 1 / (double) rank;
        }

        categoryMap.clear();
        return (double) rr / rankedAtomCount;
    }

    @Override
    public double getRepMetric() {
        switch (representative) {
            case MRR:
                return mrr();
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public double getBestRepScore() {
        switch (representative) {
            case MRR:
                return 1.0;
            default:
                throw new IllegalStateException("Unknown representative metric: " + representative);
        }
    }

    @Override
    public boolean isHigherRepBetter() {
        return true;
    }

    @Override
    public String getAllStats() {
        return String.format("MRR: %f", mrr());
    }
}
