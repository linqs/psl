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
package edu.umd.cs.psl.model.rule.arithmetic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.psl.application.groundrulestore.GroundRuleStore;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Disjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.rule.AbstractRule;
import edu.umd.cs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationAtom;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariable;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import edu.umd.cs.psl.model.term.Constant;
import edu.umd.cs.psl.model.term.Term;
import edu.umd.cs.psl.model.term.Variable;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

/**
 * Base class for all (first order, i.e., not ground) arithmetic rules.
 * 
 * @author Stephen Bach
 */
abstract public class AbstractArithmeticRule extends AbstractRule {
	
	protected final ArithmeticRuleExpression expression;
	protected final Map<SummationVariable, Formula> selects;
	
	public AbstractArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> selectStatements) {
		this.expression = expression;
		this.selects = selectStatements;
		
		/* Ensures that all Formulas are in DNF */
		for (Map.Entry<SummationVariable, Formula> e : this.selects.entrySet()) {
			e.setValue(e.getValue().getDNF());
		}
		
		//TODO: Input validation
	}
	
	@Override
	public void groundAll(AtomManager atomManager, GroundRuleStore grs) {
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
		DatabaseQuery query = new DatabaseQuery(new Conjunction((Formula[]) queryAtoms.toArray()));
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
		Variable[] varMap = groundings.getVariableMap();
		for (int i = 0; i < groundings.size(); i++) {
			populateSummationVariableSubs(subs, groundings.get(i), varMap, atomManager);
			populateCoeffsAndAtoms(coeffs, atoms, groundings.get(i), varMap, atomManager, subs);
			ground(grs, coeffs, atoms, expression.getFinalCoefficient().getValue(subs));
		}
	}
	
	private void populateSummationVariableSubs(Map<SummationVariable, Set<Constant>> subs,
			Constant[] grounding, Variable[] varMap, AtomManager atomManager) {
		/* Clears output data structure */
		for (Set<Constant> constants : subs.values()) {
			constants.clear();
		}
		
		for (Map.Entry<SummationVariable, Set<Constant>> e : subs.entrySet()) {
			Disjunction clauses = (Disjunction) selects.get(e.getKey());
		}
	}
	
	private void populateCoeffsAndAtoms(List<Double> coeffs, List<GroundAtom> atoms, Constant[] grounding,
			Variable[] varMap, AtomManager atomManager, Map<SummationVariable, Set<Constant>> subs) {
		/* Clears output data structures */
		coeffs.clear();
		atoms.clear();
		
		Map<SummationVariable, Integer> sumVarIndices = new HashMap<SummationVariable, Integer>();
		for (int j = 0; j < expression.getAtoms().size(); j++) {
			if (expression.getAtoms().get(j) instanceof SummationAtom) {
				SummationAtom atom = (SummationAtom) expression.getAtoms().get(j);
				SummationVariableOrTerm[] atomArgs = atom.getArguments();
				Constant[] args = new Constant[atom.getArguments().length];
				for (int k = 0; k < args.length; k++) {
					if (atomArgs[k] instanceof SummationVariable) {
						sumVarIndices.put((SummationVariable) atomArgs[k], k);
					}
					else if (atomArgs[k] instanceof Variable) {
						args[k] = grounding[varSubMap[j][k]];
					}
					else {
						args[k] = (Constant) atomArgs[k];
					}
				}
				
				/* Iterates over cross-product of SummationVariable substitutions */
			}
			else {
				Atom atom = (Atom) expression.getAtoms().get(j);
				Term[] atomArgs = atom.getArguments();
				Constant[] args = new Constant[atom.getArguments().length];
				for (int k = 0; k < args.length; k++) {
					if (atomArgs[k] instanceof Variable) {
						args[k] = grounding[varSubMap[j][k]];
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
	
	private void ground(GroundRuleStore grs, List<Double>coeffs, List<GroundAtom> atoms, double finalCoeff) {
		double[] coeffArray = new double[coeffs.size()];
		for (int j = 0; j < coeffArray.length; j++) {
			coeffArray[j] = coeffs.get(j);
		}
		GroundAtom[] atomArray = (GroundAtom[]) atoms.toArray();
		
		if (FunctionComparator.Equality.equals(expression.getComparator())) {
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.LargerThan, finalCoeff));
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, FunctionComparator.SmallerThan, finalCoeff));
		}
		else {
			grs.addGroundRule(makeGroundRule(coeffArray, atomArray, expression.getComparator(), finalCoeff));
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
