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
package org.linqs.psl.grounding;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.Formula2SQL;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;

import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SetOperationQuery;
import com.healthmarketscience.sqlbuilder.UnionQuery;
import org.linqs.psl.util.IteratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static utilities for performing partial grounding.
 */
public class PartialGrounding {
    private static final Logger log = LoggerFactory.getLogger(PartialGrounding.class);

    // Static only.
    private PartialGrounding() {}

    /**
     * Get the set of all standard predicates that will be used forming grounding queries.
     */
    public static Set<StandardPredicate> getPartialPredicates(Set<? extends GroundAtom> atoms) {
        Set<StandardPredicate> partialPredicates = new HashSet<StandardPredicate>();
        for (GroundAtom atom : atoms) {
            if (atom.getPredicate() instanceof StandardPredicate) {
                partialPredicates.add((StandardPredicate)atom.getPredicate());
            }
        }

        return partialPredicates;
    }

    public static Set<Rule> getPartialRules(List<Rule> rules, Set<StandardPredicate> partialPredicates) {
        Set<Rule> partialRules = new HashSet<Rule>();

        for (Rule rule : rules) {
            if (rule instanceof AbstractLogicalRule) {
                // Note that we check for atoms not in the base formula, but in the
                // query formula for the DNF because negated atoms will not
                // be considered.
                for (Atom atom : ((AbstractLogicalRule)rule).getNegatedDNF().getQueryFormula().getAtoms(new HashSet<Atom>())) {
                    if (partialPredicates.contains(atom.getPredicate())) {
                        partialRules.add(rule);
                        break;
                    }
                }
            } else if (rule instanceof AbstractArithmeticRule) {
                // Note that we do not bother checking the filters since those predicates must be closed.
                for (Predicate predicate : ((AbstractArithmeticRule)rule).getBodyPredicates()) {
                    if (partialPredicates.contains(predicate)) {
                        partialRules.add(rule);
                        break;
                    }
                }
            } else {
                throw new IllegalStateException("Unknown rule type: " + rule.getClass().getName());
            }
        }

        return partialRules;
    }

    /**
     * Complex arithmetic rules (ones with summations) require FULL regrounding.
     * Will drop all the ground rules originating from this rule and reground.
     */
    public static void partialComplexGround(AbstractArithmeticRule rule, GroundRuleStore groundRuleStore,
                                            AtomManager atomManager) {
        log.trace(String.format("Complex partial grounding on rule [%s]", rule));

        // Remove all existing ground rules.
        groundRuleStore.removeGroundRules(rule);

        // Reground.
        rule.groundAll(atomManager, groundRuleStore);
    }

    public static void partialSimpleGround(Rule rule, Set<StandardPredicate> partialPredicates,
                                           GroundRuleStore groundRuleStore, AtomManager atomManager) {
        Database db = atomManager.getDatabase();
        if (!rule.supportsGroundingQueryRewriting()) {
            throw new UnsupportedOperationException("Rule requires full regrounding: " + rule);
        }

        Formula formula = rule.getRewritableGroundingFormula();
        QueryResultIterable groundingResults = getPartialGroundingResults(formula, partialPredicates, db);
        if (groundingResults == null) {
            return;
        }

        log.trace(String.format("Simple partial grounding on rule: [%s], formula: [%s]", rule, formula));

        Iterator<Constant[]> groundingResultsIterator = groundingResults.iterator();
        List<GroundRule> groundRules = new ArrayList<GroundRule>();
        while (groundingResultsIterator.hasNext()){
            Constant[] constants = groundingResultsIterator.next();
            groundRules.clear();
            rule.ground(constants, groundingResults.getVariableMap(), atomManager, groundRules);
            for (GroundRule groundRule : groundRules) {
                if (groundRule != null) {
                    groundRuleStore.addGroundRule(groundRule);
                }
            }
        }
    }

    private static List<Atom> getPartialTargetAtoms(Formula formula, Set<StandardPredicate> partialPredicates) {
        List<Atom> partialTargetAtoms = new ArrayList<Atom>();

        // For every mention of a partial predicate in this rule, we will need to get the grounding query
        // with that specific predicate mention being the partial atom.
        for (Atom atom : formula.getAtoms(new HashSet<Atom>())) {
            if (partialPredicates.contains(atom.getPredicate())) {
                partialTargetAtoms.add(atom);
            }
        }

        return partialTargetAtoms;
    }

    public static QueryResultIterable getPartialGroundingResults(Rule rule, Set<StandardPredicate> partialPredicates, Database db) {
        if (!rule.supportsGroundingQueryRewriting()) {
            throw new UnsupportedOperationException("Rule requires full regrounding: " + rule);
        }

        Formula formula = rule.getRewritableGroundingFormula();
        return getPartialGroundingResults(formula, partialPredicates, db);
    }

    private static QueryResultIterable getPartialGroundingResults(Formula formula, Set<StandardPredicate> partialPredicates,
                                                                  Database db) {
        List<Atom> partialTargetAtoms = getPartialTargetAtoms(formula, partialPredicates);

        if (partialTargetAtoms.size() == 0) {
            return null;
        } else {
            // Do the grounding query for this rule.
            return partialGroundingIterable(formula, partialTargetAtoms, db);
        }
    }

    private static QueryResultIterable partialGroundingIterable(Formula formula, List<Atom> allPartialTargetAtoms, Database db) {
        if (allPartialTargetAtoms.size() == 0) {
            throw new IllegalArgumentException();
        }

        RDBMSDatabase relationalDB = ((RDBMSDatabase)db);
        List<SelectQuery> queries = new ArrayList<SelectQuery>();
        VariableTypeMap varTypes = formula.collectVariables(new VariableTypeMap());
        Map<Variable, Integer> projectionMap = null;
        List<Atom> partialTargetAtoms = new ArrayList<Atom>();
        Iterable<boolean[]> subsetIterable = null;
        int partialTargetIndex = 0;
        int numPartialTargets = 0;

        if (Options.PARTIAL_GROUNDING_POWERSET.getBoolean()) {
            subsetIterable = IteratorUtils.powerset(allPartialTargetAtoms.size());
        } else {
            // Build subsetIterable so that only one atom can come from a special partition at a time.
            subsetIterable = new ArrayList<boolean[]>();
            for (int i = 0; i < allPartialTargetAtoms.size(); i++) {
                boolean[] subset = new boolean[allPartialTargetAtoms.size()];
                subset[i] = true;
                ((List<boolean[]>)subsetIterable).add(subset);
            }
        }

        for (boolean[] partialTargetAtomSubset : subsetIterable) {
            partialTargetAtoms.clear();
            partialTargetIndex = 0;
            numPartialTargets = 0;

            // Build partialTargetAtoms atom array.
            for (boolean bool : partialTargetAtomSubset) {
                if (bool) {
                    partialTargetAtoms.add(allPartialTargetAtoms.get(partialTargetIndex));
                    numPartialTargets++;
                }
                partialTargetIndex++;
            }
            // Skip empty-set subset of allPartialTargetAtoms.
            if (numPartialTargets == 0) {
                continue;
            }

            Formula2SQL sqler = new Formula2SQL(varTypes.getVariables(), relationalDB, false, partialTargetAtoms);
            queries.add(sqler.getQuery(formula));

            if (projectionMap == null) {
                projectionMap = sqler.getProjectionMap();
            }
        }

        if (queries.size() == 0) {
            return null;
        }

        // This falls back to a normal SELECT when there is only one.
        UnionQuery union = new UnionQuery(SetOperationQuery.Type.UNION_ALL, queries.toArray(new SelectQuery[0]));
        return relationalDB.executeQueryIterator(projectionMap, varTypes, union.validate().toString());
    }
}
