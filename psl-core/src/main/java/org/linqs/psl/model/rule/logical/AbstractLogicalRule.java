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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.RawQuery;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.FormulaAnalysis;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.formula.FormulaAnalysis.DNFClause;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all (first order, i.e., not ground) logical rules.
 */
public abstract class AbstractLogicalRule extends AbstractRule {
    private static final Logger log = LoggerFactory.getLogger(AbstractLogicalRule.class);

    /**
     * A key to store per-rule threading grounding resource under.
     */
    private final String groundingResourcesKey;

    protected Formula formula;
    protected final DNFClause negatedDNF;

    protected AbstractLogicalRule(Formula formula, String name) {
        this.name = name;

        this.formula = formula;
        groundingResourcesKey = AbstractLogicalRule.class.getName() + ";" + formula + ";GroundingResources";

        // Do the formula analysis so we know what atoms to query for grounding.
        // We will query for all positive atoms in the negated DNF.
        FormulaAnalysis analysis = new FormulaAnalysis(new Negation(formula));

        if (analysis.getNumDNFClauses() > 1) {
            throw new IllegalArgumentException("Formula must be a disjunction of literals (or a negative literal).");
        } else {
            negatedDNF = analysis.getDNFClause(0);
        }

        Set<Variable> unboundVariables = negatedDNF.getUnboundVariables();
        if (unboundVariables.size() > 0) {
            Variable[] sortedVariables = unboundVariables.toArray(new Variable[unboundVariables.size()]);
            Arrays.sort(sortedVariables);

            throw new IllegalArgumentException(
                    "Any variable used in a negated (non-functional) predicate must also participate" +
                    " in a positive (non-functional) predicate." +
                    " The following variables do not meet this requirement: [" + StringUtils.join(", ", sortedVariables) + "]."
            );
        }

        if (negatedDNF.isGround()) {
            throw new IllegalArgumentException("Formula has no Variables.");
        }

        if (!negatedDNF.isQueriable()) {
            throw new IllegalArgumentException("Formula is not a valid rule for unknown reason.");
        }

        // Build up the hash code from positive and negative literals.
        int hash = HashCode.DEFAULT_INITIAL_NUMBER;

        for (Atom atom : negatedDNF.getPosLiterals()) {
            hash = HashCode.build(hash, atom);
        }

        for (Atom atom : negatedDNF.getNegLiterals()) {
            hash = HashCode.build(hash, atom);
        }

        this.hashcode = hash;

        ensureRegistration();
    }

    public Formula getFormula() {
        return formula;
    }

    public DNFClause getNegatedDNF() {
        return negatedDNF;
    }

    @Override
    public long groundAll(AtomManager atomManager, GroundRuleStore groundRuleStore) {
        QueryResultIterable queryResults = atomManager.executeGroundingQuery(negatedDNF.getQueryFormula());
        return groundAll(queryResults, atomManager, groundRuleStore);
    }

    @Override
    public boolean supportsGroundingQueryRewriting() {
        return true;
    }

    @Override
    public Formula getRewritableGroundingFormula() {
        return negatedDNF.getQueryFormula();
    }

    @Override
    public boolean supportsIndividualGrounding() {
        return true;
    }

    @Override
    public RawQuery getGroundingQuery(AtomManager atomManager) {
        return new RawQuery((RDBMSDatabase)atomManager.getDatabase(), getRewritableGroundingFormula());
    }

    @Override
    public void ground(Constant[] constants, Map<Variable, Integer> variableMap, AtomManager atomManager, List<GroundRule> results) {
        results.add(ground(constants, variableMap, atomManager));
    }

    private GroundRule ground(Constant[] constants, Map<Variable, Integer> variableMap, AtomManager atomManager) {
        // Get the grounding resources for this thread,
        if (!Parallel.hasThreadObject(groundingResourcesKey)) {
            Parallel.putThreadObject(groundingResourcesKey, new GroundingResources(negatedDNF));
        }
        GroundingResources resources = (GroundingResources)Parallel.getThreadObject(groundingResourcesKey);

        return groundInternal(constants, variableMap, atomManager, resources);
    }

    public long groundAll(QueryResultIterable groundVariables, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        // We will manually handle these in the grounding process.
        // We do not want to throw too early because the ground rule may turn out to be trivial in the end.
        boolean oldAccessExceptionState = atomManager.enableAccessExceptions(false);
        long initialCount = groundRuleStore.size();

        final AtomManager finalAtomManager = atomManager;
        final GroundRuleStore finalGroundRuleStore = groundRuleStore;
        final Map<Variable, Integer> variableMap = groundVariables.getVariableMap();

        Parallel.foreach(groundVariables, new Parallel.Worker<Constant[]>() {
            @Override
            public void work(long index, Constant[] row) {
                GroundRule groundRule = ground(row, variableMap, finalAtomManager);
                if (groundRule != null) {
                    finalGroundRuleStore.addGroundRule(groundRule);
                }
            }
        });

        long groundCount = groundRuleStore.size() - initialCount;
        atomManager.enableAccessExceptions(oldAccessExceptionState);

        log.debug("Grounded {} instances of rule {}", groundCount, this);
        return groundCount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || !(other instanceof AbstractLogicalRule)) {
            return false;
        }

        AbstractLogicalRule otherRule = (AbstractLogicalRule)other;

        if (this.hashCode() != otherRule.hashCode()) {
            return false;
        }

        // Final deep equality check.
        List<Atom> thisPosLiterals = this.negatedDNF.getPosLiterals();
        List<Atom> otherPosLiterals = otherRule.negatedDNF.getPosLiterals();
        if (thisPosLiterals.size() != otherPosLiterals.size()) {
            return false;
        }

        List<Atom> thisNegLiterals = this.negatedDNF.getNegLiterals();
        List<Atom> otherNegLiterals = otherRule.negatedDNF.getNegLiterals();
        if (thisNegLiterals.size() != otherNegLiterals.size()) {
            return false;
        }

        return
                (new HashSet<Atom>(thisPosLiterals)).equals(new HashSet<Atom>(otherPosLiterals)) &&
                (new HashSet<Atom>(thisNegLiterals)).equals(new HashSet<Atom>(otherNegLiterals));
    }

    protected abstract AbstractGroundLogicalRule groundFormulaInstance(List<GroundAtom> positiveAtoms, List<GroundAtom> negativeAtoms);

    private GroundRule groundInternal(Constant[] row, Map<Variable, Integer> variableMap,
            AtomManager atomManager, GroundingResources resources) {
        resources.positiveAtoms.clear();
        resources.negativeAtoms.clear();
        resources.accessExceptionAtoms.clear();

        short rvaCount = 0;

        // Note that there is a class of trivial groundings that we choose not to remove at this point for
        // computational reasons.
        // It is possible for both a ground atoms and it's negation to appear in the DNF.
        // This obviously causes a tautology.
        // Removing it here would require checking the positive atoms against the negative ones.
        // Even if we already had a mapping of possiblities (perhaps created in FormulaAnalysis),
        // it would still be non-trivial (and complex rules can cause the mapping to blow up).
        // Instead they will be removed as they are turned into hyperplane terms,
        // since we will have to keep track of variables there anyway.

        // Note that the "positive" and "negative" qualifiers here are with respect to the negated DNF (a conjunction).
        // This is why a 0.0 for a positive atom is trivial and a 1.0 for a negative atom is trivial.

        short positiveRVACount = createAtoms(atomManager, variableMap, resources, negatedDNF.getPosLiterals(), row,
                resources.positiveAtomArgs, resources.positiveAtoms, 0.0);
        if (positiveRVACount == -1) {
            // Trivial.
            return null;
        }
        rvaCount += positiveRVACount;

        short negativeRVACount = createAtoms(atomManager, variableMap, resources, negatedDNF.getNegLiterals(), row,
                resources.negativeAtomArgs, resources.negativeAtoms, 1.0);
        if (negativeRVACount == -1) {
            // Trivial.
            return null;
        }
        rvaCount += negativeRVACount;

        if (rvaCount == 0) {
            // Trivial.
            return null;
        }

        // We got an access error and the ground rule was not trivial.
        if (resources.accessExceptionAtoms.size() != 0) {
            RuntimeException ex = new RuntimeException(String.format(
                    "Found one or more RandomVariableAtoms (target ground atom)" +
                    " that were not explicitly specified in the targets." +
                    " Offending atom(s): %s." +
                    " This typically means that your specified target set is insufficient." +
                    " This was encountered during the grounding of the rule: [%s].",
                    resources.accessExceptionAtoms, this));
            atomManager.reportAccessException(ex, resources.accessExceptionAtoms.iterator().next());
        }

        return groundFormulaInstance(resources.positiveAtoms, resources.negativeAtoms);
    }

    private short createAtoms(AtomManager atomManager, Map<Variable, Integer> variableMap,
            GroundingResources resources, List<Atom> literals, Constant[] row,
            Constant[][] argumentBuffer, List<GroundAtom> groundAtoms, double trivialValue) {
        GroundAtom atom = null;
        short rvaCount = 0;

        for (int i = 0; i < literals.size(); i++) {
            // A GroundingOnlyPredicate is only evaluated during the grounding query,
            // skip evaluating (and caching) those here.
            if (literals.get(i).getPredicate() instanceof GroundingOnlyPredicate) {
                continue;
            }

            atom = ((QueryAtom)literals.get(i)).ground(atomManager, row, variableMap, argumentBuffer[i]);
            if (atom == null) {
                return -1;
            }

            if (atom instanceof RandomVariableAtom) {
                // If we got an atom that is in violation of an access policy, then we may need to throw an exception.
                // First we will check to see if the ground rule is trivial,
                // then only throw if if it is not.
                if (((RandomVariableAtom)atom).getAccessException()) {
                    resources.accessExceptionAtoms.add((RandomVariableAtom)atom);
                }

                rvaCount++;
            } else if (MathUtils.equals(atom.getValue(), trivialValue)) {
                // This rule is trivially satisfied by a constant, do not ground it.
                return -1;
            }

            groundAtoms.add(atom);
        }

        return rvaCount;
    }

    /**
     * Allocated resources needed for grounding.
     * This will be stashed in the thread objects so each thread will have one.
     */
    private static class GroundingResources {
        // Remember that these are positive/negative in the CNF.
        public List<GroundAtom> positiveAtoms;
        public List<GroundAtom> negativeAtoms;

        // Atoms that cause trouble for the atom manager.
        public Set<GroundAtom> accessExceptionAtoms;

        // Allocate up-front some buffers for grounding QueryAtoms into.
        public Constant[][] positiveAtomArgs;
        public Constant[][] negativeAtomArgs;

        public GroundingResources(DNFClause negatedDNF) {
            positiveAtoms = new ArrayList<GroundAtom>(4);
            negativeAtoms = new ArrayList<GroundAtom>(4);
            accessExceptionAtoms = new HashSet<GroundAtom>(4);

            positiveAtomArgs = new Constant[negatedDNF.getPosLiterals().size()][];
            for (int i = 0; i < negatedDNF.getPosLiterals().size(); i++) {
                positiveAtomArgs[i] = new Constant[negatedDNF.getPosLiterals().get(i).getArity()];
            }

            negativeAtomArgs = new Constant[negatedDNF.getNegLiterals().size()][];
            for (int i = 0; i < negatedDNF.getNegLiterals().size(); i++) {
                negativeAtomArgs[i] = new Constant[negatedDNF.getNegLiterals().get(i).getArity()];
            }
        }
    }
}
