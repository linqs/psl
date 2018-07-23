/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.Formula2SQL;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.reasoner.function.FunctionComparator;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SetOperationQuery;
import com.healthmarketscience.sqlbuilder.Subquery;
import com.healthmarketscience.sqlbuilder.UnionQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all (first order, i.e., not ground) arithmetic rules.
 *
 * Full equality checks (when two rules are the equal, but not the same reference) are expensive.
 *
 * @author Stephen Bach
 */
public abstract class AbstractArithmeticRule extends AbstractRule {
	private static final Logger log = LoggerFactory.getLogger(AbstractArithmeticRule.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "arithmeticrule";

	/**
	 * The delimiter to use when building summation substitutions.
	 * Make sure the value for this key does not appear in ground atoms that use a summation.
	 */
	public static final String DELIM_KEY = CONFIG_PREFIX + ".delim";
	public static final String DELIM_DEFAULT = ";";

	protected final ArithmeticRuleExpression expression;
	protected final Map<SummationVariable, Formula> filters;

	protected String delim;

	public AbstractArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses, String name) {
		super(name);
		this.expression = expression;
		this.filters = filterClauses;

		delim = Config.getString(DELIM_KEY, DELIM_DEFAULT);

		// Ensures that all filter Formulas are in DNF
		for (Map.Entry<SummationVariable, Formula> entry : this.filters.entrySet()) {
			entry.setValue(entry.getValue().getDNF());
		}

		validateRule();
	}

	public boolean hasSummation() {
		return expression.getSummationVariables().size() > 0;
	}

	public ArithmeticRuleExpression getExpression() {
		return expression;
	}

	@Override
	public int groundAll(AtomManager atomManager, GroundRuleStore groundRuleStore) {
		validateGroundRule(atomManager);

		int groundCount = 0;
		if (expression.getSummationVariables().size() == 0) {
			groundCount = groundNonSummationRule(atomManager, groundRuleStore);
		} else {
			groundCount = groundSummationRule(atomManager, groundRuleStore);
		}

		log.debug("Grounded {} instances of rule {}", groundCount, this);
		return groundCount;
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

	/**
	 * Rules without summations are much easier to ground and can do simpler queries.
	 */
	private int groundNonSummationRule(AtomManager atomManager, GroundRuleStore groundRuleStore) {
		// Ground the variables.
		ResultList groundVariables = atomManager.executeQuery(new DatabaseQuery(expression.getQueryFormula(), false));
		return groundNonSummationRule(groundVariables, atomManager, groundRuleStore);
	}

	public int groundNonSummationRule(ResultList groundVariables, AtomManager atomManager, GroundRuleStore groundRuleStore) {
		List<QueryAtom> queryAtoms = new ArrayList<QueryAtom>();
		for (SummationAtomOrAtom atom : expression.getAtoms()) {
			queryAtoms.add((QueryAtom)atom);
		}

		GroundAtom[] groundAtoms = new GroundAtom[queryAtoms.size()];

		// Since there are no summations, we only need to calculate the coefficients once,
		// and we don't need to pass any substitution information.
		double[] coefficients = new double[queryAtoms.size()];
		for (int i = 0; i < coefficients.length; i++) {
			coefficients[i] = expression.getAtomCoefficients().get(i).getValue(null);
		}
		double finalCoefficient = expression.getFinalCoefficient().getValue(null);

		// Instantiate the ground rules with the correct constants.
		int groundCount = 0;
		for (int groundingIndex = 0; groundingIndex < groundVariables.size(); groundingIndex++) {
			for (int atomIndex = 0; atomIndex < groundAtoms.length; atomIndex++) {
				groundAtoms[atomIndex] = queryAtoms.get(atomIndex).ground(atomManager, groundVariables, groundingIndex);
			}

			// Note that unweighed rules will ground an equality, while weighted rules will instead
			// ground a largerThan and lessThan.
			if (isWeighted() && FunctionComparator.Equality.equals(expression.getComparator())) {
				groundCount += addGroundRule(
						groundRuleStore, makeGroundRule(coefficients, groundAtoms, FunctionComparator.LargerThan, finalCoefficient));
				groundCount += addGroundRule(
						groundRuleStore, makeGroundRule(coefficients, groundAtoms, FunctionComparator.SmallerThan, finalCoefficient));
			} else {
				groundCount += addGroundRule(
						groundRuleStore, makeGroundRule(coefficients, groundAtoms, expression.getComparator(), finalCoefficient));
			}
		}

		return groundCount;
	}

	/**
	 * Rules with summations are complex and need to be grounded in a special way.
	 */
	private int groundSummationRule(AtomManager atomManager, GroundRuleStore groundRuleStore) {
		// Most of our work will happen in the database.
		// For each disjunctive component (conjunction or atom/negation) of each filter, we need to add a union to our query.
		// We will merge together a query for the body with each disjunctive clause.
		// Ex: "Friends(A, +B) <= 1.0 {B: Friends(B, 'Alice') || Nice(B)}"
		// becomes a unioned query of: "Friends(A, B) && Friends(B, 'Alice')" UNION "Friends(A, B) && Nice(B)}".
		// We will then group by the non-summation variables, and collect the summations.

		if (!(atomManager.getDatabase() instanceof RDBMSDatabase)) {
			throw new IllegalArgumentException("Can only ground summation arithmetic rules with a relational database.");
		}
		RDBMSDatabase relationalDB = ((RDBMSDatabase)atomManager.getDatabase());

		// First build the core (non-aggregated) query.
		Map<Variable, Integer> projectionMap = new HashMap<Variable, Integer>();
		VariableTypeMap varTypes = new VariableTypeMap();
		UnionQuery subquery = buildCoreSummationQuery(relationalDB, projectionMap, varTypes);

		// Now build the full, aggregate query.
		SelectQuery query = buildAggregateSummationQuery(projectionMap, subquery, ((RDBMSDataStore)relationalDB.getDataStore()).getDriver());

		// We need to edit the variable types to have strings on the aggregate (concatenated) values.
		VariableTypeMap fakeTypes = new VariableTypeMap();
		fakeTypes.addAll(varTypes);
		for (SummationVariable summationVar : expression.getSummationVariables()) {
			// Forcefully change the types.
			fakeTypes.addVariable(summationVar.getVariable(), ConstantType.String, true);
		}

		// Run the actual query and instantiate the results.
		ResultList groundingResults = relationalDB.executeQuery(projectionMap, fakeTypes, query.validate().toString());
		return instantiateSumamtionGroundRules(groundingResults, varTypes, atomManager, groundRuleStore);
	}

	private int instantiateSumamtionGroundRules(ResultList groundingResults, VariableTypeMap varTypes,
			AtomManager atomManager, GroundRuleStore groundRuleStore) {
		int groundCount = 0;

		List<GroundAtom> groundAtoms = new ArrayList<GroundAtom>();
		List<Double> coefficients = new ArrayList<Double>();

		for (int groundingIndex = 0; groundingIndex < groundingResults.size(); groundingIndex++) {
			groundAtoms.clear();
			coefficients.clear();

			// First, breakup the summation substitutions.
			Map<SummationVariable, Constant[]> subs = new HashMap<SummationVariable, Constant[]>();
			Map<SummationVariable, Integer> subCounts = new HashMap<SummationVariable, Integer>();

			for (SummationVariable summationVar : expression.getSummationVariables()) {
				Constant rawSubs = groundingResults.get(groundingIndex, summationVar.getVariable());
				String[] stringSubs = ((StringAttribute)rawSubs).getValue().split(delim);

				Constant[] constantSubs = new Constant[stringSubs.length];
				for (int i = 0; i < stringSubs.length; i++) {
					constantSubs[i] = ConstantType.getConstant(stringSubs[i], varTypes.getType(summationVar.getVariable()));
				}

				subs.put(summationVar, constantSubs);
				subCounts.put(summationVar, constantSubs.length);
			}

			// Ground out all the atoms.
			for (int i = 0; i < expression.getAtoms().size(); i++) {
				SummationAtomOrAtom atom = expression.getAtoms().get(i);
				double coefficientValue = expression.getAtomCoefficients().get(i).getValue(subCounts);

				if (atom instanceof SummationAtom) {
					// Recursively replace each summation variable.
					Constant[] args = new Constant[((SummationAtom)atom).getArity()];
					instantiateSummationVariables((SummationAtom)atom, args, 0, coefficientValue, subs, atomManager, groundingResults, groundingIndex, groundAtoms, coefficients);
				} else {
					groundAtoms.add(((QueryAtom)atom).ground(atomManager, groundingResults, groundingIndex));
					coefficients.add(coefficientValue);
				}
			}

			double finalCoefficient = expression.getFinalCoefficient().getValue(subCounts);

			// Note that unweighed rules will ground an equality, while weighted rules will instead
			// ground a largerThan and lessThan.
			if (isWeighted() && FunctionComparator.Equality.equals(expression.getComparator())) {
				groundCount += addGroundRule(
						groundRuleStore, makeGroundRule(coefficients, groundAtoms, FunctionComparator.LargerThan, finalCoefficient));
				groundCount += addGroundRule(
						groundRuleStore, makeGroundRule(coefficients, groundAtoms, FunctionComparator.SmallerThan, finalCoefficient));
			} else {
				groundCount += addGroundRule(
						groundRuleStore, makeGroundRule(coefficients, groundAtoms, expression.getComparator(), finalCoefficient));
				groundCount++;
			}
		}

		return groundCount;
	}

	private void instantiateSummationVariables(SummationAtom atom, Constant[] args,
			int argIndex, double coefficientValue, Map<SummationVariable, Constant[]> subs,
			AtomManager atomManager, ResultList groundingResults, int groundingIndex,
			List<GroundAtom> groundAtoms, List<Double> coefficients) {
		if (argIndex == args.length) {
			// Before we add the substituted atom, we need to make sure it actually exists.
			// Double (or more) summations can make it possible to make substitutions for atoms that
			// don't actually exist.
			// We will directly ask the database.
			if (atomManager.getDatabase().hasAtom((StandardPredicate)atom.getPredicate(), args)) {
				groundAtoms.add(atomManager.getAtom(atom.getPredicate(), args));
				coefficients.add(coefficientValue);
			}

			return;
		}

		SummationVariableOrTerm arg = atom.getArguments()[argIndex];
		if (arg instanceof Variable) {
			args[argIndex] = groundingResults.get(groundingIndex, (Variable)arg);
			instantiateSummationVariables(atom, args, argIndex + 1, coefficientValue, subs,
					atomManager, groundingResults, groundingIndex, groundAtoms, coefficients);
		} else if (arg instanceof Constant) {
			args[argIndex] = (Constant)arg;
			instantiateSummationVariables(atom, args, argIndex + 1, coefficientValue, subs,
					atomManager, groundingResults, groundingIndex, groundAtoms, coefficients);
		} else {
			// Go through all the summation subs and add one atom for each.
			for (Constant sub : subs.get((SummationVariable)arg)) {
				args[argIndex] = sub;
				instantiateSummationVariables(atom, args, argIndex + 1, coefficientValue, subs,
						atomManager, groundingResults, groundingIndex, groundAtoms, coefficients);
			}
		}
	}

	/**
	 * Build the aggregate query that concatenates all the summation replacemnets.
	 */
	private SelectQuery buildAggregateSummationQuery(Map<Variable, Integer> projectionMap, UnionQuery subquery, DatabaseDriver driver) {
		SelectQuery query = new SelectQuery();

		// Make sure we keep the same projection order.
		String[] columns = new String[projectionMap.size()];

		// First add all the non-summation variables.
		for (Variable var : expression.getVariables()) {
			columns[projectionMap.get(var).intValue()] = var.getName();
		}

		// Add all the summation columns as aggregates.
		for (SummationVariable summationVar : expression.getSummationVariables()) {
			Variable var = summationVar.getVariable();
			String aggExpression = driver.getStringAggregate(var.getName(), delim, true);
			String column = aggExpression + " AS " + var.getName();
			columns[projectionMap.get(var).intValue()] = column;
		}


		for (String column : columns) {
			query.addCustomColumns(new CustomSql(column));
		}

		// Add in the subquery with a generic alias.
		query.addCustomFromTable((new Subquery(subquery)).toString() + " X");

		// Group by all the non-summation variables.
		for (Variable var : expression.getVariables()) {
			query.addCustomGroupings(var.getName());
		}

		return query;
	}

	/**
	 * Build the non-aggregated portion of the summation grounding query.
	 * @param relationalDB the database that will be used to ground (required for partition information).
	 * @param outProjectionMap an output parameter that will be filled with the projection for the the union.
	 * @param outVarTypes an output parameter that will be filled with the variables types for the union.
	 */
	private UnionQuery buildCoreSummationQuery(RDBMSDatabase relationalDB,
			Map<Variable, Integer> outProjectionMap, VariableTypeMap outVarTypes) {
		// We will use the body formula as the base.
		Formula bodyFormula = expression.getQueryFormula();

		// Project only variables found in the body.
		// Note that this includes the summation variables.
		Set<Variable> projectionSet = bodyFormula.collectVariables(new VariableTypeMap()).keySet();
		// We will need to collect what the mapping looks like.
		Map<Variable, Integer> projectionMap = null;

		// Collect each part of the union.
		List<SelectQuery> queries = new ArrayList<SelectQuery>();

		// Do the body first.
		Formula2SQL sqler = new Formula2SQL(projectionSet, relationalDB, false);
		SelectQuery bodyQuery = sqler.getQuery(bodyFormula);
		projectionMap = sqler.getProjectionMap();

		// Collect queries for all the filters.
		collectFilterQueries(queries, projectionSet, relationalDB, bodyFormula,
				filters.values().toArray(new Formula[0]), 0, null);

		// Set the output variables.
		bodyFormula.collectVariables(outVarTypes);
		outProjectionMap.putAll(projectionMap);

		return new UnionQuery(SetOperationQuery.Type.UNION, queries.toArray(new SelectQuery[0]));
	}

	/**
	 * Filters without disjunctions can be directly added to the base query as a join.
	 * However if there are filters with disjunctions, then we need to take the corssproduct of disjunctive components
	 * and put in one union for each result.
	 * This will also deal with the base case where there are no filters.
	 * @param appendedFormulas collects all the formulas that we have added to the base formual
	 *  so that we can track what filter conditions need to be added. Starts null.
	 */
	private void collectFilterQueries(List<SelectQuery> queries, Set<Variable> projectionSet, RDBMSDatabase relationalDB,
			Formula baseFormula, Formula[] filterFormulas, int formulaIndex, Formula appendedFormulas) {
		// If we have exhauseted all the formulas, then add the base formula.
		if (formulaIndex == filterFormulas.length) {
			Formula2SQL sqler = new Formula2SQL(projectionSet, relationalDB, false);
			SelectQuery query = sqler.getQuery(baseFormula);

			// Now we need to add the filter conditions.
			if (appendedFormulas != null) {
				// Start by getting the table aliases.
				Map<Atom, String> tableAliases = sqler.getTableAliases();

				// Go through each atom in the filter (disjunctive component)
				// and append filter conditions to the query.
				addFilterConditions(appendedFormulas, query, tableAliases);
			}

			queries.add(query);
			return;
		}

		// Each filter is already in DNF, so it is either a disjunction, negation, or atom.
		// Recusivley descend down multiple branches for disjunctions.
		if (filterFormulas[formulaIndex] instanceof Disjunction) {
			Disjunction disjunction = (Disjunction)filterFormulas[formulaIndex];

			for (int i = 0; i < disjunction.length(); i++) {
				// Add in the current disjunctive component and descend.
				Formula formula = stableQueryConjunction(baseFormula, disjunction.get(i));

				Formula newAppendedFormuals = null;
				if (appendedFormulas == null) {
					newAppendedFormuals = disjunction.get(i);
				} else {
					newAppendedFormuals = stableQueryConjunction(appendedFormulas, disjunction.get(i));
				}

				collectFilterQueries(queries, projectionSet, relationalDB, formula,
						filterFormulas, formulaIndex + 1, newAppendedFormuals);
			}
		} else {
			// Add whatever this filter is to the body and descend to the next filter.
			// Be VERY careful to maintain ordering so that columns come out in the same order and we can union.
			Formula formula = stableQueryConjunction(baseFormula, filterFormulas[formulaIndex]);

			if (appendedFormulas == null) {
				appendedFormulas = filterFormulas[formulaIndex];
			} else {
				appendedFormulas = stableQueryConjunction(appendedFormulas, filterFormulas[formulaIndex]);
			}

			collectFilterQueries(queries, projectionSet, relationalDB, formula,
					filterFormulas, formulaIndex + 1, appendedFormulas);
		}
	}

	/**
	 * Create a conjunction of the given formulas meant for querying, but maintain ordering of all the components.
	 * Both formula are already expected to be flattened and either a conjunction, negation, or atom.
	 * Conjunctions will be broken apart and re-added, negations will be converted to atoms, and atoms will be passed through.
	 */
	private Formula stableQueryConjunction(Formula a, Formula b) {
		List<Formula> components = new ArrayList<Formula>();

		if (a instanceof Conjunction) {
			Conjunction conjunction = (Conjunction)a;
			for (int i = 0; i < conjunction.length(); i++) {
				components.add(conjunction.get(i));
			}
		} else if (a instanceof Negation) {
			components.add(((Negation)a).getFormula());
		} else {
			components.add(a);
		}

		if (b instanceof Conjunction) {
			Conjunction conjunction = (Conjunction)b;
			for (int i = 0; i < conjunction.length(); i++) {
				components.add(conjunction.get(i));
			}
		} else if (b instanceof Negation) {
			components.add(((Negation)b).getFormula());
		} else {
			components.add(b);
		}

		return new Conjunction(components.toArray(new Formula[0]));
	}

	private void addFilterConditions(Formula filterFormula, SelectQuery query, Map<Atom, String> tableAliases) {
		if (filterFormula instanceof Atom) {
			CustomSql valueColumn = new CustomSql(tableAliases.get((Atom)filterFormula) + "." + PredicateInfo.VALUE_COLUMN_NAME);
			query.addCondition(BinaryCondition.greaterThan(valueColumn, 0.0));
		} else if (filterFormula instanceof Negation) {
			Atom atom = (Atom)(((Negation)filterFormula).getFormula());
			CustomSql valueColumn = new CustomSql(tableAliases.get(atom) + "." + PredicateInfo.VALUE_COLUMN_NAME);
			query.addCondition(BinaryCondition.equalTo(valueColumn, 0.0));
		} else if (filterFormula instanceof Conjunction) {
			Conjunction conjunction = (Conjunction)filterFormula;
			for (int i = 0; i < conjunction.length(); i++) {
				addFilterConditions(conjunction.get(i), query, tableAliases);
			}
		} else {
			throw new IllegalStateException("Unexpected formula type: " + filterFormula.getClass().getName());
		}
	}

	/**
	 * Check a rule for triviality and add it to the GRS if it is non-trivial.
	 * @return the number of ground rules added to the store (1 or 0).
	 */
	private int addGroundRule(GroundRuleStore groundRuleStore, AbstractGroundArithmeticRule rule) {
		// Start simple and just look for rules with a single atom.
		if (rule.getOrderedAtoms().length == 1) {
			if (FunctionComparator.LargerThan.equals(rule.getComparator())) {
				double constantMax = 0.0;
				if (rule.getCoefficients()[0] < 0.0) {
					constantMax = -1.0;
				}

				// Trivial if either of the below situations:
				//  +x >= y (y <= 0.0)
				//  -x >= y (y <= -1.0)
				if (rule.getConstant() <= constantMax) {
					return 0;
				}
			} else if (FunctionComparator.SmallerThan.equals(rule.getComparator())) {
				double constantMin = 1.0;
				if (rule.getCoefficients()[0] < 0.0) {
					constantMin = 0.0;
				}

				// Trivial if either of the below situations:
				//  +x <= y (y >= 1.0)
				//  -x <= y (y >= 0.0)
				if (rule.getConstant() >= constantMin) {
					return 0;
				}
			}
		}

		groundRuleStore.addGroundRule(rule);
		return 1;
	}

	/**
	 * Validate what we can about an abstract rule at creation:
	 *	 - An argument to a filter must appear in the arithmetic expression.
	 *	 - All variables used in a filter are either the argument to the filter or
	 *	  appear in the arithmetic expression.
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
	 */
	public void validateGroundRule(AtomManager atomManager) {
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
	}

	protected abstract AbstractGroundArithmeticRule makeGroundRule(double[] coeffs,
			GroundAtom[] atoms, FunctionComparator comparator, double c);

	protected abstract AbstractGroundArithmeticRule makeGroundRule(List<Double> coeffs,
			List<GroundAtom> atoms, FunctionComparator comparator, double c);

	@Override
	public int hashCode() {
		return expression.hashCode();
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
}
