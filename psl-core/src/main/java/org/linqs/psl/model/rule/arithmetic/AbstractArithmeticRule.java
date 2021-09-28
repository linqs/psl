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
package org.linqs.psl.model.rule.arithmetic;

import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.Formula2SQL;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.RawQuery;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.util.Parallel;

import com.healthmarketscience.sqlbuilder.SelectQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all (first order, i.e., not ground) arithmetic rules.
 *
 * Full equality checks (when two rules are the equal, but not the same reference) are expensive.
 */
public abstract class AbstractArithmeticRule extends AbstractRule {
    private static final Logger log = LoggerFactory.getLogger(AbstractArithmeticRule.class);

    protected final ArithmeticRuleExpression expression;
    protected final Map<SummationVariable, Formula> filters;

    /**
     * A key to store per-rule threading grounding resources under.
     */
    private final String groundingResourcesKey;

    private volatile boolean validatedByAtomManager;

    public AbstractArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses, String name) {
        super(name, expression.hashCode());
        this.expression = expression;
        this.filters = filterClauses;

        groundingResourcesKey = AbstractArithmeticRule.class.getName() + ";" + System.identityHashCode(this) + ";GroundingResources";

        // Ensures that all filter Formulas are in DNF
        for (Map.Entry<SummationVariable, Formula> entry : this.filters.entrySet()) {
            entry.setValue(entry.getValue().getDNF());
        }

        validatedByAtomManager = false;
        validateRule();
    }

    public boolean hasSummation() {
        return expression.getSummationVariables().size() > 0;
    }

    public ArithmeticRuleExpression getExpression() {
        return expression;
    }

    @Override
    public boolean requiresSplit() {
        // Arithmetic rules will need to split if there is a filter with a disjunction.
        for (Formula filter : filters.values()) {
            if (filter instanceof Disjunction) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<Rule> split() {
        List<Rule> splitRules = new ArrayList<Rule>();

        if (!requiresSplit()) {
            splitRules.add(this);
            return splitRules;
        }

        // Note that we prime this with an empty filter.
        List<Map<SummationVariable, Formula>> splitFilters = new ArrayList<Map<SummationVariable, Formula>>();
        splitFilters.add(new HashMap<SummationVariable, Formula>());

        // Go over one filter at a time, and either break it up (if it is a disjunction)
        // or distribute over the existing formuals (if it is not a disjunction).

        for (Map.Entry<SummationVariable, Formula> entry : filters.entrySet()) {
            if (!(entry.getValue() instanceof Disjunction)) {
                // Non-disjunctions just get added to all existing splits.
                for (Map<SummationVariable, Formula> splitFilter : splitFilters) {
                    splitFilter.put(entry.getKey(), entry.getValue());
                }

                continue;
            }

            // The disjunction is already in DNF.
            Disjunction disjunction = (Disjunction)entry.getValue();

            // For every existing split filter, make a copy for each disjunct.
            List<Map<SummationVariable, Formula>> tempFilters = new ArrayList<Map<SummationVariable, Formula>>();

            for (Map<SummationVariable, Formula> splitFilter : splitFilters) {
                for (int i = 0; i < disjunction.length(); i++) {
                    // A shallow copy is fine, since we will not be modifying formulas.
                    Map<SummationVariable, Formula> newFilter = new HashMap<SummationVariable, Formula>(splitFilter);
                    newFilter.put(entry.getKey(), disjunction.get(i));
                    tempFilters.add(newFilter);
                }
            }

            splitFilters = tempFilters;
        }

        for (int i = 0; i < splitFilters.size(); i++) {
            String newName = String.format("_%s_%03d", name, i);

            if (getClass() == WeightedArithmeticRule.class) {
                WeightedArithmeticRule weightedRule = (WeightedArithmeticRule)this;
                splitRules.add(new WeightedArithmeticRule(expression, splitFilters.get(i),
                        weightedRule.getWeight(), weightedRule.isSquared(), newName));
            } else if (getClass() == UnweightedArithmeticRule.class) {
                splitRules.add(new UnweightedArithmeticRule(expression, splitFilters.get(i), newName));
            } else {
                throw new IllegalStateException("Unknown arithmetic rule class: " + getClass());
            }
        }

        return splitRules;
    }

    /**
     * Get all the predicates used in the body of this rule (no filters).
     */
    public Set<Predicate> getBodyPredicates() {
        Set<Predicate> predicates = new HashSet<Predicate>();

        for (SummationAtomOrAtom atom : expression.getAtoms()) {
            if (atom instanceof SummationAtom) {
                predicates.add(((SummationAtom)atom).getPredicate());
            } else {
                predicates.add(((Atom)atom).getPredicate());
            }
        }

        return predicates;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || !(other instanceof AbstractArithmeticRule)) {
            return false;
        }

        AbstractArithmeticRule otherRule = (AbstractArithmeticRule)other;
        return this.filters.equals(otherRule.filters) && this.expression.equals(otherRule.expression);
    }

    protected abstract AbstractGroundArithmeticRule makeGroundRule(float[] coefficients,
            GroundAtom[] atoms, FunctionComparator comparator, float constant);

    protected abstract AbstractGroundArithmeticRule makeGroundRule(List<Float> coefficients,
            List<GroundAtom> atoms, FunctionComparator comparator, float constant);

    @Override
    public boolean supportsGroundingQueryRewriting() {
        // Only non-summation rules can be rewritten.
        return !hasSummation();
    }

    @Override
    public Formula getRewritableGroundingFormula() {
        if (!hasSummation()) {
            return expression.getQueryFormula();
        }

        throw new UnsupportedOperationException("Rule does not support query rewriting: " + this);
    }

    @Override
    public boolean supportsIndividualGrounding() {
        return true;
    }

    @Override
    public RawQuery getGroundingQuery(AtomManager atomManager) {
        if (!(atomManager.getDatabase() instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("Can only ground arithmetic rules with a relational database.");
        }
        RDBMSDatabase database = ((RDBMSDatabase)atomManager.getDatabase());

        if (!hasSummation()) {
            return new RawQuery(database, expression.getQueryFormula());
        } else {
            return getSummationRawQuery(database);
        }
    }

    @Override
    public void ground(Constant[] constants, Map<Variable, Integer> variableMap, AtomManager atomManager,
            List<GroundRule> results) {
        if (!validatedByAtomManager) {
            validateForGrounding(atomManager);
        }

        if (!hasSummation()) {
            groundForNonSummation(constants, variableMap, atomManager, results);
        } else {
            groundForSummation(constants, variableMap, atomManager, results);
        }
    }

    private void groundForNonSummation(Constant[] constants, Map<Variable, Integer> variableMap, AtomManager atomManager,
            List<GroundRule> results) {
        GroundingResources resources = getGroundingResources(expression);
        groundSingleNonSummationRule(constants, variableMap, atomManager, resources);

        results.addAll(resources.groundRules);
        resources.groundRules.clear();
        resources.accessExceptionAtoms.clear();
    }

    private void groundForSummation(Constant[] constants, Map<Variable, Integer> variableMap, AtomManager atomManager,
            List<GroundRule> results) {
        if (!(atomManager.getDatabase() instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("Can only ground summation arithmetic rules with a relational database.");
        }
        RDBMSDatabase database = ((RDBMSDatabase)atomManager.getDatabase());

        GroundingResources resources = prepSummationGroundingResources(database);

        // Bail if there are no groundings.
        if (resources.flatExpression == null) {
            return;
        }

        groundSingleSummationRule(constants, variableMap, atomManager, resources);

        results.addAll(resources.groundRules);
        resources.groundRules.clear();
        resources.accessExceptionAtoms.clear();
    }

    @Override
    public long groundAll(AtomManager atomManager, GroundRuleStore groundRuleStore) {
        if (!validatedByAtomManager) {
            validateForGrounding(atomManager);
        }

        long groundCount = 0;
        if (!hasSummation()) {
            groundCount = groundAllNonSummationRule(atomManager, groundRuleStore);
        } else {
            groundCount = groundAllSummationRule(atomManager, groundRuleStore);
        }

        log.debug("Grounded {} instances of rule {}", groundCount, this);
        return groundCount;
    }

    private long groundAllNonSummationRule(AtomManager atomManager, GroundRuleStore groundRuleStore) {
        GroundingResources resources = getGroundingResources(expression);

        ResultList results = atomManager.executeQuery(new DatabaseQuery(expression.getQueryFormula(), false));
        Map<Variable, Integer> variableMap = results.getVariableMap();

        for (int groundingIndex = 0; groundingIndex < results.size(); groundingIndex++) {
            groundSingleNonSummationRule(results.get(groundingIndex), variableMap, atomManager, resources);
        }

        long count = resources.groundRules.size();
        for (GroundRule groundRule : resources.groundRules) {
            groundRuleStore.addGroundRule(groundRule);
        }
        resources.groundRules.clear();
        resources.accessExceptionAtoms.clear();

        return count;
    }

    private void groundSingleNonSummationRule(
            Constant[] queryRow, Map<Variable, Integer> variableMap,
            AtomManager atomManager, GroundingResources resources) {

        for (int atomIndex = 0; atomIndex < resources.groundAtoms.length; atomIndex++) {
            GroundAtom atom = resources.queryAtoms.get(atomIndex).ground(
                    atomManager, queryRow, variableMap, resources.argumentBuffer[atomIndex]);
            if (atom == null) {
                return;
            }

            resources.groundAtoms[atomIndex] = atom;

            if ((atom instanceof RandomVariableAtom) && ((RandomVariableAtom)atom).getAccessException()) {
                resources.accessExceptionAtoms.add(resources.groundAtoms[atomIndex]);
            }
        }

        // Note that unweighed rules will ground an equality, while weighted rules will instead
        // ground a largerThan and lessThan.
        GroundRule groundRule = null;
        if (isWeighted() && FunctionComparator.EQ.equals(expression.getComparator())) {
            groundRule = makeGroundRule(resources.coefficients, resources.groundAtoms,
                    FunctionComparator.GTE, resources.finalCoefficient);
            if (verifyGroundRule(groundRule, atomManager, resources)) {
                resources.groundRules.add(groundRule);
            }

            groundRule = makeGroundRule(resources.coefficients, resources.groundAtoms,
                    FunctionComparator.LTE, resources.finalCoefficient);
            if (verifyGroundRule(groundRule, atomManager, resources)) {
                resources.groundRules.add(groundRule);
            }
        } else {
            groundRule = makeGroundRule(resources.coefficients, resources.groundAtoms,
                    expression.getComparator(), resources.finalCoefficient);
            if (verifyGroundRule(groundRule, atomManager, resources)) {
                resources.groundRules.add(groundRule);
            }
        }
    }

    /**
     * Ground by first expanding summation atoms into normal ones and then calling the non-summation grounding.
     */
    private long groundAllSummationRule(AtomManager atomManager, GroundRuleStore groundRuleStore) {
        if (!(atomManager.getDatabase() instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("Can only ground summation arithmetic rules with a relational database.");
        }
        RDBMSDatabase database = ((RDBMSDatabase)atomManager.getDatabase());

        GroundingResources resources = prepSummationGroundingResources(database);

        // Bail if there are no groundings.
        if (resources.flatExpression == null) {
            return 0;
        }

        RawQuery rawQuery = getSummationRawQuery(database);

        ResultList results = database.executeQuery(rawQuery);
        Map<Variable, Integer> variableMap = results.getVariableMap();

        for (int groundingIndex = 0; groundingIndex < results.size(); groundingIndex++) {
            groundSingleSummationRule(results.get(groundingIndex), variableMap, atomManager, resources);
        }

        long count = resources.groundRules.size();
        for (GroundRule groundRule : resources.groundRules) {
            groundRuleStore.addGroundRule(groundRule);
        }
        resources.groundRules.clear();
        resources.accessExceptionAtoms.clear();

        return count;
    }

    private void groundSingleSummationRule(
            Constant[] queryRow, Map<Variable, Integer> variableMap,
            AtomManager atomManager, GroundingResources resources) {
        // First reset the summation counts.
        for (Map.Entry<SummationVariable, Integer> entry : resources.totalSummationCounts.entrySet()) {
            resources.summationCounts.put(entry.getKey(), entry.getValue());
        }

        int skippedAtoms = 0;
        for (int atomIndex = 0; atomIndex < resources.groundAtoms.length; atomIndex++) {
            resources.groundAtoms[atomIndex] = null;

            // We will need to check the database for existance if we have an open summation atom.
            boolean checkDatabase =
                    resources.flatSummationAtoms[atomIndex] &&
                    !atomManager.isClosed((StandardPredicate)resources.queryAtoms.get(atomIndex).getPredicate());

            boolean skip = false;
            SummationVariable[] variables = resources.flatSummationVariables.get(atomIndex);

            // Check the DB cache for summation atoms.
            GroundAtom groundAtom = resources.queryAtoms.get(atomIndex).ground(
                    atomManager, queryRow, variableMap, resources.argumentBuffer[atomIndex], checkDatabase);

            // This atom does not exist in the DB cache, skip it.
            // Non-summation atoms will throw an access exception in this case.
            if (groundAtom == null) {
                skip = true;
            }

            // Check if this atom is removed by a filter.
            if (!skip) {
                for (int variableIndex = 0; variableIndex < variables.length; variableIndex++) {
                    SummationVariable variable = variables[variableIndex];

                    if (variable == null || !filters.containsKey(variable)) {
                        continue;
                    }

                    if (!evalFilter(
                            filters.get(variable), variable,
                            groundAtom.getArguments()[variableIndex],
                            atomManager, queryRow, variableMap)) {
                        skip = true;
                        break;
                    }
                }
            }

            if (!skip) {
                resources.groundAtoms[atomIndex] = groundAtom;
            } else {
                skippedAtoms++;

                // If this is a summation atom, then subtract this from the counts.
                if (resources.flatSummationAtoms[atomIndex]) {
                    for (SummationVariable variable : variables) {
                        if (variable == null) {
                            continue;
                        }

                        resources.summationCounts.put(variable, resources.summationCounts.get(variable).intValue() - 1);
                    }
                }
            }
        }

        if (skippedAtoms >= resources.groundAtoms.length) {
            // There are no atoms to ground with.
            return;
        }

        // Compute the coefficients.
        // and we don't need to pass any substitution information.
        for (int i = 0; i < resources.coefficients.length; i++) {
            resources.coefficients[i] = resources.flatExpression.getAtomCoefficients().get(i).getValue(resources.summationCounts);
        }
        resources.finalCoefficient = resources.flatExpression.getFinalCoefficient().getValue(resources.summationCounts);

        // Note that unweighed rules will ground an equality, while weighted rules will instead
        // ground a largerThan and lessThan.
        GroundRule groundRule = null;
        if (isWeighted() && FunctionComparator.EQ.equals(resources.flatExpression.getComparator())) {
            groundRule = makeGroundRule(resources.coefficients, resources.groundAtoms, FunctionComparator.GTE, resources.finalCoefficient);
            if (verifyGroundRule(groundRule, atomManager, resources)) {
                resources.groundRules.add(groundRule);
            }

            groundRule = makeGroundRule(resources.coefficients, resources.groundAtoms, FunctionComparator.LTE, resources.finalCoefficient);
            if (verifyGroundRule(groundRule, atomManager, resources)) {
                resources.groundRules.add(groundRule);
            }
        } else {
            groundRule = makeGroundRule(resources.coefficients, resources.groundAtoms, resources.flatExpression.getComparator(), resources.finalCoefficient);
            if (verifyGroundRule(groundRule, atomManager, resources)) {
                resources.groundRules.add(groundRule);
            }
        }
    }

    /**
     * Evaluate a filter statement.
     * Return true if the filter expression is true (and thus keep the grounding).
     * Note that by this point, all disjunctions have been removed from the filters.
     * Only conjunctions, negations, and atoms are left.
     * For filters, anything > 0 is true.
     */
    private boolean evalFilter(
            Formula filter,
            SummationVariable summationVariable, Constant variableValue,
            AtomManager atomManager, Constant[] queryRow, Map<Variable, Integer> variableMap) {
        if (filter instanceof Atom) {
            // If the summation variable is in this atom, then replace its value and then ground.
            QueryAtom atom = (QueryAtom)filter;
            Term[] arguments = atom.getArguments();
            Term[] newArguments = null;

            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i].equals(summationVariable.getVariable())) {
                    if (newArguments == null) {
                        newArguments = Arrays.copyOf(arguments, arguments.length);
                    }

                    newArguments[i] = variableValue;
                }
            }

            if (newArguments != null) {
                atom = new QueryAtom(atom.getPredicate(), newArguments);
            }

            GroundAtom groundAtom = atom.ground(atomManager, queryRow, variableMap);
            if (groundAtom == null) {
                return false;
            }

            return groundAtom.getValue() > 0.0f;
        } else if (filter instanceof Negation) {
            return !evalFilter(
                ((Negation)filter).getFormula(),
                summationVariable, variableValue,
                atomManager, queryRow, variableMap);
        } else if (filter instanceof Conjunction) {
            Conjunction conjunction = (Conjunction)filter;
            for (int i = 0; i < conjunction.length(); i++) {
                boolean value = evalFilter(
                    conjunction.get(i),
                    summationVariable, variableValue,
                    atomManager, queryRow, variableMap);

                if (!value) {
                    return false;
                }
            }

            return true;
        } else {
            throw new IllegalStateException("Unexpected filter formula: " + filter);
        }
    }

    /**
     * Check a rule for triviality and access exceptions.
     */
    private boolean verifyGroundRule(GroundRule baseRule, AtomManager atomManager, GroundingResources resources) {
        AbstractGroundArithmeticRule rule = (AbstractGroundArithmeticRule)baseRule;

        // Start simple and just look for rules with a single atom.
        if (rule.getOrderedAtoms().length == 1) {
            if (FunctionComparator.GTE.equals(rule.getComparator())) {
                float constantMax = 0.0f;
                if (rule.getCoefficients()[0] < 0.0f) {
                    constantMax = -1.0f;
                }

                // Trivial if either of the below situations:
                //  +x >= y (y <= 0.0)
                //  -x >= y (y <= -1.0)
                if (rule.getConstant() <= constantMax) {
                    return false;
                }
            } else if (FunctionComparator.LTE.equals(rule.getComparator())) {
                float constantMin = 1.0f;
                if (rule.getCoefficients()[0] < 0.0f) {
                    constantMin = 0.0f;
                }

                // Trivial if either of the below situations:
                //  +x <= y (y >= 1.0)
                //  -x <= y (y >= 0.0)
                if (rule.getConstant() >= constantMin) {
                    return false;
                }
            }
        }

        // Ensure that there are RVAs.
        boolean hasRVA = false;
        for (int i = 0; i < rule.getOrderedAtoms().length; i++) {
            if (rule.getOrderedAtoms()[i] instanceof RandomVariableAtom) {
                hasRVA = true;
            }
        }

        if (!hasRVA) {
            return false;
        }

        // This rule is not trivial, so also ensure that it does not have any PAM exceptions.
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

        return true;
    }

    /**
     * Validate what we can about an abstract rule at creation:
     *     - An argument to a filter must appear in the arithmetic expression.
     *     - All variables used in a filter are either the argument to the filter or
     *        appear in the arithmetic expression.
     */
    private void validateRule() {
        // Ensure all filter arguments appear in the arithmetic expression.
        for (SummationVariable filterArg : filters.keySet()) {
            if (!expression.getSummationVariables().contains(filterArg)) {
                throw new IllegalArgumentException(String.format(
                        "Unknown variable (%s) used as filter argument. " +
                        "All filter arguments must appear as summation variables in associated arithmetic expression.",
                        filterArg.getVariable().getName()));
            }
        }

        // Ensure all variables used in the filters are either the argument summation variable or in the expression.
        Set<String> expressionVariableNames = new HashSet<String>();
        for (Variable var : expression.getVariables()) {
            expressionVariableNames.add(var.getName());
        }

        for (Map.Entry<SummationVariable, Formula> filter : filters.entrySet()) {
            VariableTypeMap filterVars = new VariableTypeMap();
            filter.getValue().collectVariables(filterVars);

            for (Variable var : filterVars.keySet()) {
                if (!(filter.getKey().getVariable().getName().equals(var.getName()) || expressionVariableNames.contains(var.getName()))) {
                    throw new IllegalArgumentException(String.format(
                            "Unknown variable (%s) used in filter. " +
                            "All filter variables must either be the filter argument or appear " +
                            "in the associated arithmetic expression.",
                            var.getName()));
                }
            }
        }
    }

    /**
     * Validate the abstract rule in the context of of grounding.
     * Ensure that no open predicates are being used in a filter.
     * This is syncronized because we set a variable that multiple threads will look at.
     */
    private synchronized void validateForGrounding(AtomManager atomManager) {
        if (validatedByAtomManager) {
            return;
        }

        if (requiresSplit()) {
            throw new IllegalStateException("This rule should be split() before attemting grounding.");
        }

        Set<Atom> filterAtoms = new HashSet<Atom>();
        for (Formula filter : filters.values()) {
            filter.getAtoms(filterAtoms);
        }

        for (Atom filterAtom : filterAtoms) {
            if (filterAtom.getPredicate() instanceof StandardPredicate
                    && !atomManager.isClosed(((StandardPredicate)filterAtom.getPredicate()))) {
                throw new IllegalArgumentException(String.format(
                        "Open predicate (%s) not allowed in filter. " +
                        "Only closed predicates may appear in filters.",
                        filterAtom.getPredicate().getName()));
            }
        }

        validatedByAtomManager = true;
    }

    /**
     * Get a raw query that represents the grounding query for a rule with summations.
     */
    private RawQuery getSummationRawQuery(RDBMSDatabase database) {
        // For the actual query, just use the normal expression.
        // We can't use the flat expression, since the flat summation will guarentee no results in most cases.
        // But, we can just ground normally and ignore the summation variables to get the variable replacments.
        // Then, we can use those replacements in the flat expression.
        Formula queryFormula = expression.getQueryFormula();

        // The distinct here is unfortunate, but we need it since we are ignoring the summation variables.
        // Note that ArithmeticRuleExpression.getVariables() does not return SummationVariables.
        Formula2SQL sqler = new Formula2SQL(expression.getVariables(), database, true);
        SelectQuery query = sqler.getQuery(queryFormula);

        VariableTypeMap variableTypes = new VariableTypeMap();
        queryFormula.collectVariables(variableTypes);

        Map<Variable, Integer> projectionMap = sqler.getProjectionMap();

        // If there are only summation atoms in this rule, then only get one result from the database.
        // The rule will be fully grounded, so we won't use any variable replacements.
        if (projectionMap.size() == 0) {
            query.setFetchNext(1);
        }

        return new RawQuery(query.validate().toString(),  projectionMap, variableTypes);
    }

    private GroundingResources prepSummationGroundingResources(RDBMSDatabase database) {
        GroundingResources resources = getGroundingResources(null);
        if (resources.summationDataLoaded) {
            return resources;
        }

        List<SummationAtomOrAtom> flatAtoms = new ArrayList<SummationAtomOrAtom>();
        List<Coefficient> flatCoefficients = new ArrayList<Coefficient>();
        List<SummationVariable[]> flatSummationVariables = new ArrayList<SummationVariable[]>();

        flattenAtoms(database, flatAtoms, flatCoefficients, flatSummationVariables);

        if (flatAtoms.size() == 0) {
            // There are no atoms, this rule has no groundings.
            resources.summationDataLoaded = true;
            return resources;
        }

        // Count all the appearences of a summation variable so we can correctly compute coefficients.
        Map<SummationVariable, Integer> summationCounts = new HashMap<SummationVariable, Integer>();
        for (SummationVariable variable : expression.getSummationMapping().keySet()) {
            summationCounts.put(variable, 0);
        }

        for (SummationVariable[] variables : flatSummationVariables) {
            for (SummationVariable variable : variables) {
                if (variable != null) {
                    summationCounts.put(variable, summationCounts.get(variable).intValue() + 1);
                }
            }
        }

        // Mark which atoms are summation atoms.
        boolean[] flatSummationAtoms = new boolean[flatSummationVariables.size()];
        for (int i = 0; i < flatSummationVariables.size(); i++) {
            for (SummationVariable variable : flatSummationVariables.get(i)) {
                if (variable != null) {
                    flatSummationAtoms[i] = true;
                }
            }
        }

        ArithmeticRuleExpression flatExpression = new ArithmeticRuleExpression(
                flatCoefficients, flatAtoms,
                expression.getComparator(), expression.getFinalCoefficient(),
                true);


        resources.parseExpression(flatExpression, false);

        resources.summationDataLoaded = true;
        resources.flatExpression = flatExpression;
        resources.totalSummationCounts = summationCounts;
        resources.summationCounts = new HashMap<SummationVariable, Integer>(summationCounts);
        resources.flatSummationVariables = flatSummationVariables;
        resources.flatSummationAtoms = flatSummationAtoms;

        return resources;
    }

    /**
     * Query the database for the possible replacements for summation variables.
     */
    private Map<SummationVariable, ResultList> fetchSummationConstants(
            Map<SummationVariable, SummationAtom> summationMapping, RDBMSDatabase database) {
        Map<SummationVariable, ResultList> summationConstants = new HashMap<SummationVariable, ResultList>();

        for (Map.Entry<SummationVariable, SummationAtom> entry : summationMapping.entrySet()) {
            ResultList results = fetchSummationValues(database, entry.getKey(), entry.getValue());
            summationConstants.put(entry.getKey(), results);
        }

        return summationConstants;
    }

    /**
     * Take the context expression and flatten out any summation atoms into non-summation atoms by expanding the summation variables.
     * The three output lists will all be the same size and indexes will match up.
     */
    private void flattenAtoms(RDBMSDatabase database,
            List<SummationAtomOrAtom> flatAtoms,
            List<Coefficient> flatCoefficients,
            List<SummationVariable[]> flatSummationVariables) {
        // All the summation variables mapped to their possible constants.
        Map<SummationVariable, ResultList> summationConstants = fetchSummationConstants(expression.getSummationMapping(), database);

        flatAtoms.clear();
        flatCoefficients.clear();
        flatSummationVariables.clear();

        // Start with all the current atoms, and expand the variables of each summation atom one at a time.
        flatAtoms.addAll(expression.getAtoms());
        flatCoefficients.addAll(expression.getAtomCoefficients());

        // Build up the initial summation variables.
        // This list will tell is which arguments came from summation variables.
        // We can build it now, and just move/copy elements along with the atoms.
        for (SummationAtomOrAtom atom : flatAtoms) {
            SummationVariable[] variables = new SummationVariable[atom.getArity()];

            if (atom instanceof SummationAtom) {
                SummationAtom summationAtom = (SummationAtom)atom;
                for (int i = 0; i < summationAtom.getArity(); i++) {
                    if (summationAtom.getArguments()[i] instanceof SummationVariable) {
                        variables[i] = (SummationVariable)summationAtom.getArguments()[i];
                    }
                }
            }

            flatSummationVariables.add(variables);
        }

        boolean done = false;
        while (!done) {
            done = true;

            for (int atomIndex = flatAtoms.size() - 1; atomIndex >= 0; atomIndex--) {
                if (!(flatAtoms.get(atomIndex) instanceof SummationAtom)) {
                    continue;
                }

                done = false;

                SummationAtom atom = (SummationAtom)flatAtoms.remove(atomIndex);
                Coefficient coefficient = flatCoefficients.remove(atomIndex);
                SummationVariable[] variables = flatSummationVariables.remove(atomIndex);

                // If this is the last summation variable in this atom, then convert it.
                boolean convertToQueryAtom = (atom.getNumSummationVariables() == 1);

                for (int argumentIndex = 0; argumentIndex < atom.getArity(); argumentIndex++) {
                    SummationVariableOrTerm argument = atom.getArguments()[argumentIndex];

                    if (!(argument instanceof SummationVariable)) {
                        continue;
                    }

                    // Replace this atom using the constants for this summation variable.
                    ResultList replacements = summationConstants.get((SummationVariable)argument);
                    for (int resultIndex = 0; resultIndex < replacements.size(); resultIndex++) {
                        flatCoefficients.add(coefficient);
                        flatSummationVariables.add(variables);

                        if (convertToQueryAtom) {
                            Term[] newArgs = new Term[atom.getArity()];
                            for (int i = 0; i < atom.getArity(); i++) {
                                if (i == argumentIndex) {
                                    newArgs[i] = replacements.get(resultIndex)[0];
                                } else {
                                    newArgs[i] = (Term)atom.getArguments()[i];
                                }
                            }

                            flatAtoms.add(new QueryAtom(atom.getPredicate(), newArgs));
                        } else {
                            SummationVariableOrTerm[] newArgs = Arrays.copyOf(atom.getArguments(), atom.getArity());
                            newArgs[argumentIndex] = replacements.get(resultIndex)[0];
                            flatAtoms.add(new SummationAtom(atom.getPredicate(), newArgs));
                        }
                    }

                    // Only make one replacement per atom, per iteration.
                    break;
                }
            }
        }
    }

    private ResultList fetchSummationValues(RDBMSDatabase database, SummationVariable variable, SummationAtom atom) {
        QueryAtom queryAtom = atom.getQueryAtom();

        VariableTypeMap variableTypes = new VariableTypeMap();
        queryAtom.collectVariables(variableTypes);

        Set<Variable> projectionSet = new HashSet<Variable>();
        projectionSet.add(variable.getVariable());

        Formula2SQL sqler = new Formula2SQL(projectionSet, database, true);

        SelectQuery query = sqler.getQuery(queryAtom);
        Map<Variable, Integer> projectionMap = sqler.getProjectionMap();

        return database.executeQuery(projectionMap, variableTypes, query.validate().toString());
    }

    private GroundingResources getGroundingResources(ArithmeticRuleExpression expression) {
        GroundingResources resources = null;
        if (!Parallel.hasThreadObject(groundingResourcesKey)) {
            resources = new GroundingResources();

            if (expression != null) {
                resources.parseExpression(expression, !hasSummation());
            }

            Parallel.putThreadObject(groundingResourcesKey, resources);
        } else {
            resources = (GroundingResources)Parallel.getThreadObject(groundingResourcesKey);
        }

        return resources;
    }

    /**
     * Resources that every grounding thread will use and reuse.
     */
    private static class GroundingResources {
        // Because multiple ground rules can be generated from a single rule,
        // we need a place to hold onto ground rules until we pass them back.
        public List<GroundRule> groundRules;

        // Atoms that cause trouble for the atom manager.
        public Set<GroundAtom> accessExceptionAtoms;

        // Shared resources.

        public List<QueryAtom> queryAtoms;
        public GroundAtom[] groundAtoms;
        public Constant[][] argumentBuffer;
        public float[] coefficients;
        public float finalCoefficient;

        // More resources necessary for summations.

        public boolean summationDataLoaded;

        // The constext expression with all summation variables expanded.
        public ArithmeticRuleExpression flatExpression;

        // The maximum counts of all summation variable replacements.
        public Map<SummationVariable, Integer> totalSummationCounts;

        // A buffer for counting actual replacements.
        // If we filter out an atom, we can mark it here.
        // This will allow us to make accurate coefficient computations.
        public Map<SummationVariable, Integer> summationCounts;

        // A marker for every variables that shows which are summation variables.
        public List<SummationVariable[]> flatSummationVariables;

        // True for each summation atom.
        boolean[] flatSummationAtoms;

        public GroundingResources() {
            groundRules = new ArrayList<GroundRule>();
            accessExceptionAtoms = new HashSet<GroundAtom>(4);
        }

        public void parseExpression(ArithmeticRuleExpression expression, boolean computeCoefficients) {
            queryAtoms = new ArrayList<QueryAtom>();
            for (SummationAtomOrAtom atom : expression.getAtoms()) {
                queryAtoms.add((QueryAtom)atom);
            }

            groundAtoms = new GroundAtom[queryAtoms.size()];

            argumentBuffer = new Constant[queryAtoms.size()][];
            for (int i = 0; i < queryAtoms.size(); i++) {
                argumentBuffer[i] = new Constant[queryAtoms.get(i).getArity()];
            }

            coefficients = new float[queryAtoms.size()];
            finalCoefficient = 0.0f;

            if (computeCoefficients) {
                for (int i = 0; i < coefficients.length; i++) {
                    coefficients[i] = expression.getAtomCoefficients().get(i).getValue(null);
                }

                finalCoefficient = expression.getFinalCoefficient().getValue(null);
            }
        }
    }
}
