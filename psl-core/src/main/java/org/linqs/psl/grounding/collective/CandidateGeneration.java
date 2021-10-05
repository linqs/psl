/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.grounding.collective;

import org.linqs.psl.config.Config;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.rdbms.Formula2SQL;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.BitUtils;
import org.linqs.psl.util.IteratorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generate groudning candidates for a rule.
 * The generation process is really a search process over the powerset of atoms for a rule.
 * Note that this class makes heavy use of referential equality.
 */
public class CandidateGeneration {
    private static final Logger log = LoggerFactory.getLogger(CandidateGeneration.class);

    // TODO(eriq): Send these to the config.
    private static final String SEARCH_TYPE = "SearchFringe.BoundedSearchFringe";
    private static final int SEARCH_BUDGET = 100;
    private static final double OPTIMISTIC_QUERY_COST_MULTIPLIER = 0.018;
    private static final double OPTIMISTIC_INSTANTIATION_COST_MULTIPLIER = 0.0015;
    private static final double PESSIMISTIC_QUERY_COST_MULTIPLIER = 0.020;
    private static final double PESSIMISTIC_INSTANTIATION_COST_MULTIPLIER = 0.0020;

    private final SearchFringe fringe;
    private final int budget;

    private final double optimisticQueryCostMultiplier;
    private final double pessimisticQueryCostMultiplier;
    private final double optimisticInstantiationCostMultiplier;
    private final double pessimisticInstantiationCostMultiplier;

    /**
     * A volitile set of atoms used as a working set.
     * This is just a buffer and it's use case constantly changes.
     * Once a process is done with this buffer, they must clear the buffer to indicate it is no longer in-use.
     */
    private final Set<Atom> atomBuffer;

    public CandidateGeneration() {
        fringe = new SearchFringe.BoundedSearchFringe();
        budget = SEARCH_BUDGET;

        optimisticQueryCostMultiplier = OPTIMISTIC_QUERY_COST_MULTIPLIER;
        optimisticInstantiationCostMultiplier = OPTIMISTIC_INSTANTIATION_COST_MULTIPLIER;
        pessimisticQueryCostMultiplier = PESSIMISTIC_QUERY_COST_MULTIPLIER;
        pessimisticInstantiationCostMultiplier = PESSIMISTIC_INSTANTIATION_COST_MULTIPLIER;

        // A volitile set of atoms used as a working set.
        // This is just a buffer and it's use case constantly changes.
        // Once a procedure is done with this buffer, they should clear it to indicate it is no longer in-use.
        atomBuffer = new HashSet<Atom>();
    }

    /**
     * Generate viable query candidates to minimize the execution time while trading off query size.
     * Get the top n results.
     */
    public void generateCandidates(Rule rule, RDBMSDatabase database,
            int maxResults, Collection<CandidateQuery> results) {
        search(rule.getRewritableGroundingFormula(), database);

        int count = 0;
        while (fringe.savedSize() > 0 && (maxResults <= 0 || count < maxResults)) {
            CandidateSearchNode node = fringe.popBestNode();
            if (node != null) {
                results.add(new CandidateQuery(rule, node.formula, node.optimisticCost));
                count++;
            }
        }
    }

    /**
     * Search through the candidates (limited by the budget).
     */
    private void search(Formula baseFormula, RDBMSDatabase database) {
        fringe.clear();

        // Once validated, we know that the formula is a conjunction or single atom.
        DatabaseQuery.validate(baseFormula);

        // Shortcut for priors (single atoms).
        if (baseFormula instanceof Atom) {
            fringe.push(createNode(1l, baseFormula, 1, database));
            return;
        }

        assert(atomBuffer.isEmpty());

        // Get all the atoms used in the base formula.
        baseFormula.getAtoms(atomBuffer);

        // Atoms that are removed for consideration during the search, but will be added in at the end (to all candidates).
        Set<Atom> passthrough = filterSpecialAtoms(atomBuffer);

        // A mapping of each variable present in the base formula (including passthrough) and the atoms that variable appears in.
        Map<Variable, Set<Atom>> variableUsageMapping = getAllUsedVariables(atomBuffer);

        // Get the atoms in a consistent ordering so we can create a bitmap.
        List<Atom> atoms = new ArrayList<Atom>(atomBuffer);
        Collections.sort(atoms, new Comparator<Atom>() {
            public int compare(Atom a, Atom b) {
                return a.toString().compareTo(b.toString());
            }
        });

        atomBuffer.clear();

        Set<Long> seenNodes = new HashSet<Long>();

        // A bitset representing the different atoms involved in the candidate.
        // We will typically just pull a node's long bitset into this buffer.
        boolean[] atomBits = new boolean[atoms.size()];
        for (int i = 0; i < atomBits.length; i++) {
            atomBits[i] = true;
        }

        // Start with all atoms.
        CandidateSearchNode rootNode = validateAndCreateNode(atomBits, atoms, passthrough, variableUsageMapping, database);

        fringe.push(rootNode);
        seenNodes.add(new Long(BitUtils.toBitSet(atomBits)));

        // Keep track of how many times we ask for an EXPLAIN from the database.
        // After the budget is expended, we will still finish out search on the remaining nodes, but not create any more.
        int explains = 0;

        while (fringe.size() > 0) {
            CandidateSearchNode node = fringe.pop();

            // Expand the node by trying to remove each atom (in-turn).
            BitUtils.toBits(node.atomsBitSet, atomBits);

            for (int i = 0; i < atoms.size(); i++) {
                // Skip if this atom was dropped somewhere else.
                if (!atomBits[i]) {
                    continue;
                }

                // Flip the atom.
                atomBits[i] = false;

                // Skip nodes we have seen before and totally empty candidates (no atoms on).
                // Also skip nodes if we have already exceeded our budget.
                Long bitId = Long.valueOf(BitUtils.toBitSet(atomBits));
                if ((budget <= 0 || explains <= budget) && !seenNodes.contains(bitId) && bitId.longValue() != 0) {
                    seenNodes.add(bitId);

                    CandidateSearchNode child = validateAndCreateNode(atomBits, atoms, passthrough, variableUsageMapping, database);
                    if (child != null) {
                        fringe.push(child);
                        explains++;
                    }
                }

                // Unflip the atom (so we can flip the next one).
                atomBits[i] = true;
            }
        }

        CandidateSearchNode bestNode = fringe.getBestNode();
        log.debug("Searched candidates for [{}]({}). Best candidate: [{}]({}).",
                rootNode.formula, rootNode.optimisticCost,
                bestNode.formula, bestNode.optimisticCost);
    }

    /**
     * Given the active atoms, create a search node and estimate the cost.
     * The cost is non-trivial, since a query estimate is made.
     * @return The candidate search node, or null if the candidate is invalid.
     */
    private CandidateSearchNode validateAndCreateNode(boolean[] atomBits, List<Atom> atoms,
            Set<Atom> passthrough, Map<Variable, Set<Atom>> variableUsageMapping,
            RDBMSDatabase database) {
        Formula formula = constructFormula(atomBits, atoms, passthrough);

        // Make sure that all variables are covered.
        for (Map.Entry<Variable, Set<Atom>> entry : variableUsageMapping.entrySet()) {
            boolean hasVariable = false;
            for (Atom atomWithVariable : entry.getValue()) {
                if (atomBuffer.contains(atomWithVariable)) {
                    hasVariable = true;
                    break;
                }
            }

            if (!hasVariable) {
                atomBuffer.clear();
                return null;
            }
        }

        int numAtoms = atomBuffer.size();
        atomBuffer.clear();

        return createNode(BitUtils.toBitSet(atomBits), formula, numAtoms, database);
    }

    /**
     * Bypass validation and directly create a candidate node.
     */
    private CandidateSearchNode createNode(long atomBitSet, Formula formula, int numAtoms, RDBMSDatabase database) {
        RDBMSDataStore dataStore = (RDBMSDataStore)database.getDataStore();
        String sql = Formula2SQL.getQuery(formula, database, false);
        DatabaseDriver.ExplainResult result = ((RDBMSDataStore)database.getDataStore()).getDriver().explain(sql);

        double optimisticCost =
                result.totalCost * optimisticQueryCostMultiplier
                + result.rows * optimisticInstantiationCostMultiplier * numAtoms;
        double pessimisticCost =
                result.totalCost * pessimisticQueryCostMultiplier
                + result.rows * pessimisticInstantiationCostMultiplier * numAtoms;

        return new CandidateSearchNode(atomBitSet, formula, optimisticCost, pessimisticCost);
    }

    /**
     * Construct a formula from the active atoms, and fill atomBuffer with all active (including passthrough) atoms.
     * The caller is responsible for clearing atomBuffer when it is finished with it.
     */
    private Formula constructFormula(boolean[] atomBits, List<Atom> atoms, Set<Atom> passthrough) {
        assert(atomBuffer.isEmpty());

        atomBuffer.addAll(passthrough);

        for (int i = 0; i < atomBits.length; i++) {
            if (atomBits[i]) {
                atomBuffer.add(atoms.get(i));
            }
        }

        Formula formula = null;
        if (atomBuffer.size() == 1) {
            formula = atomBuffer.iterator().next();
        } else {
            formula = new Conjunction(atomBuffer.toArray(new Formula[0]));
        }

        return formula;
    }

    private Map<Variable, Set<Atom>> getAllUsedVariables(Set<Atom> atoms) {
        Map<Variable, Set<Atom>> variables = new HashMap<Variable, Set<Atom>>();

        for (Atom atom : atoms) {
            for (Variable variable : atom.getVariables()) {
                if (!variables.containsKey(variable)) {
                    variables.put(variable, new HashSet<Atom>());
                }

                variables.get(variable).add(atom);
            }
        }

        return variables;
    }

    /**
     * Filter the passed in atoms and return the passthrough atoms.
     * Remove external functional prediates and passthrough grounding only predicates.
    */
    private Set<Atom> filterSpecialAtoms(Set<Atom> atoms) {
        Set<Atom> passthrough = new HashSet<Atom>();

        Set<Atom> removeAtoms = new HashSet<Atom>();
        for (Atom atom : atoms) {
            if (atom.getPredicate() instanceof ExternalFunctionalPredicate) {
                // Skip. These are handled at instantiation time.
                removeAtoms.add(atom);
            } else if (atom.getPredicate() instanceof GroundingOnlyPredicate) {
                // Passthrough.
                removeAtoms.add(atom);
                passthrough.add(atom);
            } else if (!(atom.getPredicate() instanceof StandardPredicate)) {
                throw new IllegalStateException("Unknown predicate type: " + atom.getPredicate().getClass().getName());
            }
        }

        atoms.removeAll(removeAtoms);
        return passthrough;
    }
}
