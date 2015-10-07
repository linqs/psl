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
package edu.umd.cs.psl.model.formula;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.model.argument.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;

/**
 * A PSL rule containing averaging conjunctions. These rules are restricted to
 * implications where the body is a Lukasiewicz conjunction of averaging
 * conjunctions of literals, and the head is a Lukasiewicz disjunction of
 * averaging conjunctions of literals.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class AvgConjRule implements Formula {

	protected final Formula ACRuleFormula;
	
	protected final Atom[] posLiterals;
	protected final Atom[] negLiterals;
	protected final Double[] posLiteralsWeights; 
	protected final Double[] negLiteralsWeights;
	
	public AvgConjRule(Formula ACRuleFormula) {
		assert ACRuleFormula!=null;
		this.ACRuleFormula = ACRuleFormula;
		//Compute weighted DNF
		
		HashSet<Atom> posLiteralsHS = new HashSet<Atom>();
		HashSet<Atom> negLiteralsHS = new HashSet<Atom>();
		HashMap<Atom, Double> posLiteralsWeightsHM = new HashMap<Atom, Double>();
		HashMap<Atom, Double> negLiteralsWeightsHM = new HashMap<Atom, Double>();
		
		assert ACRuleFormula instanceof Rule;
		Rule implicationRule = (Rule) ACRuleFormula;
		Formula body = implicationRule.getBody();
		Formula head = implicationRule.getHead();
		assert body instanceof Conjunction || body instanceof AvgConjunction || body instanceof Atom || body instanceof Negation;
		assert head instanceof Disjunction || head instanceof AvgConjunction || head instanceof Atom || head instanceof Negation;

		//We have an implication rule, body >> head, i.e. ~body v head.  So the body is currently negated.
		//Note that the final hinge loss encodes its distance to satisfaction,
		//1 - (body >> head) = ~(body >> head) = body & ~head, so this will later be reversed.
		
		Formula[] bodyFormulae;
		if (body instanceof Conjunction) {
			Conjunction conj = ((Conjunction) body).flatten();
			bodyFormulae = new Formula[conj.getNoFormulas()];
			for (int c = 0; c < conj.getNoFormulas(); c++) {
				bodyFormulae[c] = conj.get(c);
			}
		}
		else {
			bodyFormulae = new Formula[1];
			bodyFormulae[0] = body;
		}
		for (int c = 0; c < bodyFormulae.length; c++) {
			Formula conjunct = bodyFormulae[c];
			if (conjunct instanceof Atom) {
				includeLiteral((Atom)conjunct, 1.0, negLiteralsHS, negLiteralsWeightsHM);			
			} else if (conjunct instanceof Negation) {
				assert ((Negation) conjunct).getFormula() instanceof Atom; //it must be a literal
				Atom a = (Atom ) ((Negation) conjunct).getFormula();
				includeLiteral(a, 1.0, posLiteralsHS, posLiteralsWeightsHM);
			} else if (conjunct instanceof AvgConjunction) {
				AvgConjunction ac = ((AvgConjunction) conjunct).flatten();
				int numFormulas = ac.getNoFormulas();
				for (int i = 0; i < numFormulas; i++) {
					Formula avgConjunct = ac.get(i);
					assert avgConjunct instanceof Atom || avgConjunct instanceof Negation;
					if (avgConjunct instanceof Atom) {
						includeLiteral((Atom)avgConjunct, 1.0 / (double) numFormulas, negLiteralsHS, negLiteralsWeightsHM);
					} else {
						assert ((Negation) avgConjunct).getFormula() instanceof Atom; //it must be a literal
						Atom a = (Atom ) ((Negation) avgConjunct).getFormula();
						includeLiteral((Atom)a, 1.0 / (double) numFormulas, posLiteralsHS, posLiteralsWeightsHM);
					}
				}			
			} else throw new IllegalStateException("body of average conjunction rule not of a recognized type.");
		}
		
		Formula[] headFormulae;
		if (head instanceof Disjunction) {
			Disjunction disj = ((Disjunction) head).flatten();
			headFormulae = new Formula[disj.getNoFormulas()];
			for (int d = 0; d < disj.getNoFormulas(); d++) {
				headFormulae[d] = disj.get(d);
			}
		}
		else {
			headFormulae = new Formula[1];
			headFormulae[0] = head;
		}
		for (int d = 0; d < headFormulae.length; d++) {
			Formula disjunct = headFormulae[d];
			if (disjunct instanceof Atom) {
				includeLiteral((Atom)disjunct, 1.0, posLiteralsHS, posLiteralsWeightsHM);			
			} else if (disjunct instanceof Negation) {
				assert ((Negation) disjunct).getFormula() instanceof Atom; //it must be a literal
				Atom a = (Atom ) ((Negation) disjunct).getFormula();
				includeLiteral(a, 1.0, negLiteralsHS, negLiteralsWeightsHM);
			} else if (disjunct instanceof AvgConjunction) {
				AvgConjunction ac = ((AvgConjunction) disjunct).flatten();
				int numFormulas = ac.getNoFormulas();
				for (int i = 0; i < numFormulas; i++) {
					Formula avgConjunct = ac.get(i);
					assert avgConjunct instanceof Atom || avgConjunct instanceof Negation;
					if (avgConjunct instanceof Atom) {
						includeLiteral((Atom)avgConjunct, 1.0 / (double) numFormulas, posLiteralsHS, posLiteralsWeightsHM);
					} else {
						assert ((Negation) avgConjunct).getFormula() instanceof Atom; //it must be a literal
						Atom a = (Atom ) ((Negation) avgConjunct).getFormula();
						includeLiteral((Atom)a, 1.0 / (double) numFormulas, negLiteralsHS, negLiteralsWeightsHM);
					}
				}			
			} else throw new IllegalStateException("head of average conjunction rule not of a recognized type.");
		}
		
		Atom[] exampleArray = new Atom[0];
		posLiterals = posLiteralsHS.toArray(exampleArray);
		negLiterals = negLiteralsHS.toArray(exampleArray);
		
		posLiteralsWeights = new Double[posLiterals.length];
		negLiteralsWeights = new Double[negLiterals.length];
		for (int i = 0; i < posLiterals.length; i++) {
			posLiteralsWeights[i] = posLiteralsWeightsHM.get(posLiterals[i]);
		}
		for (int i = 0; i < negLiterals.length; i++) {
			negLiteralsWeights[i] = negLiteralsWeightsHM.get(negLiterals[i]);
		}
	}
	
	private static void includeLiteral(Atom a, double weight, HashSet<Atom> literalsHS, HashMap<Atom, Double> literalsWeights) {		
		boolean addedNewLiteral = literalsHS.add(a);
		if (addedNewLiteral) 
			literalsWeights.put(a, weight);
		else 
			literalsWeights.put(a, literalsWeights.get(a) + weight);
	}
		
	public List<Atom> getPosLiterals() {
		return Arrays.asList(posLiterals);
	}
	
	public List<Atom> getNegLiterals() {
		return Arrays.asList(negLiterals);
	}
	
	public List<Double> getPosLiteralsWeights() {
		return Arrays.asList(posLiteralsWeights);
	}
	
	public List<Double> getNegLiteralsWeights() {
		return Arrays.asList(negLiteralsWeights);
	}

	
	@Override
	public Formula getDNF() {
		//Note that CompatibilityAveragingRuleKernel requires the negative and positive literals
		//in the DNF to be in the same order as getNegLiterals() and getPosLiterals().
		Formula[] dnfComponents = new Formula[negLiterals.length + posLiterals.length];
		int i = 0;
		for (; i < negLiterals.length; i++) {
			dnfComponents[i] = new Negation(negLiterals[i]);
		}
		int j = 0;
		for (; i < dnfComponents.length; i++) {
			dnfComponents[i] = posLiterals[j];
			j++;
		}
		return new Disjunction(dnfComponents);
	}
	
	@Override
	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		//TODO!
		//body.collectVariables(varMap);
		//head.collectVariables(varMap);
		return varMap;
	}
	
	@Override
	public Set<Atom> getAtoms(Set<Atom> atoms) {
		//body.getAtoms(atoms);
		//head.getAtoms(atoms);
		//TODO!
		return atoms;
		
	}	

	@Override
	public int hashCode() {
		//return new HashCodeBuilder().append(body).append(head).toHashCode();
		return ACRuleFormula.hashCode();
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		AvgConjRule of = (AvgConjRule)oth;
		return ACRuleFormula.equals(of.ACRuleFormula);
	}
	
	@Override
	public String toString() {
		//TODO print weighted DNF
		return ACRuleFormula.toString();
	}



}
