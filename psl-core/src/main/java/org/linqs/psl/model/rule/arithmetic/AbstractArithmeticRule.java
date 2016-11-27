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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		/* Ensure that no open predicates are being used in a select. */
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

		/* Constructs initial query */
		List<Atom> queryAtoms = new LinkedList<Atom>();
		for (SummationAtomOrAtom saoa : expression.getAtoms()) {
			if (saoa instanceof SummationAtom) {
				queryAtoms.add(((SummationAtom) saoa).getQueryAtom());
			}
			else {
				queryAtoms.add((Atom) saoa);
			}
		}
		DatabaseQuery query;
		if (queryAtoms.size() > 1) {
			query = new DatabaseQuery(new Conjunction(queryAtoms.toArray(new Formula[0])));
		}
		else {
			query = new DatabaseQuery(queryAtoms.get(0));
		}
		query.getProjectionSubset().addAll(expression.getVariables());

		/* Executes initial query */
		ResultList groundings = atomManager.executeQuery(query);

		/* Prepares data structure for SummationVariable substitutions */
		Map<SummationVariable, Set<Constant>> subs = new HashMap<SummationVariable, Set<Constant>>();
		for (SummationVariable sumVar : expression.getSummationVariables()) {
			subs.put(sumVar, new HashSet<Constant>());
		}

		/* Processes results */
		List<Double> coeffs = new LinkedList<Double>();
		List<GroundAtom> atoms = new LinkedList<GroundAtom>();
		Map<Variable, Integer> varMap = groundings.getVariableMap();
		for (int i = 0; i < groundings.size(); i++) {
			populateSummationVariableSubs(subs, groundings.get(i), varMap, atomManager);
			populateCoeffsAndAtoms(coeffs, atoms, groundings.get(i), varMap, atomManager, subs);
			ground(grs, coeffs, atoms, expression.getFinalCoefficient().getValue(subs));
		}
	}

	private void populateSummationVariableSubs(Map<SummationVariable, Set<Constant>> subs,
			Constant[] grounding, Map<Variable, Integer> varMap, AtomManager atomManager) {
		/* Clears output data structure */
		for (Set<Constant> constants : subs.values()) {
			constants.clear();
		}

		for (Map.Entry<SummationVariable, Set<Constant>> e : subs.entrySet()) {
			Formula select = selects.get(e.getKey());

			/* If there is no select statement for a summation variable, then the arithmetic rule expression is used */
			if (select == null) {
				List<Atom> queryAtoms = new LinkedList<Atom>();
				for (SummationAtomOrAtom atom : this.expression.getAtoms()) {
					if (atom instanceof SummationAtom) {
						queryAtoms.add(((SummationAtom) atom).getQueryAtom());
					}
					else {
						queryAtoms.add((Atom) atom);
					}
				}
				if (queryAtoms.size() == 1) {
					select = queryAtoms.get(0);
				}
				else {
					select = new Conjunction(queryAtoms.toArray(new Formula[0]));
				}
			}

			select = select.getDNF();

			// TEST
			System.out.println("TEST4.0: " + select);

			Formula[] queries;
			if (select instanceof Disjunction) {
				// TEST
				System.out.println("   TEST4.1");

				queries = new Formula[((Disjunction) select).getNoFormulas()];
				for (int i = 0; i < queries.length; i++) {
					queries[i] = ((Disjunction) select).get(i);
				}
			}
			else {
				// TEST
				System.out.println("   TEST4.2");

				queries = new Formula[] {select};
			}

			for (Formula f : queries) {
				// TEST
				System.out.println("TEST5.0: " + f);

				DatabaseQuery query = new DatabaseQuery(f);
				ResultList results = atomManager.executeQuery(query);
				// TEST
				System.out.println("   TEST5.1: " + results);

				for (int j = 0; j < results.size(); j++) {
					// TEST
					System.out.println("   TEST5.3: " + results.get(j, e.getKey().getVariable()));

					// TODO(eriq): This is broken when we use a variable that is not the summation variable.
					//  The foreach of the map up top assumes that we are only working with
					//  the summation variable. Then this will null pointer out since the results don't
					//  have this column.
					e.getValue().add(results.get(j, e.getKey().getVariable()));
				}

				// TEST
				System.out.println("TEST5.5");
			}
		}
	}

	private void populateCoeffsAndAtoms(List<Double> coeffs, List<GroundAtom> atoms, Constant[] grounding,
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
						partialGrounding[k] = grounding[varMap.get(atomArgs[k])];
					}
					else {
						sumVars[k] = null;
						partialGrounding[k] = (Constant) atomArgs[k];
					}
				}

				/* Iterates over cross product of SummationVariable substitutions */
				double coeffValue = expression.getAtomCoefficients().get(j).getValue(subs);
				populateCoeffsAndAtomsForSummationAtom(coeffs, atoms, atom.getPredicate(), 0, sumVars, partialGrounding, atomManager, subs, coeffValue);
			}
			/* Handles an Atom */
			else {
				Atom atom = (Atom) expression.getAtoms().get(j);
				Term[] atomArgs = atom.getArguments();
				Constant[] args = new Constant[atom.getArguments().length];
				for (int k = 0; k < args.length; k++) {
					if (atomArgs[k] instanceof Variable) {
						args[k] = grounding[varMap.get(atomArgs[k])];
					}
					else {
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
	private void populateCoeffsAndAtomsForSummationAtom(List<Double> coeffs, List<GroundAtom> atoms, Predicate p,
			int index, SummationVariable[] sumVars, Constant[] partialGrounding, AtomManager atomManager,
			Map<SummationVariable, Set<Constant>> subs, double coeff) {
		if (index == partialGrounding.length) {
			atoms.add(atomManager.getAtom(p, partialGrounding));
			coeffs.add(coeff);
		}
		else if (sumVars[index] == null) {
			populateCoeffsAndAtomsForSummationAtom(coeffs, atoms, p, index + 1, sumVars, partialGrounding, atomManager, subs, coeff);
		}
		else {
			for (Constant sub : subs.get(sumVars[index])) {
				partialGrounding[index] = sub;
				populateCoeffsAndAtomsForSummationAtom(coeffs, atoms, p, index + 1, sumVars, partialGrounding, atomManager, subs, coeff);
			}
		}
	}

	private void ground(GroundRuleStore grs, List<Double>coeffs, List<GroundAtom> atoms, double finalCoeff) {
		double[] coeffArray = new double[coeffs.size()];
		for (int j = 0; j < coeffArray.length; j++) {
			coeffArray[j] = coeffs.get(j);
		}
		GroundAtom[] atomArray = atoms.toArray(new GroundAtom[atoms.size()]);

		if (FunctionComparator.Equality.equals(expression.getComparator())) {
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.LargerThan, finalCoeff));
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.SmallerThan, finalCoeff));
		}
		else {
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, expression.getComparator(), finalCoeff));
		}
	}

	/**
	 * Validate what we can about an abstract rule at creation:
	 *	 - An argument to a select must appear in the arithmetic expression.
	 *	 - All variables used in a select are either the argument to the select or
	 *     appear in the arithmetic expression.
	 */
	private void validateRule() {
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
