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
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.AtomEvent;
import org.linqs.psl.model.atom.AtomEventFramework;
import org.linqs.psl.model.atom.AtomManager;
import org.linqs.psl.model.atom.GroundAtom;
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
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.reasoner.function.FunctionComparator;

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

	protected final ArithmeticRuleExpression expression;
	protected final Map<SummationVariable, Formula> filters;

	public AbstractArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses) {
		this.expression = expression;
		this.filters = filterClauses;

		// Ensures that all Formulas are in DNF
		for (Map.Entry<SummationVariable, Formula> e : this.filters.entrySet()) {
			e.setValue(e.getValue().getDNF());
		}

		validateRule();
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundRuleStore grs) {
		validateGroundRule(atomManager);

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

		for (int i = 0; i < rawGroundings.size(); i++) {
			Constant[] rawGrounding = rawGroundings.get(i);

			// Store all ground summation atoms for later validation.
			for (SummationAtom summationAtom : summationAtoms) {
				groundSummationAtoms.add(getGroundAtom(summationAtom, Arrays.asList(rawGrounding), groundingVariableMap, atomManager));
			}

			// Put all the non-summation constants into a list.
			// Note that we know the size ahead of time and would have used an array, but this will need to
			// be a key in a map so it must be an Object.
			List<Constant> nonSummationConstants = new ArrayList<Constant>();
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
			groundCount += ground(grs, coeffs, atoms,
					expression.getFinalCoefficient().getValue(summationSub.getValue()));

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

			// For each conjunction/negation (component of the disjunction that is the filter).
			// We need to do each disjunction independently, because the non-exsistance of a grounding
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
	protected void populateCoeffsAndAtoms(List<Double> coeffs, List<GroundAtom> atoms,
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

				double coeffValue = expression.getAtomCoefficients().get(atomIndex).getValue(subs);

				// Iterates over cross product of SummationVariable substitutions and add them to
				// |atoms| and |coeff|.
				populateCoeffsAndAtomsForSummationAtom(coeffs, atoms,
						atom.getPredicate(), sumVars,
						partialGrounding, atomManager,
						groundSummationAtoms, subs, coeffValue, 0, 0);
			} else {
				// Non-summation atoms will get onverted into ground atoms.
				GroundAtom atom = getGroundAtom((Atom)expression.getAtoms().get(atomIndex),
						grounding, varMap, atomManager);

				if (validateGroundAtom(atom, 0, groundSummationAtoms)) {
					atoms.add(atom);
					coeffs.add(expression.getAtomCoefficients().get(atomIndex).getValue(subs));
				}
			}
		}
	}

	/**
	 * Get a ground atom as if there were no summation variables.
	 */
	protected GroundAtom getGroundAtom(SummationAtom atom, List<Constant> grounding,
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
	protected GroundAtom getGroundAtom(Atom atom, List<Constant> grounding,
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
	protected void populateCoeffsAndAtomsForSummationAtom(List<Double> coeffs, List<GroundAtom> atoms,
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
	protected boolean validateGroundAtom(GroundAtom atom,
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
	protected int ground(GroundRuleStore grs, List<Double>coeffs, List<GroundAtom> atoms, double finalCoeff) {
		double[] coeffArray = new double[coeffs.size()];
		for (int j = 0; j < coeffArray.length; j++) {
			coeffArray[j] = coeffs.get(j);
		}
		GroundAtom[] atomArray = atoms.toArray(new GroundAtom[atoms.size()]);

		if (FunctionComparator.Equality.equals(expression.getComparator())) {
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.LargerThan, finalCoeff));
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.SmallerThan, finalCoeff));
			return 2;
		} else {
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, expression.getComparator(), finalCoeff));
			return 1;
		}
	}

	/**
	 * Validate what we can about an abstract rule at creation:
	 *	 - An argument to a filter must appear in the arithmetic expression.
	 *	 - All variables used in a filter are either the argument to the filter or
	 *	  appear in the arithmetic expression.
	 */
	protected void validateRule() {
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
	 * Since each component of the disjunction (we will call conjunction, but can also be a negation)
	 * needs to be evaluated independently and has an independent set of variables used, we
	 * will need to keep track of each separately.
	 */
	private static class SummationDisjunctionValues {
		// Each conjunction/negation/atom that makes of the disjunction.
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
		//   <Constant, Map<Constant, Object>>
		//   or
		//   <Constant, Boolean>
		private Map<Constant, Object> valueMap;

		public SummationValues(Set<Atom> atoms) {
			Set<Variable> setVariables = new HashSet<Variable>();
			for (Atom atom : atoms) {
				Term[] args = atom.getArguments();
				for (Term arg : args) {
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

	@Override
	protected void notifyAtomEvent(AtomEvent event, GroundRuleStore grs) {
		throw new UnsupportedOperationException("Arithmetic rules do not support atom events.");
	}

	@Override
	protected void registerForAtomEvents(AtomEventFramework eventFramework) {
		throw new UnsupportedOperationException("Arithmetic rules do not support atom events.");
	}

	@Override
	protected void unregisterForAtomEvents(AtomEventFramework eventFramework) {
		throw new UnsupportedOperationException("Arithmetic rules do not support atom events.");
	}

}
