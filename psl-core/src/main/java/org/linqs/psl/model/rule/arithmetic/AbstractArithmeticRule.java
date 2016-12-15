/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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

	protected final ArithmeticRuleExpression expression;
	protected final Map<SummationVariable, Formula> selects;

	public AbstractArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> selectStatements) {
		this.expression = expression;
		this.selects = selectStatements;

		/* Ensures that all Formulas are in DNF */
		for (Map.Entry<SummationVariable, Formula> e : this.selects.entrySet()) {
			e.setValue(e.getValue().getDNF());
		}

		validateRule();
	}

	@Override
	public void groundAll(AtomManager atomManager, GroundRuleStore grs) {
		validateGroundRule(atomManager);

		// Evaluate the selects.
		Map<SummationVariable, SummationValues> selectEvaluations = evaluateSelects(atomManager);

		// Later, we will collect all the non-summation grounding values, so we will need to know which
		// variables are non-summations.
		List<Variable> nonSummationVariables = new ArrayList<Variable>();

		// Collect all the atoms from the body (expression).
		// Pretend that all atoms are non-summation atoms.
		List<Atom> queryAtoms = new ArrayList<Atom>();
		for (SummationAtomOrAtom atom : expression.getAtoms()) {
			if (atom instanceof SummationAtom) {
				queryAtoms.add(((SummationAtom)atom).getQueryAtom());

				for (SummationVariableOrTerm arg : ((SummationAtom)atom).getArguments()) {
					if (arg instanceof Variable && !nonSummationVariables.contains((Variable)arg)) {
						nonSummationVariables.add((Variable)arg);
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

		/* Executes initial query */
		ResultList rawGroundings = atomManager.executeQuery(query);

		Map<Variable, Integer> groundingVariableMap = rawGroundings.getVariableMap();

		// <Non-Summation Values, <Summation Variable, Summation Replacements>>
		Map<List<Constant>, Map<SummationVariable, Set<Constant>>> summationSubs =
				new HashMap<List<Constant>, Map<SummationVariable, Set<Constant>>>();

		for (int i = 0; i < rawGroundings.size(); i++) {
			Constant[] rawGrounding = rawGroundings.get(i);

			// Put all the non-summation constants into a list.
			// Note that we know the size ahead of time and would have used an array, but this will need to
			// be a key in a map so must be an Object.
			List<Constant> nonSummationConstants = new ArrayList<Constant>();
			for (Variable nonSummationVariable : nonSummationVariables) {
				nonSummationConstants.add(rawGrounding[groundingVariableMap.get(nonSummationVariable).intValue()]);
			}

			if (!summationSubs.containsKey(nonSummationConstants)) {
				summationSubs.put(nonSummationConstants, new HashMap<SummationVariable, Set<Constant>>());
			}
			Map<SummationVariable, Set<Constant>> subs = summationSubs.get(nonSummationConstants);

			// Add summation values that pass the select into the set of summation subs.
			for (Map.Entry<SummationVariable, SummationValues> selectEvaluation : selectEvaluations.entrySet()) {
				SummationVariable sumVar = selectEvaluation.getKey();
				if (!subs.containsKey(sumVar)) {
					subs.put(sumVar, new HashSet<Constant>());
				}

				if (selectEvaluation.getValue().getEvaluation(rawGrounding, groundingVariableMap)) {
					subs.get(sumVar).add(rawGrounding[groundingVariableMap.get(sumVar.getVariable()).intValue()]);
				}
			}
		}

		List<Double> coeffs = new LinkedList<Double>();
		List<GroundAtom> atoms = new LinkedList<GroundAtom>();
      for (Map.Entry<List<Constant>, Map<SummationVariable, Set<Constant>>> summationSub : summationSubs.entrySet()) {
			populateCoeffsAndAtoms(coeffs, atoms, summationSub.getKey(), subsVariableMap, atomManager, summationSub.getValue());
			ground(grs, coeffs, atoms, expression.getFinalCoefficient().getValue(summationSub.getValue()));

         coeffs.clear();
         atoms.clear();
      }
	}

	private Map<SummationVariable, SummationValues> evaluateSelects(AtomManager atomManager) {
		Map<SummationVariable, SummationValues> summationEvals = new HashMap<SummationVariable, SummationValues>();

		for (Map.Entry<SummationVariable, Formula> select : selects.entrySet()) {
			Formula selectFormula = select.getValue().getDNF();

			Set<Atom> queryAtomsSet = new HashSet<Atom>();
			queryAtomsSet = selectFormula.getAtoms(queryAtomsSet);

			SummationValues summationEval = new SummationValues(queryAtomsSet);
			Atom[] queryAtoms = queryAtomsSet.toArray(new Atom[0]);

			DatabaseQuery query;
			if (queryAtoms.length > 1) {
				query = new DatabaseQuery(new Conjunction(queryAtoms));
			} else {
				query = new DatabaseQuery(queryAtoms[0]);
			}

			/* Executes initial query */
			ResultList selectGroundings = atomManager.executeQuery(query);

			for (int i = 0; i < selectGroundings.size(); i++) {
				boolean evaluationValue = evaluateSelectGrounding(atomManager,
						selectFormula, queryAtoms,
						selectGroundings.get(i), selectGroundings.getVariableMap());
				summationEval.add(selectGroundings.get(i), selectGroundings.getVariableMap(), evaluationValue);
			}

			summationEvals.put(select.getKey(), summationEval);
		}

		return summationEvals;
	}

	/**
	 * Recursively evaluate a select grounding.
	 * The input formual should already be in DNF.
	 */
	private boolean evaluateSelectGrounding(
			AtomManager atomManager,
			Formula formula, Atom[] atoms,
			Constant[] groundings, Map<Variable, Integer> variableMap) {
		if (formula instanceof Conjunction) {
			Conjunction conjunction = (Conjunction)formula;
			for (int i = 0; i < conjunction.getNoFormulas(); i++) {
				if (!evaluateSelectGrounding(atomManager, conjunction.get(i), atoms, groundings, variableMap)) {
					// Short circuit.
					return false;
				}
			}
			return true;
		} else if (formula instanceof Disjunction) {
			Disjunction disjunction = (Disjunction)formula;
			for (int i = 0; i < disjunction.getNoFormulas(); i++) {
				if (evaluateSelectGrounding(atomManager, disjunction.get(i), atoms, groundings, variableMap)) {
					// Short circuit.
					return true;
				}
			}
			return false;
		} else if (formula instanceof Negation) {
			return !(evaluateSelectGrounding(atomManager, ((Negation)formula).getFormula(), atoms, groundings, variableMap));
		} else if (formula instanceof Atom) {
			Atom atom = (Atom)formula;
			Term[] args = atom.getArguments();
			Constant[] groundValues = new Constant[args.length];

			// TODO(eriq): Deal with constants (check more, more pondering)
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

	protected void populateCoeffsAndAtoms(List<Double> coeffs, List<GroundAtom> atoms, List<Constant> grounding,
			Map<Variable, Integer> varMap, AtomManager atomManager, Map<SummationVariable, Set<Constant>> subs) {
		/* Clears output data structures */
		coeffs.clear();
		atoms.clear();

		/* Does population */
		for (int j = 0; j < expression.getAtoms().size(); j++) {
			/* Handles a SummationAtom */
			if (expression.getAtoms().get(j) instanceof SummationAtom) {
				/* Separates SummationVariable args and substitutes Constants for Variables */
				SummationAtom atom = (SummationAtom) expression.getAtoms().get(j);
				SummationVariableOrTerm[] atomArgs = atom.getArguments();
				SummationVariable[] sumVars = new SummationVariable[atom.getArguments().length];
				Constant[] partialGrounding = new Constant[atom.getArguments().length];
				for (int k = 0; k < partialGrounding.length; k++) {
					if (atomArgs[k] instanceof SummationVariable) {
						sumVars[k] = (SummationVariable) atomArgs[k];
						partialGrounding[k] = null;
					}
					else if (atomArgs[k] instanceof Variable) {
						sumVars[k] = null;
						partialGrounding[k] = grounding.get(varMap.get(atomArgs[k]));
					}
					else {
						sumVars[k] = null;
						partialGrounding[k] = (Constant) atomArgs[k];
					}
				}

				/* Iterates over cross product of SummationVariable substitutions */
				double coeffValue = expression.getAtomCoefficients().get(j).getValue(subs);
				populateCoeffsAndAtomsForSummationAtom(coeffs, atoms, atom.getPredicate(), 0, sumVars, partialGrounding, atomManager, subs, coeffValue);
			} else {
				/* Handles an Atom */
				Atom atom = (Atom) expression.getAtoms().get(j);
				Term[] atomArgs = atom.getArguments();
				Constant[] args = new Constant[atom.getArguments().length];
				for (int k = 0; k < args.length; k++) {
					if (atomArgs[k] instanceof Variable) {
						args[k] = grounding.get(varMap.get(atomArgs[k]));
					} else {
						args[k] = (Constant) atomArgs[k];
					}
				}
				atoms.add(atomManager.getAtom(atom.getPredicate(), args));
				coeffs.add(expression.getAtomCoefficients().get(j).getValue(subs));
			}
		}
	}

	/**
	 * Recursively grounds GroundAtoms by replacing SummationVariables with all constants.
	 */
	protected void populateCoeffsAndAtomsForSummationAtom(List<Double> coeffs, List<GroundAtom> atoms, Predicate p,
			int index, SummationVariable[] sumVars, Constant[] partialGrounding, AtomManager atomManager,
			Map<SummationVariable, Set<Constant>> subs, double coeff) {
		if (index == partialGrounding.length) {
			atoms.add(atomManager.getAtom(p, partialGrounding));
			coeffs.add(coeff);
		} else if (sumVars[index] == null) {
			populateCoeffsAndAtomsForSummationAtom(coeffs, atoms, p, index + 1, sumVars, partialGrounding, atomManager, subs, coeff);
		} else {
			for (Constant sub : subs.get(sumVars[index])) {
				partialGrounding[index] = sub;
				populateCoeffsAndAtomsForSummationAtom(coeffs, atoms, p, index + 1, sumVars, partialGrounding, atomManager, subs, coeff);
			}
		}
	}
	
	protected void ground(GroundRuleStore grs, List<Double>coeffs, List<GroundAtom> atoms, double finalCoeff) {
		double[] coeffArray = new double[coeffs.size()];
		for (int j = 0; j < coeffArray.length; j++) {
			coeffArray[j] = coeffs.get(j);
		}
		GroundAtom[] atomArray = atoms.toArray(new GroundAtom[atoms.size()]);

		if (FunctionComparator.Equality.equals(expression.getComparator())) {
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.LargerThan, finalCoeff));
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.SmallerThan, finalCoeff));
		} else {
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, expression.getComparator(), finalCoeff));
		}
	}

	/**
	 * Validate what we can about an abstract rule at creation:
	 *	 - An argument to a select must appear in the arithmetic expression.
	 *	 - All variables used in a select are either the argument to the select or
	 *	  appear in the arithmetic expression.
	 */
	protected void validateRule() {
		/* Ensure all select arguments appear in the arithmetic expression. */
		for (SummationVariable selectArg : selects.keySet()) {
			if (!expression.getSummationVariables().contains(selectArg)) {
				throw new IllegalArgumentException(String.format(
						"Unknown variable (%s) used as select argument. " +
						"All select arguments must appear as summation variables in associated arithmetic expression.",
						selectArg.getVariable().getName()));
			}
		}

		/* Ensure all variables used in the selects are either the argument summation variable or in the expression. */
		Set<String> expressionVariableNames = new HashSet<String>();
		for (Variable var : expression.getVariables()) {
			expressionVariableNames.add(var.getName());
		}

		for (Map.Entry<SummationVariable, Formula> select : selects.entrySet()) {
			VariableTypeMap selectVars = new VariableTypeMap();
			select.getValue().collectVariables(selectVars);

			for (Variable var : selectVars.keySet()) {
				if (!(select.getKey().getVariable().getName().equals(var.getName()) || expressionVariableNames.contains(var.getName()))) {
					throw new IllegalArgumentException(String.format(
							"Unknown variable (%s) used in select. " +
							"All select variables must either be the select argument or appear " +
							"in the associated arithmetic expression.",
							var.getName()));
				}
			}
		}
	}

	/**
	 * Validate the abstract rule in the context of of grounding.
	 * Ensure that no open predicates are being used in a select.
	 */
	public void validateGroundRule(AtomManager atomManager) {
		Set<Atom> selectAtoms = new HashSet<Atom>();
		for (Formula select : selects.values()) {
			select.getAtoms(selectAtoms);
		}

		for (Atom selectAtom : selectAtoms) {
			if (selectAtom.getPredicate() instanceof StandardPredicate
					&& !atomManager.isClosed(((StandardPredicate)selectAtom.getPredicate()))) {
				throw new IllegalArgumentException(String.format(
						"Open predicate (%s) not allowed in select. " +
						"Only closed predicates	may appear in selects.",
						selectAtom.getPredicate().getName()));
			}
		}
	}

	/**
	 * All the non-summation variables, groundings, and truth value of a select grounding.
	 */
	private static class SummationValues {
		// The non-summation variables used in this select.
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
		 * Add a new select evaluation.
		 */
		public void add(Constant[] groundings, Map<Variable, Integer> variableMap, boolean value) {
			assert(variableMap.size() == variables.size());

			// We don't actually hold false values.
			if (!value) {
				return;
			}

			addEvaluation(0, valueMap, groundings, variableMap);
		}

		/**
		 * Recursivley add the select evaluation.
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
		 * TODO(eriq): Input validation on the variables (it has all the required ones).
		 */
		public boolean getEvaluation(Constant[] groundings, Map<Variable, Integer> variableMap) {
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
	}

	abstract protected AbstractGroundArithmeticRule makeGroundRule(double[] coeffs,
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
