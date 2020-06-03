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
package org.linqs.psl.grounding;

import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SetOperationQuery;
import com.healthmarketscience.sqlbuilder.UnionQuery;
import org.linqs.psl.database.*;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.database.rdbms.Formula2SQL;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Static utilities for common {@link Model}-grounding tasks.
 */
public class PartialGrounding {
    private static final Logger log = LoggerFactory.getLogger(PartialGrounding.class);

    // Static only.
    private PartialGrounding() {}

    public static Set<StandardPredicate> getLazyPredicates(Set<RandomVariableAtom> toActivate) {
        Set<StandardPredicate> lazyPredicates = new HashSet<StandardPredicate>();
        for (Atom atom : toActivate) {
            if (atom.getPredicate() instanceof StandardPredicate) {
                lazyPredicates.add((StandardPredicate)atom.getPredicate());
            }
        }
        return lazyPredicates;
    }

    public static Set<Rule> getLazyRules(List<Rule> rules, Set<StandardPredicate> lazyPredicates) {
        Set<Rule> lazyRules = new HashSet<Rule>();

        for (Rule rule : rules) {
            if (rule instanceof AbstractLogicalRule) {
                // Note that we check for atoms not in the base formula, but in the
                // query formula for the DNF because negated atoms will not
                // be considered.
                for (Atom atom : ((AbstractLogicalRule)rule).getNegatedDNF().getQueryFormula().getAtoms(new HashSet<Atom>())) {
                    if (lazyPredicates.contains(atom.getPredicate())) {
                        lazyRules.add(rule);
                        break;
                    }
                }
            } else if (rule instanceof AbstractArithmeticRule) {
                // Note that we do not bother checking the filters since those predicates must be closed.
                for (Predicate predicate : ((AbstractArithmeticRule)rule).getBodyPredicates()) {
                    if (lazyPredicates.contains(predicate)) {
                        lazyRules.add(rule);
                        break;
                    }
                }
            } else {
                throw new IllegalStateException("Unknown rule type: " + rule.getClass().getName());
            }
        }

        return lazyRules;
    }

    /**
     * Complex arithmetic rules (ones with summations) require FULL regrounding.
     * Will drop all the ground rules originating from this rule and reground.
     */
    public static void lazyComplexGround(AbstractArithmeticRule rule, GroundRuleStore groundRuleStore,
                                         AtomManager atomManager) {
        log.trace(String.format("Complex lazy grounding on rule [%s]", rule));

        // Remove all existing ground rules.
        groundRuleStore.removeGroundRules(rule);

        // Reground.
        rule.groundAll(atomManager, groundRuleStore);
    }

    public static void lazySimpleGround(Rule rule, Set<StandardPredicate> lazyPredicates,
                                        GroundRuleStore groundRuleStore, AtomManager atomManager) {
        Database db = atomManager.getDatabase();
        if (!rule.supportsGroundingQueryRewriting()) {
            throw new UnsupportedOperationException("Rule requires full regrounding: " + rule);
        }

        Formula formula = rule.getRewritableGroundingFormula(atomManager);
        ResultList groundingResults = getLazyGroundingResults(formula, lazyPredicates, db);
        if (groundingResults == null) {
            return;
        }

        log.trace(String.format("Simple lazy grounding on rule: [%s], formula: [%s]", rule, formula));

        List<GroundRule> groundRules = new ArrayList<GroundRule>();
        for (int i = 0; i < groundingResults.size(); i++) {
            groundRules.clear();
            rule.ground(groundingResults.get(i), groundingResults.getVariableMap(), atomManager, groundRules);
            for (GroundRule groundRule : groundRules) {
                if (groundRule != null) {
                    groundRuleStore.addGroundRule(groundRule);
                }
            }
        }
    }

    private static ResultList getLazyGroundingResults(Formula formula, Set<StandardPredicate> lazyPredicates,
                                                      Database db) {
        List<Atom> lazyTargets = new ArrayList<Atom>();

        // For every mention of a lazy predicate in this rule, we will need to get the grounding query
        // with that specific predicate mention being the lazy target.
        for (Atom atom : formula.getAtoms(new HashSet<Atom>())) {
            if (!lazyPredicates.contains(atom.getPredicate())) {
                continue;
            }

            lazyTargets.add(atom);
        }

        if (lazyTargets.size() == 0) {
            return null;
        }

        // Do the grounding query for this rule.
        return lazyGround(formula, lazyTargets, db);
    }

    private static ResultList lazyGround(Formula formula, List<Atom> lazyTargets, Database db) {
        if (lazyTargets.size() == 0) {
            throw new IllegalArgumentException();
        }

        RDBMSDatabase relationalDB = ((RDBMSDatabase)db);

        List<SelectQuery> queries = new ArrayList<SelectQuery>();

        VariableTypeMap varTypes = formula.collectVariables(new VariableTypeMap());
        Map<Variable, Integer> projectionMap = null;

        for (Atom lazyTarget : lazyTargets) {
            Formula2SQL sqler = new Formula2SQL(varTypes.getVariables(), relationalDB, false, lazyTarget);
            queries.add(sqler.getQuery(formula));

            if (projectionMap == null) {
                projectionMap = sqler.getProjectionMap();
            }
        }

        // This fallbacks to a normal SELECT when there is only one.
        UnionQuery union = new UnionQuery(SetOperationQuery.Type.UNION, queries.toArray(new SelectQuery[0]));
        return relationalDB.executeQuery(projectionMap, varTypes, union.validate().toString());
    }

    public static Set<Predicate> getOnlinePredicates(Set<GroundAtom> onlineAtoms) {
        Set<Predicate> onlinePredicates = new HashSet<Predicate>();
        for (Atom atom : onlineAtoms) {
            onlinePredicates.add(atom.getPredicate());
        }
        return onlinePredicates;
    }

    public static Set<Rule> getOnlineRules(List<Rule> rules, Set<Predicate> onlinePredicates) {
        Set<Rule> onlineRules = new HashSet<Rule>();

        for (Rule rule : rules) {
            if (rule instanceof AbstractLogicalRule) {
                // Note that we check for atoms not in the base formula, but in the
                // query formula for the DNF because negated atoms will not
                // be considered.
                for (Atom atom : ((AbstractLogicalRule)rule).getNegatedDNF().getQueryFormula().getAtoms(new HashSet<Atom>())) {
                    if (onlinePredicates.contains(atom.getPredicate())) {
                        onlineRules.add(rule);
                        break;
                    }
                }
            } else if (rule instanceof AbstractArithmeticRule) {
                // Note that we do not bother checking the filters since those predicates must be closed.
                for (Predicate predicate : ((AbstractArithmeticRule)rule).getBodyPredicates()) {
                    if (onlinePredicates.contains(predicate)) {
                        onlineRules.add(rule);
                        break;
                    }
                }
            } else {
                throw new IllegalStateException("Unknown rule type: " + rule.getClass().getName());
            }
        }

        return onlineRules;
    }

    public static ArrayList<GroundRule> onlineSimpleGround(Rule rule, Set<Predicate> onlinePredicates, AtomManager atomManager) {
        Database db = atomManager.getDatabase();
        if (!rule.supportsGroundingQueryRewriting()) {
            throw new UnsupportedOperationException("Rule requires full regrounding: " + rule);
        }

        Formula formula = rule.getRewritableGroundingFormula(atomManager);
        ResultList groundingResults = getOnlineGroundingResults(formula, onlinePredicates, db);
        if (groundingResults == null) {
            return null;
        }

        log.trace(String.format("Simple online grounding on rule: [%s], formula: [%s]", rule, formula));

        List<GroundRule> groundRules = new ArrayList<GroundRule>();
        ArrayList<GroundRule> groundedRules = new ArrayList<GroundRule>();
        for (int i = 0; i < groundingResults.size(); i++) {
            groundRules.clear();
            rule.ground(groundingResults.get(i), groundingResults.getVariableMap(), atomManager, groundRules);
            for (GroundRule groundRule : groundRules) {
                if (groundRule != null) {
                    for (GroundAtom atom : groundRule.getAtoms()) {
                        if (((OnlineAtomManager)atomManager).newAtoms.contains(atom)) {
                            groundedRules.add(groundRule);
                            break;
                        }
                    }
                }
            }
        }
        return groundedRules;
    }

    private static ResultList getOnlineGroundingResults(Formula formula, Set<Predicate> onlinePredicates, Database db) {
        List<Atom> onlineTargets = new ArrayList<Atom>();

        // For every mention of a online predicate in this rule, we will need to get the grounding query
        // with that specific predicate mention being the online target.
        for (Atom atom : formula.getAtoms(new HashSet<Atom>())) {
            // TODO(connor) : Changed code here, should be handled properly
            onlineTargets.add(atom);
        }

        if (onlineTargets.size() == 0) {
            return null;
        }

        // Do the grounding query for this rule.
        return partialGround(formula, onlineTargets, db);
    }

    private static ResultList partialGround(Formula formula, List<Atom> onlineTargets, Database db) {
        if (onlineTargets.size() == 0) {
            throw new IllegalArgumentException();
        }

        RDBMSDatabase relationalDB = ((RDBMSDatabase)db);

        List<SelectQuery> queries = new ArrayList<SelectQuery>();

        VariableTypeMap varTypes = formula.collectVariables(new VariableTypeMap());
        Map<Variable, Integer> projectionMap = null;

        Formula2SQL sqler = new Formula2SQL(varTypes.getVariables(), relationalDB, false, null);
        queries.add(sqler.getQuery(formula));

        if (projectionMap == null) {
            projectionMap = sqler.getProjectionMap();
        }

        // This fallbacks to a normal SELECT when there is only one.
        UnionQuery union = new UnionQuery(SetOperationQuery.Type.UNION, queries.toArray(new SelectQuery[0]));
        return relationalDB.executeQuery(projectionMap, varTypes, union.validate().toString());
    }
}

