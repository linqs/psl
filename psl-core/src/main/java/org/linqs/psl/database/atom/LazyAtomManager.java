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
package org.linqs.psl.database.atom;

import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.Formula2SQL;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;

import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SetOperationQuery;
import com.healthmarketscience.sqlbuilder.UnionQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A persisted atom manager that will keep track of atoms that it returns, but that
 * don't actually exist (lazy atoms).
 * If activateAtoms() is called, then all lazy atoms above the activation threshold
 * (set by the ACTIVATION_THRESHOLD_KEY configuration option) will be instantiated as
 * real atoms.
 */
public class LazyAtomManager extends PersistedAtomManager {
    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "lazyatommanager";

    /**
     * The minimum value an atom must take for it to be activated.
     * Must be a float in (0,1].
     */
    public static final String ACTIVATION_THRESHOLD_KEY = CONFIG_PREFIX + ".activation";

    /**
     * Default value for ACTIVATION_THRESHOLD_KEY property.
     */
    public static final double ACTIVATION_THRESHOLD_DEFAULT = 0.01;

    private static final Logger log = LoggerFactory.getLogger(LazyAtomManager.class);

    /**
     * All the ground atoms that have been seen, but not instantiated.
     */
    private final Set<RandomVariableAtom> lazyAtoms;
    private final double activation;

    public LazyAtomManager(Database db) {
        super(db);

        if (!(db instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("LazyAtomManagers require RDBMSDatabase.");
        }

        lazyAtoms = new HashSet<RandomVariableAtom>();
        activation = Config.getDouble(ACTIVATION_THRESHOLD_KEY, ACTIVATION_THRESHOLD_DEFAULT);

        if (activation <= 0 || activation > 1) {
            throw new IllegalArgumentException(
                    "Activation threshold must be in (0,1]." +
                    " Got: " + activation + ".");
        }
    }

    @Override
    public synchronized GroundAtom getAtom(Predicate predicate, Constant... arguments) {
        GroundAtom atom = db.getAtom(predicate, arguments);
        if (!(atom instanceof RandomVariableAtom)) {
            return atom;
        }
        RandomVariableAtom rvAtom = (RandomVariableAtom)atom;

        // If this atom has not been persisted, it is lazy.
        if (!rvAtom.getPersisted()) {
            lazyAtoms.add(rvAtom);
        }

        return rvAtom;
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // LazyAtomManger does not have access exceptions.
    }

    public Set<RandomVariableAtom> getLazyAtoms() {
        return Collections.unmodifiableSet(lazyAtoms);
    }

    /**
     * Compute the number of lazy atoms that can be activated at this moment.
     */
    public int countActivatableAtoms() {
        int count = 0;
        for (RandomVariableAtom atom : lazyAtoms) {
            if (atom.getValue() >= activation) {
                count++;
            }
        }
        return count;
    }

    /**
     * Activate any lazy atoms above the threshold.
     * @param rules the potential rules to look for lazy atoms in.
     * @return the number of lazy atoms instantiated.
     */
    public int activateAtoms(List<Rule> rules, GroundRuleStore groundRuleStore) {
        if (lazyAtoms.size() == 0) {
            return 0;
        }

        Set<RandomVariableAtom> toActivate = new HashSet<RandomVariableAtom>();

        Iterator<RandomVariableAtom> lazyAtomIterator = lazyAtoms.iterator();
        while (lazyAtomIterator.hasNext()) {
            RandomVariableAtom atom = lazyAtomIterator.next();

            if (atom.getValue() >= activation) {
                toActivate.add(atom);
                lazyAtomIterator.remove();
            }
        }

        activate(toActivate, rules, groundRuleStore);
        return toActivate.size();
    }

    /**
     * Activate a specific set of lazy atoms.
     * Any passed in atom that is not part of this manager's lazy atom set will be ignored.
     * The caller must be sure that these given atoms came from the same database managed by this AtomManager.
     * @return the number of lazy atoms instantiated.
     */
    public int activateAtoms(Set<RandomVariableAtom> atoms, List<Rule> rules, GroundRuleStore groundRuleStore) {
        // Ensure that all the atoms are valid.
        Iterator<RandomVariableAtom> atomIterator = atoms.iterator();
        while (atomIterator.hasNext()) {
            RandomVariableAtom atom = atomIterator.next();

            // Remove atoms that are not lazy.
            if (!lazyAtoms.contains(atom)) {
                atomIterator.remove();
            }
        }

        activate(atoms, rules, groundRuleStore);
        return atoms.size();
    }

    private void activate(Set<RandomVariableAtom> toActivate, List<Rule> rules, GroundRuleStore groundRuleStore) {
        // First commit the atoms to the database.
        db.commit(toActivate, Partition.LAZY_PARTITION_ID);

        // Also ensure that the activated atoms are now considered "persisted" by the atom manager.
        addToPersistedCache(toActivate);

        // Now, we need to do a partial regrounding with the activated atoms.

        // Collect the specific predicates that are targets in this lazy batch
        // and the rules associated with those predicates.
        Set<StandardPredicate> lazyPredicates = getLazyPredicates(toActivate);
        Set<Rule> lazyRules = getLazyRules(rules, lazyPredicates);

        for (Rule lazyRule : lazyRules) {
            // We will deal with these rules after we move the lazy atoms to the write partition.
            if (lazyRule.supportsGroundingQueryRewriting()) {
                lazySimpleGround(lazyRule, lazyPredicates, groundRuleStore);
            }
        }

        // Move all the new atoms out of the lazy partition and into the write partition.
        for (StandardPredicate lazyPredicate : lazyPredicates) {
            db.moveToWritePartition(lazyPredicate, Partition.LAZY_PARTITION_ID);
        }

        // Since complex aritmetic rules require a full regound, we need to do them
        // after we move the atoms to the write partition.
        for (Rule lazyRule : lazyRules) {
            if (!lazyRule.supportsGroundingQueryRewriting()) {
                lazyComplexGround((AbstractArithmeticRule)lazyRule, groundRuleStore);
            }
        }
    }

    /**
     * Complex arithmetic rules (ones with summations) require FULL regrounding.
     * Will will drop all the ground rules originating from this rule and reground.
     */
    private void lazyComplexGround(AbstractArithmeticRule rule, GroundRuleStore groundRuleStore) {
        // Remove all existing ground rules.
        groundRuleStore.removeGroundRules(rule);

        // Reground.
        rule.groundAll(this, groundRuleStore);
    }

    private void lazySimpleGround(Rule rule, Set<StandardPredicate> lazyPredicates, GroundRuleStore groundRuleStore) {
        if (!rule.supportsGroundingQueryRewriting()) {
            throw new UnsupportedOperationException("Rule requires full regrounding: " + rule);
        }

        Formula formula = rule.getRewritableGroundingFormula(this);
        ResultList groundingResults = getLazyGroundingResults(formula, lazyPredicates);
        if (groundingResults == null) {
            return;
        }

        List<GroundRule> groundRules = new ArrayList<GroundRule>();
        for (int i = 0; i < groundingResults.size(); i++) {
            rule.ground(groundingResults.get(i), groundingResults.getVariableMap(), this, groundRules);
            for (GroundRule groundRule : groundRules) {
                if (groundRule != null) {
                    groundRuleStore.addGroundRule(groundRule);
                }
            }
        }
    }

    private ResultList getLazyGroundingResults(Formula formula, Set<StandardPredicate> lazyPredicates) {
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
        return lazyGround(formula, lazyTargets);
    }

    private ResultList lazyGround(Formula formula, List<Atom> lazyTargets) {
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

    private Set<StandardPredicate> getLazyPredicates(Set<RandomVariableAtom> toActivate) {
        Set<StandardPredicate> lazyPredicates = new HashSet<StandardPredicate>();
        for (Atom atom : toActivate) {
            if (atom.getPredicate() instanceof StandardPredicate) {
                lazyPredicates.add((StandardPredicate)atom.getPredicate());
            }
        }
        return lazyPredicates;
    }

    private Set<Rule> getLazyRules(List<Rule> rules, Set<StandardPredicate> lazyPredicates) {
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
}
