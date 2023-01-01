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
package org.linqs.psl.grounding.collective;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.rdbms.Formula2SQL;
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
import org.linqs.psl.util.Reflection;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generate groudning candidates for a rule.
 * The generation process is really a search process over the powerset of atoms for a rule.
 * Note that this class makes heavy use of referential equality.
 */
public class CandidateGeneration {
    private static final Logger log = LoggerFactory.getLogger(CandidateGeneration.class);

    public static enum SearchType {
        BFS,
        DFS,
        UCS,
        BoundedUCS,
        BoundedDFS
    }

    public static final double CANDIDATE_SIZE_ADJUSTMENT = 1.0;
    public static final double OPTIMISTIC_QUERY_COST_MULTIPLIER = 0.018;
    public static final double OPTIMISTIC_INSTANTIATION_COST_MULTIPLIER = 0.0010;
    public static final double PESSIMISTIC_QUERY_COST_MULTIPLIER = 0.020;
    public static final double PESSIMISTIC_INSTANTIATION_COST_MULTIPLIER = 0.0020;

    private SearchType searchType;
    private int budget;

    // Share explains across all uses of this object.
    // Note that we do not try to rewrite variable names or order atoms to match explains beyond the raw formula.
    private Map<String, DatabaseDriver.ExplainResult> explains;

    public CandidateGeneration() {
        searchType = SearchType.valueOf(Options.GROUNDING_COLLECTIVE_CANDIDATE_SEARCH_TYPE.getString());
        budget = Options.GROUNDING_COLLECTIVE_CANDIDATE_SEARCH_BUDGET.getInt();

        explains = new ConcurrentHashMap<String, DatabaseDriver.ExplainResult>();
    }

    /**
     * Generate viable query candidates to minimize the execution time while trading off query size.
     * Get the top n results.
     */
    public void generateCandidates(Rule rule, Database database,
            int maxResults, Collection<CandidateQuery> results) {
        SearchFringe fringe = createFringe();
        List<CandidateQuery> candidates = search(fringe, rule, database);

        Collections.sort(candidates);

        for (int i = 0; i < Math.min(candidates.size(), maxResults); i++) {
            results.add(candidates.get(i));
        }
    }

    /**
     * Search through the candidates (limited by the budget).
     */
    private List<CandidateQuery> search(SearchFringe fringe, Rule rule, Database database) {
        fringe.clear();

        // Once validated, we know that the formula is a conjunction or single atom.
        Formula baseFormula = rule.getRewritableGroundingFormula();
        DatabaseQuery.validate(baseFormula);

        // Shortcut for single atoms (often priors).
        if (baseFormula instanceof Atom) {
            return singleAtomSearch(rule, baseFormula, database);
        }

        List<CandidateQuery> candidates = new ArrayList<CandidateQuery>();

        // A volatile set of atoms used as a working set.
        // This is just a buffer and it's use case constantly changes.
        // Once a procedure is done with this buffer, they should clear it to indicate it is no longer in-use.
        Set<Atom> atomBuffer = new HashSet<Atom>();


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
        CandidateSearchNode rootNode = validateAndCreateNode(atomBits, atoms, passthrough, variableUsageMapping, atomBuffer,
                0.0, 0.0);

        fringe.push(rootNode);
        seenNodes.add(Long.valueOf(BitUtils.toBitSet(atomBits)));

        // Keep track of how many times we ask for an EXPLAIN from the database.
        // After the budget is expended, we will still finish out search on the remaining nodes, but not create any more.
        int explains = 0;

        while (fringe.size() > 0 && (budget <= 0 || explains < budget)) {
            CandidateSearchNode node = fringe.pop();

            // Expand the node by computing an actual score and trying to remove each atom (in-turn).
            BitUtils.toBits(node.atomsBitSet, atomBits);

            if (explainNode(node, database)) {
                explains++;
            }

            candidates.add(new CandidateQuery(rule, node.formula, node.optimisticCost));
            fringe.newPessimisticCost(node.pessimisticCost);

            for (int i = 0; i < atoms.size(); i++) {
                // Skip if this atom was dropped somewhere else.
                if (!atomBits[i]) {
                    continue;
                }

                // Flip the atom.
                atomBits[i] = false;

                // Skip nodes we have seen before and totally empty candidates (no atoms on).
                Long bitId = Long.valueOf(BitUtils.toBitSet(atomBits));
                if (!seenNodes.contains(bitId) && bitId.longValue() != 0) {
                    seenNodes.add(bitId);

                    CandidateSearchNode child = validateAndCreateNode(atomBits, atoms, passthrough, variableUsageMapping, atomBuffer,
                            node.optimisticCost, node.pessimisticCost);
                    if (child != null) {
                        fringe.push(child);
                    }
                }

                // Unflip the atom (so we can flip the next one).
                atomBits[i] = true;
            }
        }

        return candidates;
    }

    /**
     * Search specifically knowing that the formula is only a single atom.
     * This can indicate a prior or other type of simple rule that can be augmented with special handling.
     */
    private List<CandidateQuery> singleAtomSearch(Rule rule, Formula baseFormula, Database database) {
        assert(baseFormula instanceof Atom);

        List<CandidateQuery> candidates = new ArrayList<CandidateQuery>(2);

        CandidateSearchNode node = new CandidateSearchNode(0l, baseFormula, 1, 1.0, 1.0);
        explainNode(node, database);

        // Add the base formula as a candidate.
        candidates.add(new CandidateQuery(rule, node.formula, node.optimisticCost));

        // Collect the full set of atoms used in the rule (not just the ones in the query formula).
        Set<Atom> atoms = new HashSet<Atom>();
        rule.getCoreAtoms(atoms);

        // If this is a prior (single atom) or has more than two atoms, then just return the single candidate.
        if (atoms.size() != 2) {
            return candidates;
        }

        // If the rule takes a form similar to: `Closd(A, B) -> Open(A, B)`, then we can take an additional optimization.
        // We assume there will be no PAM exceptions, so a subset of Open(A, B) will get grounded.
        // Therefore, we can use Open(A, B) as a candidate.
        // This should work as long as at least one of the atoms is open.

        int openAtoms = 0;
        for (Atom atom : atoms) {
            if ((atom.getPredicate() instanceof StandardPredicate) && !database.isClosed((StandardPredicate)atom.getPredicate())) {
                openAtoms++;
            }
        }

        if (openAtoms == 0) {
            return candidates;
        }

        atoms.remove(baseFormula);
        if (atoms.size() != 1) {
            // This should never happen.
            return candidates;
        }

        node = new CandidateSearchNode(0l, atoms.iterator().next(), 1, 1.0, 1.0);
        explainNode(node, database);
        candidates.add(new CandidateQuery(rule, node.formula, node.optimisticCost));

        return candidates;
    }

    /**
     * Given the active atoms, create a search node and estimate the cost.
     * The cost is non-trivial, since a query estimate is made.
     * @return The candidate search node, or null if the candidate is invalid.
     */
    private CandidateSearchNode validateAndCreateNode(boolean[] atomBits, List<Atom> atoms,
            Set<Atom> passthrough, Map<Variable, Set<Atom>> variableUsageMapping, Set<Atom> atomBuffer,
            double parentOptimisticCost, double parentPessimisticCost) {
        Formula formula = constructFormula(atomBits, atoms, passthrough, atomBuffer);

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

        assert(numAtoms > 0);

        // Compute the approximate cost knowing that this candidate will have one less atom than its parent.
        // (Unless this is the root node, where the cost will already be zero.)

        double optimisticCost = parentOptimisticCost * numAtoms / (numAtoms + 1);
        double pessimisticCost = parentPessimisticCost * numAtoms / (numAtoms + 1);

        return new CandidateSearchNode(BitUtils.toBitSet(atomBits), formula,
                numAtoms, optimisticCost, pessimisticCost);
    }

    private boolean explainNode(CandidateSearchNode node, Database database) {
        DatabaseDriver.ExplainResult result = null;
        boolean usedExplain = false;

        String formulaString = node.formula.toString();
        if (explains.containsKey(formulaString)) {
            result = explains.get(formulaString);
        } else {
            String sql = Formula2SQL.getQuery(node.formula, database, false);
            result = database.getDataStore().explain(sql);
            explains.put(formulaString, result);
            usedExplain = true;
        }

        node.approximateCost = false;
        node.optimisticCost =
                (result.totalCost * OPTIMISTIC_QUERY_COST_MULTIPLIER
                + result.rows * OPTIMISTIC_INSTANTIATION_COST_MULTIPLIER)
                * (node.numAtoms * CANDIDATE_SIZE_ADJUSTMENT);
        node.pessimisticCost =
                (result.totalCost * PESSIMISTIC_QUERY_COST_MULTIPLIER
                + result.rows * PESSIMISTIC_INSTANTIATION_COST_MULTIPLIER)
                * (node.numAtoms * CANDIDATE_SIZE_ADJUSTMENT);

        if (usedExplain) {
            log.trace("Scored candidate: " + node);
        }

        return usedExplain;
    }

    /**
     * Construct a formula from the active atoms, and fill atomBuffer with all active (including passthrough) atoms.
     * The caller is responsible for clearing atomBuffer when it is finished with it.
     */
    private Formula constructFormula(boolean[] atomBits, List<Atom> atoms, Set<Atom> passthrough, Set<Atom> atomBuffer) {
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
            // Only atoms backed by real data (i.e. StandardPredicate) count for providing variables.
            if (!(atom.getPredicate() instanceof StandardPredicate)) {
                continue;
            }

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
                // Treat these like normal predicates.
                // They will either be handled in the query, or at instantiation time.
            } else if (!(atom.getPredicate() instanceof StandardPredicate)) {
                throw new IllegalStateException("Unknown predicate type: " + atom.getPredicate().getClass().getName());
            }
        }

        atoms.removeAll(removeAtoms);
        return passthrough;
    }

    private SearchFringe createFringe() {
        switch (searchType) {
            case BFS:
                return new SearchFringe.BFSSearchFringe();
            case DFS:
                return new SearchFringe.DFSSearchFringe();
            case UCS:
                return new SearchFringe.UCSSearchFringe();
            case BoundedUCS:
                return new SearchFringe.BoundedUCSSearchFringe();
            case BoundedDFS:
                return new SearchFringe.BoundedDFSSearchFringe();
            default:
                throw new IllegalStateException("Unknown search type: " + searchType);
        }
    }
}
