/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
import org.linqs.psl.model.atom.VariableAssignment;
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
 * @author Stephen Bach
 */
public abstract class AbstractArithmeticRule extends AbstractRule {
	private static final Logger log = LoggerFactory.getLogger(AbstractArithmeticRule.class);

	/**
	 * The delimiter  to use when building summation substitutions.
	 * Make sure the value for this key does not appear in ground atoms that use a summation.
	 */
	private static String DELIM = ";";

	protected final ArithmeticRuleExpression expression;
	protected final Map<SummationVariable, Formula> filters;

	public AbstractArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses) {
		this.expression = expression;
		this.filters = filterClauses;

		// Ensures that all filter Formulas are in DNF
		for (Map.Entry<SummationVariable, Formula> e : this.filters.entrySet()) {
			e.setValue(e.getValue().getDNF());
		}

		validateRule();
	}

	public boolean hasSummation() {
		return expression.getSummationVariables().size() > 0;
	}

	public ArithmeticRuleExpression getExpression() {
		return expression;
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
	public void groundAll(AtomManager atomManager, GroundRuleStore groundRuleStore) {
		validateGroundRule(atomManager);

		if (expression.getSummationVariables().size() == 0) {
			groundNonSummationRule(atomManager, groundRuleStore);
		} else {
			groundSummationRule(atomManager, groundRuleStore);
		}
	}

	/**
	 * Rules without summations are much easier to ground and can do simpler queries.
	 */
	private void groundNonSummationRule(AtomManager atomManager, GroundRuleStore groundRuleStore) {
		// Ground the variables.
		ResultList groundVariables = atomManager.executeQuery(new DatabaseQuery(expression.getQueryFormula(), false));
		groundNonSummationRule(groundVariables, atomManager, groundRuleStore);
	}

	// TEST(eriq): Change back to private when lazy complex grounding is complete.
	public void groundNonSummationRule(ResultList groundVariables, AtomManager atomManager, GroundRuleStore groundRuleStore) {
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

			if (FunctionComparator.Equality.equals(expression.getComparator())) {
				groundRuleStore.addGroundRule(
						makeGroundRule(coefficients, groundAtoms, FunctionComparator.LargerThan, finalCoefficient));
				groundRuleStore.addGroundRule(
						makeGroundRule(coefficients, groundAtoms, FunctionComparator.SmallerThan, finalCoefficient));
				groundCount += 2;
			} else {
				groundRuleStore.addGroundRule(
						makeGroundRule(coefficients, groundAtoms, expression.getComparator(), finalCoefficient));
				groundCount++;
			}
		}

		log.debug("Grounded {} instances of rule {}", groundCount, this);
	}

	/**
	 * Rules with summations are complex and need to be grounded in a special way.
	 */
	private void groundSummationRule(AtomManager atomManager, GroundRuleStore groundRuleStore) {
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
		ResultList groundingResults = relationalDB.executeQuery(
				new VariableAssignment(), projectionMap, fakeTypes, query.validate().toString());
		int groundCount = instantiateSumamtionGroundRules(groundingResults, varTypes, atomManager, groundRuleStore);

		log.debug("Grounded {} instances of rule {}", groundCount, this);
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
				String[] stringSubs = ((StringAttribute)rawSubs).getValue().split(DELIM);

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

			if (FunctionComparator.Equality.equals(expression.getComparator())) {
				groundRuleStore.addGroundRule(
						makeGroundRule(coefficients, groundAtoms, FunctionComparator.LargerThan, finalCoefficient));
				groundRuleStore.addGroundRule(
						makeGroundRule(coefficients, groundAtoms, FunctionComparator.SmallerThan, finalCoefficient));
				groundCount += 2;
			} else {
				groundRuleStore.addGroundRule(
						makeGroundRule(coefficients, groundAtoms, expression.getComparator(), finalCoefficient));
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
			String aggExpression = driver.getStringAggregate(var.getName(), DELIM, true);
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

	// TODO(eriq): Should this go into the database classes.
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

		// There is no partial grounding.
		VariableAssignment partialGrounding = new VariableAssignment();

		// Collect each part of the union.
		List<SelectQuery> queries = new ArrayList<SelectQuery>();

		// Do the body first.
		Formula2SQL sqler = new Formula2SQL(partialGrounding, projectionSet, relationalDB, false);
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
			Formula2SQL sqler = new Formula2SQL(new VariableAssignment(), projectionSet, relationalDB, false);
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




	// TEST
	private void old_groundSummationRule(AtomManager atomManager, GroundRuleStore groundRuleStore) {
		// Evaluate the filters.
		Map<SummationVariable, SummationDisjunctionValues> filterEvaluations = evaluateFilters(atomManager);

		// Later, we will collect all the non-summation grounding values, so we will need to know which
		// variables are non-summations.
		List<Variable> nonSummationVariables = new ArrayList<Variable>();

		// We will also need to maintain a collection of all the summation variables.
		// Note that we can not just use |filters| because not all summation variables
		// will have a filter associated with it.
		Set<SummationVariable> summationVariables = new HashSet<SummationVariable>();
		Set<SummationAtom> summationAtoms = new HashSet<SummationAtom>();

		// Collect all the atoms from the body (expression).
		// Pretend that all atoms are non-summation atoms.
		List<Atom> queryAtoms = new ArrayList<Atom>();
		for (SummationAtomOrAtom atom : expression.getAtoms()) {
			if (atom instanceof SummationAtom) {
				queryAtoms.add(((SummationAtom)atom).getQueryAtom());
				summationAtoms.add(((SummationAtom)atom));

				for (SummationVariableOrTerm arg : ((SummationAtom)atom).getArguments()) {
					if (arg instanceof Variable && !nonSummationVariables.contains((Variable)arg)) {
						nonSummationVariables.add((Variable)arg);
					} else if (arg instanceof SummationVariable) {
						summationVariables.add((SummationVariable)arg);
					}
				}
			} else {
				queryAtoms.add((Atom)atom);

				for (Term arg : ((Atom)atom).getArguments()) {
					if (arg instanceof Variable && !nonSummationVariables.contains((Variable)arg)) {
						nonSummationVariables.add((Variable)arg);
					}
				}
			}
		}

		// Build a variable map for the constants used as keys in |summationSubs|.
		// Maps non-summation variables to a unique integer.
		Map<Variable, Integer> subsVariableMap = new HashMap<Variable, Integer>();
		for (int i = 0; i < nonSummationVariables.size(); i++) {
			subsVariableMap.put(nonSummationVariables.get(i), new Integer(i));
		}

		DatabaseQuery query;
		if (queryAtoms.size() > 1) {
			query = new DatabaseQuery(new Conjunction(queryAtoms.toArray(new Formula[0])));
		} else {
			query = new DatabaseQuery(queryAtoms.get(0));
		}

		// Execute the body query
		ResultList rawGroundings = atomManager.executeQuery(query);
		Map<Variable, Integer> groundingVariableMap = rawGroundings.getVariableMap();

		// <Non-Summation Values, <Summation Variable, Summation Replacements>>
		Map<List<Constant>, Map<SummationVariable, Set<Constant>>> summationSubs =
				new HashMap<List<Constant>, Map<SummationVariable, Set<Constant>>>();

		// Store all ground summation atoms for later validation.
		Set<GroundAtom> groundSummationAtoms = new HashSet<GroundAtom>();

		// Collapse all the groundings with the same non-arithmetic components into one grounding (|summationSubs|).
		// Ex: Foo(A, +B) = 1 -- [Foo('Alice', 'Bob') = 1, Foo('Alice', 'Charlie') = 1]
		//  becomes [Foo('Alice', 'Bob') + Foo('Alice', 'Charlie') = 1]
		for (int i = 0; i < rawGroundings.size(); i++) {
			Constant[] rawGrounding = rawGroundings.get(i);

			// Store all ground summation atoms for later validation.
			for (SummationAtom summationAtom : summationAtoms) {
				groundSummationAtoms.add(getGroundAtom(summationAtom, Arrays.asList(rawGrounding), groundingVariableMap, atomManager));
			}

			// Put all the non-summation constants into a list.
			// Note that we know the size ahead of time and would have used an array, but this will need to
			// be a key in a map so it must be an Object.
			List<Constant> nonSummationConstants = new ArrayList<Constant>(nonSummationVariables.size());
			for (Variable nonSummationVariable : nonSummationVariables) {
				nonSummationConstants.add(rawGrounding[groundingVariableMap.get(nonSummationVariable).intValue()]);
			}

			if (!summationSubs.containsKey(nonSummationConstants)) {
				summationSubs.put(nonSummationConstants, new HashMap<SummationVariable, Set<Constant>>());
			}
			Map<SummationVariable, Set<Constant>> subs = summationSubs.get(nonSummationConstants);

			// Add summation values that pass the filter into the set of summation subs.
			// If there is no filter, we just add it directly.
			for (SummationVariable sumVar : summationVariables) {
				if (!subs.containsKey(sumVar)) {
					subs.put(sumVar, new HashSet<Constant>());
				}

				// Either there is no filter statement, or the filter allows this grounding.
				if (!filterEvaluations.containsKey(sumVar)
					 || filterEvaluations.get(sumVar).getEvaluation(rawGrounding, groundingVariableMap)) {
					subs.get(sumVar).add(rawGrounding[groundingVariableMap.get(sumVar.getVariable()).intValue()]);
				}
			}
		}

		List<Double> coeffs = new LinkedList<Double>();
		List<GroundAtom> atoms = new LinkedList<GroundAtom>();

		int groundCount = 0;

		// For each ground set of non-summation constants,
		// ground out the non-summation atoms along with all the summation substitutions.
		for (Map.Entry<List<Constant>, Map<SummationVariable, Set<Constant>>> summationSub : summationSubs.entrySet()) {
			populateCoeffsAndAtoms(coeffs, atoms, summationSub.getKey(), subsVariableMap,
					atomManager, summationSub.getValue(), groundSummationAtoms);
			groundCount += ground(groundRuleStore, coeffs, atoms,
					// TEST
					// expression.getFinalCoefficient().getValue(summationSub.getValue()));
					0);

			coeffs.clear();
			atoms.clear();
		}

		log.debug("Grounded {} instances of rule {}", groundCount, this);
	}

	private Map<SummationVariable, SummationDisjunctionValues> evaluateFilters(AtomManager atomManager) {
		Map<SummationVariable, SummationDisjunctionValues> summationEvals = new HashMap<SummationVariable, SummationDisjunctionValues>();

		// For each filter
		for (Map.Entry<SummationVariable, Formula> filter : filters.entrySet()) {
			SummationDisjunctionValues summationDisjunctionEval = new SummationDisjunctionValues();

			Formula filterFormula = filter.getValue().getDNF();

			// Collect all the formula used in this filter (may be more than one if it is a disjunction).
			List<Formula> filterFormulas = new ArrayList<Formula>();
			if (filterFormula instanceof Disjunction) {
				for (int i = 0; i < ((Disjunction)filterFormula).length(); i++) {
					filterFormulas.add(((Disjunction)filterFormula).get(i));
				}
			} else {
				filterFormulas.add(filterFormula);
			}

			// We need to process each disjunctive component independently, because the non-exsistance of a grounding
			// in one part of the disjunction should not affect the other parts of the disjunction.
			// Ex: Friends(A, +B) >= 1 {B: Empty(B) || AlwaysTrue(B)
			// Where Empty has no observations and AlwaysTrue has 1 for every possible opservation.
			for (Formula componentFormula : filterFormulas) {
				Set<Atom> queryAtomsSet = new HashSet<Atom>();
				queryAtomsSet = componentFormula.getAtoms(queryAtomsSet);

				SummationValues summationEval = new SummationValues(queryAtomsSet);
				Atom[] queryAtoms = queryAtomsSet.toArray(new Atom[0]);

				DatabaseQuery query;
				if (queryAtoms.length > 1) {
					query = new DatabaseQuery(new Conjunction(queryAtoms));
				} else {
					query = new DatabaseQuery(queryAtoms[0]);
				}

				ResultList filterGroundings = atomManager.executeQuery(query);
				for (int i = 0; i < filterGroundings.size(); i++) {
					boolean evaluationValue = evaluateFilterGrounding(atomManager,
							componentFormula, queryAtoms,
							filterGroundings.get(i), filterGroundings.getVariableMap());
					summationEval.add(filterGroundings.get(i), filterGroundings.getVariableMap(), evaluationValue);
				}

				summationDisjunctionEval.addComponent(summationEval);
			}

			summationEvals.put(filter.getKey(), summationDisjunctionEval);
		}

		return summationEvals;
	}

	/**
	 * Recursively evaluate a filter grounding.
	 * The input formula should already be in DNF.
	 */
	private boolean evaluateFilterGrounding(
			AtomManager atomManager,
			Formula formula, Atom[] atoms,
			Constant[] groundings, Map<Variable, Integer> variableMap) {
		if (formula instanceof Conjunction) {
			Conjunction conjunction = (Conjunction)formula;
			for (int i = 0; i < conjunction.length(); i++) {
				if (!evaluateFilterGrounding(atomManager, conjunction.get(i), atoms, groundings, variableMap)) {
					// Short circuit.
					return false;
				}
			}
			return true;
		} else if (formula instanceof Disjunction) {
			throw new IllegalArgumentException("Filter disjunctions should be handled independently of one another.");
		} else if (formula instanceof Negation) {
			return !(evaluateFilterGrounding(atomManager, ((Negation)formula).getFormula(), atoms, groundings, variableMap));
		} else if (formula instanceof Atom) {
			Atom atom = (Atom)formula;
			Term[] args = atom.getArguments();
			Constant[] groundValues = new Constant[args.length];

			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Constant) {
					groundValues[i] = (Constant)args[i];
				} else if (args[i] instanceof Variable) {
					groundValues[i] = groundings[variableMap.get((Variable)args[i]).intValue()];
				} else {
					throw new IllegalArgumentException("Unknown type of atom term: " + args[i].getClass().getName());
				}
			}

			return atomManager.getAtom(atom.getPredicate(), groundValues).getValue() > 0.0;
		} else {
			throw new IllegalArgumentException("Formula was not in DNF as expected. Found class: " + formula.getClass().getName());
		}
	}

	/**
	 * Given a collection of the non-summation constants and allowed substitutions,
	 * figure out all the atoms and coefficients that will appear in the final ground rule.
	 */
	private void populateCoeffsAndAtoms(List<Double> coeffs, List<GroundAtom> atoms,
			List<Constant> grounding, Map<Variable, Integer> varMap,
			AtomManager atomManager, Map<SummationVariable, Set<Constant>> subs,
			Set<GroundAtom> groundSummationAtoms) {
		// Clears output data structures
		coeffs.clear();
		atoms.clear();

		// For each atom in the body's expression.
		for (int atomIndex = 0; atomIndex < expression.getAtoms().size(); atomIndex++) {
			// Summation atoms will get replaced using the substitutions.
			if (expression.getAtoms().get(atomIndex) instanceof SummationAtom) {
				// Separates SummationVariable args and substitutes Constants for Variables
				SummationAtom atom = (SummationAtom)expression.getAtoms().get(atomIndex);
				SummationVariableOrTerm[] atomArgs = atom.getArguments();

				// Each position will either be null if there is no summation variable
				// at that position in the atom, or the summation atom that appears at
				// that argument position.
				SummationVariable[] sumVars = new SummationVariable[atomArgs.length];

				// Each position will be either null if a summation atom appears at this location,
				// or the actual constant that will appear in the ground rule if there
				// is no summation atom at that position.
				Constant[] partialGrounding = new Constant[atomArgs.length];

				for (int termIndex = 0; termIndex < atomArgs.length; termIndex++) {
					if (atomArgs[termIndex] instanceof SummationVariable) {
						sumVars[termIndex] = (SummationVariable)atomArgs[termIndex];
						partialGrounding[termIndex] = null;
					} else if (atomArgs[termIndex] instanceof Variable) {
						sumVars[termIndex] = null;
						partialGrounding[termIndex] = grounding.get(varMap.get(atomArgs[termIndex]));
					} else {
						sumVars[termIndex] = null;
						partialGrounding[termIndex] = (Constant)atomArgs[termIndex];
					}
				}

				// TEST
				// double coeffValue = expression.getAtomCoefficients().get(atomIndex).getValue(subs);
				double coeffValue = 0;

				// Iterates over cross product of SummationVariable substitutions and add them to
				// |atoms| and |coeff|.
				populateCoeffsAndAtomsForSummationAtom(coeffs, atoms,
						atom.getPredicate(), sumVars,
						partialGrounding, atomManager,
						groundSummationAtoms, subs, coeffValue, 0, 0);
			} else {
				// Non-summation atoms will get converted into ground atoms.
				GroundAtom atom = getGroundAtom((Atom)expression.getAtoms().get(atomIndex),
						grounding, varMap, atomManager);

				if (validateGroundAtom(atom, 0, groundSummationAtoms)) {
					atoms.add(atom);
					// TEST
					// coeffs.add(expression.getAtomCoefficients().get(atomIndex).getValue(subs));
					coeffs.add(new Double(0.0));
				}
			}
		}
	}

	/**
	 * Get a ground atom as if there were no summation variables.
	 */
	private GroundAtom getGroundAtom(SummationAtom atom, List<Constant> grounding,
			Map<Variable, Integer> varMap, AtomManager atomManager) {
		SummationVariableOrTerm[] atomArgs = atom.getArguments();
		Constant[] args = new Constant[atomArgs.length];

		for (int termIndex = 0; termIndex < args.length; termIndex++) {
			SummationVariableOrTerm term = atomArgs[termIndex];
			if (term instanceof Variable) {
				args[termIndex] = grounding.get(varMap.get((Variable)term));
			} else if (term instanceof SummationVariable) {
				args[termIndex] = grounding.get(varMap.get(((SummationVariable)term).getVariable()));
			} else {
				args[termIndex] = (Constant)term;
			}
		}

		return atomManager.getAtom(atom.getPredicate(), args);
	}

	/**
	 * Get a ground atom from an atom and its groundings.
	 */
	private GroundAtom getGroundAtom(Atom atom, List<Constant> grounding,
			Map<Variable, Integer> varMap, AtomManager atomManager) {
		Term[] atomArgs = atom.getArguments();
		Constant[] args = new Constant[atomArgs.length];

		for (int termIndex = 0; termIndex < args.length; termIndex++) {
			if (atomArgs[termIndex] instanceof Variable) {
				args[termIndex] = grounding.get(varMap.get(atomArgs[termIndex]));
			} else {
				args[termIndex] = (Constant)atomArgs[termIndex];
			}
		}

		return atomManager.getAtom(atom.getPredicate(), args);
	}

	/**
	 * Recursively grounds GroundAtoms by replacing SummationVariables with all constants.
	 */
	private void populateCoeffsAndAtomsForSummationAtom(List<Double> coeffs, List<GroundAtom> atoms,
			Predicate predicate, SummationVariable[] sumVars,
			Constant[] partialGrounding, AtomManager atomManager,
			Set<GroundAtom> groundSummationAtoms,
			Map<SummationVariable, Set<Constant>> subs, double coeff,
			int termIndex, int summationVariableCount) {
		// If we have already examined all terms.
		if (termIndex == partialGrounding.length) {
			GroundAtom atom = atomManager.getAtom(predicate, partialGrounding);
			if (validateGroundAtom(atom, summationVariableCount, groundSummationAtoms)) {
				atoms.add(atom);
				coeffs.add(coeff);
			}
		} else if (sumVars[termIndex] == null) {
			// This term is a not a summation variable, move to the next term.
			populateCoeffsAndAtomsForSummationAtom(coeffs, atoms, predicate, sumVars,
					partialGrounding, atomManager, groundSummationAtoms, subs, coeff,
					termIndex + 1, summationVariableCount);
		} else {
			// This term is a summation variable, go through all substitutions for this variable.
			for (Constant sub : subs.get(sumVars[termIndex])) {
				partialGrounding[termIndex] = sub;
				populateCoeffsAndAtomsForSummationAtom(coeffs, atoms, predicate, sumVars,
						partialGrounding, atomManager, groundSummationAtoms, subs, coeff,
						termIndex + 1, summationVariableCount + 1);
			}
		}
	}

	/**
	 * Last chance to invalidate a ground atom before it is added to a ground rule.
	 * |summationVariableCount| is the number of summation variables in this
	 * specific atom.
	 */
	private boolean validateGroundAtom(GroundAtom atom,
			int summationVariableCount, Set<GroundAtom> groundSummationAtoms) {
		// Multiple summation variables in the same atom can put us in a situation
		// where we create ground atoms that do not actually exist.
		// Invalidate these.
		// Skip the costly check for the simple cases.
		if (summationVariableCount >= 2) {
			if (!groundSummationAtoms.contains(atom)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * The actual grounding into the GroundRuleStore.
	 * @return the number of ground rules added to the store.
	 */
	private int ground(GroundRuleStore groundRuleStore, List<Double>coeffs, List<GroundAtom> atoms, double finalCoeff) {
		double[] coeffArray = new double[coeffs.size()];
		for (int j = 0; j < coeffArray.length; j++) {
			coeffArray[j] = coeffs.get(j);
		}
		GroundAtom[] atomArray = atoms.toArray(new GroundAtom[atoms.size()]);

		if (FunctionComparator.Equality.equals(expression.getComparator())) {
			groundRuleStore.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.LargerThan, finalCoeff));
			groundRuleStore.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.SmallerThan, finalCoeff));
			return 2;
		} else {
			groundRuleStore.addGroundRule(makeGroundRule(coeffArray, atomArray, expression.getComparator(), finalCoeff));
			return 1;
		}
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

	/**
	 * Holds the SummationValues for a single filter.
	 * Since each component of the disjunction (we usually will call conjunction, but can also be a negation)
	 * needs to be evaluated independently and has an independent set of variables used, we
	 * will need to keep track of each separately.
	 */
	private static class SummationDisjunctionValues {
		// Each conjunction/negation/atom that makes up the disjunction.
		private List<SummationValues> components;

		public SummationDisjunctionValues() {
			components = new ArrayList<SummationValues>();
		}

		public void addComponent(SummationValues component) {
			this.components.add(component);
		}

		public boolean getEvaluation(Constant[] groundings, Map<Variable, Integer> variableMap) {
			for (SummationValues component : components) {
				if (component.getEvaluation(groundings, variableMap)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * All the non-summation variables, groundings, and truth value of a filter grounding.
	 */
	private static class SummationValues {
		// In the case that there are no variables used in to this filter (all constants),
		// we will use this key to access the single value.
		// Since we only hold true values, we will not even need to read the value, just check the
		// size of the map.
		private static final Constant SINGLE_VALUE_KEY = null;

		// The non-summation variables used in this filter.
		// Ordered lexicographically.
		private List<Variable> variables;

		// A map holding all the constants and final evaluation values.
		// We only actually hold true (falses are implicit when no key is found.
		// The keys are in the order of |variables| (which is sorted lexicographically).
		// HACK(eriq): The complex structure is to squeeze some performance out of this until we
		// have more database support.
		//
		// <Constant, Object>
		// Either:
		//	<Constant, Map<Constant, Object>>
		//	or
		//	<Constant, Boolean>
		private Map<Constant, Object> valueMap;

		public SummationValues(Set<Atom> atoms) {
			Set<Variable> setVariables = new HashSet<Variable>();
			for (Atom atom : atoms) {
				for (Term arg : atom.getArguments()) {
					if (arg instanceof Variable) {
						setVariables.add((Variable)arg);
					}
				}
			}

			this.variables = Arrays.asList(setVariables.toArray(new Variable[0]));
			Collections.sort(this.variables, new Comparator() {
				public int compare(Object a, Object b) {
					return ((Variable)a).getName().compareTo(((Variable)b).getName());
				}
			});

			valueMap = new HashMap<Constant, Object>();
		}

		/**
		 * Add a new filter evaluation.
		 */
		public void add(Constant[] groundings, Map<Variable, Integer> variableMap, boolean value) {
			assert(variableMap.size() == variables.size());

			// We don't actually hold false values.
			if (!value) {
				return;
			}

			// It is possible for there to be no variables in a filter.
			if (variables.size() == 0) {
				valueMap.put(SINGLE_VALUE_KEY, new Boolean(true));
				return;
			}

			addEvaluation(0, valueMap, groundings, variableMap);
		}

		/**
		 * Recursivley add the filter evaluation.
		 */
		private void addEvaluation(int variableIndex, Map<Constant, Object> currentMap,
				Constant[] groundings, Map<Variable, Integer> variableMap) {
			Constant currentConstant = groundings[variableMap.get(variables.get(variableIndex)).intValue()];
			// If all but one of the variables have been evaluated, then put in the evaluation.
			if (variableIndex == variables.size() - 1) {
				currentMap.put(currentConstant, new Boolean(true));
				return;
			}

			if (!currentMap.containsKey(currentConstant)) {
				currentMap.put(currentConstant, new HashMap<Constant, Object>());
			}

			addEvaluation(variableIndex + 1, (Map<Constant, Object>)currentMap.get(currentConstant), groundings, variableMap);
		}

		/**
		 * Get the evaluation for a specific grounding.
		 */
		public boolean getEvaluation(Constant[] groundings, Map<Variable, Integer> variableMap) {
			// If we have no variables we are actually tracking, then see if this is always
			// true (has any true values).
			if (variables.size() == 0) {
				return valueMap.size() == 1;
			}

			return getEvaluation(0, valueMap, groundings, variableMap);
		}

		/**
		 * Recursivley check for the evaluation.
		 */
		private boolean getEvaluation(int variableIndex, Map<Constant, Object> currentMap,
				Constant[] groundings, Map<Variable, Integer> variableMap) {
			Constant currentConstant = groundings[variableMap.get(variables.get(variableIndex)).intValue()];

			// We made it to the final map.
			if (variableIndex == variables.size() - 1) {
				return currentMap.containsKey(currentConstant);
			}

			// If there is no key for this constant, then the evaluation is false.
			if (!currentMap.containsKey(currentConstant)) {
				return false;
			}

			return getEvaluation(variableIndex + 1, (Map<Constant, Object>)currentMap.get(currentConstant), groundings, variableMap);
		}

		public String toString() {
			return "{{" + variables.toString() + " -- " + valueMap.toString() + "}}";
		}
	}

	protected abstract AbstractGroundArithmeticRule makeGroundRule(double[] coeffs,
			GroundAtom[] atoms, FunctionComparator comparator, double c);

	protected abstract AbstractGroundArithmeticRule makeGroundRule(List<Double> coeffs,
			List<GroundAtom> atoms, FunctionComparator comparator, double c);

	// TODO(eriq): Remove this once global configuration is implemented.
	public static void setDelim(String delim) {
		DELIM = delim;
	}
}
