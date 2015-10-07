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
package edu.umd.cs.psl.groovy

import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.groovy.syntax.FormulaContainer
import edu.umd.cs.psl.groovy.syntax.GenericVariable
import edu.umd.cs.psl.model.Model
import edu.umd.cs.psl.model.argument.ArgumentType
import edu.umd.cs.psl.model.argument.DoubleAttribute
import edu.umd.cs.psl.model.argument.IntegerAttribute
import edu.umd.cs.psl.model.argument.StringAttribute
import edu.umd.cs.psl.model.argument.Term
import edu.umd.cs.psl.model.argument.Variable
import edu.umd.cs.psl.model.argument.VariableTypeMap
import edu.umd.cs.psl.model.atom.QueryAtom
import edu.umd.cs.psl.model.formula.AvgConjRule
import edu.umd.cs.psl.model.formula.Formula
import edu.umd.cs.psl.model.function.ExternalFunction
import edu.umd.cs.psl.model.kernel.Kernel
import edu.umd.cs.psl.model.kernel.predicateconstraint.DomainRangeConstraintKernel
import edu.umd.cs.psl.model.kernel.predicateconstraint.SymmetryConstraintKernel
import edu.umd.cs.psl.model.kernel.rule.AbstractRuleKernel
import edu.umd.cs.psl.model.kernel.rule.CompatibilityAveragingRuleKernel
import edu.umd.cs.psl.model.kernel.rule.CompatibilityRuleKernel
import edu.umd.cs.psl.model.kernel.rule.ConstraintRuleKernel
import edu.umd.cs.psl.model.kernel.setdefinition.SetDefinitionKernel
import edu.umd.cs.psl.model.predicate.FunctionalPredicate
import edu.umd.cs.psl.model.predicate.Predicate
import edu.umd.cs.psl.model.predicate.PredicateFactory
import edu.umd.cs.psl.model.predicate.StandardPredicate
import edu.umd.cs.psl.model.set.term.SetTerm

/**
 * Groovy class representing a PSL model.
 * 
 * @author Matthias Broecheler
 * @author Eric Norris <enorris@cs.umd.edu>
 */
class PSLModel extends Model {
	// Keys for Groovy syntactic sugar
	private static final String predicateKey = 'predicate';
	private static final String predicateArgsKey = 'types';
	private static final String functionKey = 'function';
	private static final String ruleKey = 'rule';
	private static final String setComparisonKey = 'setcomparison';
	private static final String avgConjRuleKey = 'avgConjRule';

	private static final String auxPredicateSeparator = '__';
	
	// Storage for set comparisons
	def Map setComparisons = [:];
	private int auxPredicateCounter = 0;
	
	// Local PredicateFactory
	private PredicateFactory pf = PredicateFactory.getFactory();
	private DataStore ds;
	
	
	// TODO: Documentation
	public PSLModel(Object context, DataStore ds) {
		this.ds = ds;
		context.metaClass.propertyMissing = { String name ->
			return lookupProperty(name);
		}
		context.metaClass.methodMissing = { String name, args ->
			return createFormulaContainer(name, args);
		}
	}
	
	/**
	 * - Looks up a Predicate with the given name, else
	 * - Allows for "inverse" and "inv" syntactic sugar, or
	 * - Creates a Variable with the given name
	 * @param name	name of the property used
	 * @return  	an Object corresponding to the name
	 */
	private Object lookupProperty(String name) {
		/* Hacky fix for broken println */
		if (name.equals("out"))
			return System.out;
		
		Predicate predicate = pf.getPredicate(name);
		
		if (predicate != null)
			return predicate;
		
		if (name == "inverse" || name == "inv")
			return 'inverse';
		
		if (name.charAt(0).isUpperCase())
			return new GenericVariable(name, this);
		
		throw new RuntimeException("Unknown property: " + name);
	}
	
	/**
	 * Creates a FormulaContainer using the predicate specified by name.
	 * @param name		the predicate to use for the Formula
	 * @return			a FormulaContainer 
	 */
	public Object createFormulaContainer(String name, Object[] args) {
		Predicate pred = pf.getPredicate(name);
		
		if (pred != null) {
			Term[] terms = new Term[args.size()];
			
			for (int i = 0; i < terms.length; i ++) {
				if (args[i] instanceof GenericVariable) {
					terms[i]=args[i].toAtomVariable();
				} else if ((args[i] instanceof Term)) {
					terms[i] = (Term)args[i];
				} else if (args[i] instanceof String) {
					terms[i] = new StringAttribute(args[i]);
				} else if (args[i] instanceof Double) {
					terms[i] = new DoubleAttribute(args[i]);
				} else if (args[i] instanceof Integer) {
					terms[i] = new IntegerAttribute(args[i]);
				} else 
					throw new IllegalArgumentException("The arguments to predicate ${name} must be terms");
			}
			
			return new FormulaContainer(new QueryAtom(pred, terms));
		} else if (setComparisons.containsKey(name)) {
			Map setcomp = setComparisons[name];
			if (args.size() != 2)
				throw new IllegalArgumentException("Expected 2 set definition for set comparison, but got: ${args}");
			if (!(args[0] instanceof Closure && args[1] instanceof Closure))
				throw new IllegalArgumentException("Expected set definitions for set comparison, but got: ${args}");
			
			SetTerm t1 = args[0].call().getSetTerm();
			SetTerm t2 = args[1].call().getSetTerm();
			
			VariableTypeMap vars = t2.getAnchorVariables(t1.getAnchorVariables(new VariableTypeMap()));
			
			ArgumentType[] types = new ArgumentType[vars.size()];
			Variable[] variables = new Variable[vars.size()];
			Term[] terms = new Term[vars.size()];
			String predname = name + auxPredicateSeparator + (++ auxPredicateCounter);
			
			/* Sorts Variables used to define sets to also use as arguments to new aux Predicate */
			List<Map.Entry<Variable, ArgumentType>> sortedVars = new ArrayList<Map.Entry<Variable, ArgumentType>>(vars.entrySet());
			Collections.sort(sortedVars, new Comparator<Map.Entry<Variable, ArgumentType>>() {
				public int compare(Map.Entry<Variable, ArgumentType> a, Map.Entry<Variable, ArgumentType> b) {
					return a.getKey().getName().compareTo(b.getKey().getName());
				}
			});
		
			for (int i = 0; i < sortedVars.size(); i++) {
				variables[i] = sortedVars.get(i).getKey();
				types[i] = sortedVars.get(i).getValue();
				terms[i] = sortedVars.get(i).getKey();
			}
			
			StandardPredicate auxpred = addAggregatePredicate(predname,types);
			
			addKernel(new SetDefinitionKernel(auxpred, t1, t2, variables, setcomp['predicate'], setcomp['aggregator']));
			return new FormulaContainer(new QueryAtom(auxpred, terms));	
		} else if (name == 'when') {
			return args[0];
		} else 
			throw new RuntimeException("Unknown method: " + name);
	}
	
	private StandardPredicate addAggregatePredicate(String name, ArgumentType[] types) {
		StandardPredicate p = pf.createStandardPredicate(name, types);
		ds.registerPredicate(p);
		return p;
	}
	
	/*
	 * Allows for syntactic sugar when creating rules, functions, and set comparisons.
	 */
	def add(Map args) {
		if (args.containsKey(predicateKey)) {
			String predicatename = args[predicateKey];
			args.remove predicateKey;
			return addPredicate(predicatename, args);
		} else if (args.containsKey(functionKey)) {
			String functionname = args[functionKey];
			args.remove functionKey;
			return addFunction(functionname, args);
		} else if (args.containsKey(setComparisonKey)) {
			if (!(args[setComparisonKey] instanceof String))
				throw new IllegalArgumentException("Expected a STRING as set comparison function name, but got: ${args[setComparisonKey]}");
			String setcompname = args[setComparisonKey];
			args.remove setComparisonKey;
			return addSetComparison(setcompname,args);
		} else if (args.containsKey(ruleKey)) {
			if (!(args[ruleKey] instanceof FormulaContainer))
				throw new IllegalArgumentException("Expected a formula, but got: ${args[ruleKey]}");
			FormulaContainer ruledef = args[ruleKey];
			args.remove ruleKey;
			return addRule(ruledef,args);
		} else if (args.containsKey(avgConjRuleKey)) {
			if (!(args[avgConjRuleKey] instanceof FormulaContainer))
				throw new IllegalArgumentException("Expected a formula, but got: ${args[ruleKey]}");
			FormulaContainer ruledef = args[avgConjRuleKey];
			args.remove avgConjRuleKey;
			return addAvgConjRule(ruledef,args);
		} else {
			throw new IllegalArgumentException("Unrecognized element added to model: ${args}");
		}
	}
	
	/*
	 * Handles adding PredicateConstraints
	 */
	def add(Map args, PredicateConstraint type) {
		addConstraint(type,args)
	}
	
	def addPredicate(String name, Map args) {
		if (args.containsKey(predicateArgsKey)) {
			ArgumentType[] predArgs;
			if (args[predicateArgsKey] instanceof List<?>) {
				predArgs = ((List<?>) args[predicateArgsKey]).toArray(new ArgumentType[1]);
			}
			else if (args[predicateArgsKey] instanceof ArgumentType) {
				predArgs = new ArgumentType[1];
				predArgs[0] = args[predicateArgsKey];
			}
			else
				throw new IllegalArgumentException("Must provide at least one ArgumentType. " +
					"Include multiple arguments as a list wrapped in [...].");
				
			StandardPredicate pred = pf.createStandardPredicate(name, predArgs);
			ds.registerPredicate(pred);
		}
		else
			throw new IllegalArgumentException("Must provide at least one ArgumentType. " +
				"Include multiple arguments as a list wrapped in [...].");
	}
	
	def addFunction(String name, Map args) {
		if (pf.getPredicate(name) != null)
			throw new IllegalArgumentException("A similarity function with the name [${name}] has already been defined.");
		
		ExternalFunction implementation = null;
		if (args.containsKey('implementation')) {
			if (args['implementation'] instanceof ExternalFunction)
				implementation = args['implementation'];
			else throw new IllegalArgumentException("The implementation of an external function must implement the ExternalFunction interface");
			args.remove 'implementation';
		}
		
		return addFunctionalPredicate(name, implementation);
	}
	
	public FunctionalPredicate addFunctionalPredicate(String name, ExternalFunction extFun) {
		return pf.createFunctionalPredicate(name, extFun);
	}
	
	def addSetComparison(String name, Map args) {
		if (setComparisons.containsKey(name))
			throw new IllegalArgumentException("Set comparison [${name}] has already been defined.");
		StandardPredicate predicate = getBasicPredicate(args,'on');
		if (!(args['using'] instanceof SetComparison))
			throw new IllegalArgumentException("Expected set comparison operator for [using] label, but got: ${args['using']}")
		
		setComparisons[name] = [:];
		setComparisons[name]['predicate'] = predicate;
		setComparisons[name]['aggregator'] = args['using'].getAggregator();
	}
	
	private StandardPredicate getBasicPredicate(Map args, String key) {
		Predicate predicate = args[key];
		if (predicate==null)
			throw new IllegalArgumentException("Need to define predicate via [${key}] argument label.");
		if (!(predicate instanceof StandardPredicate)) {
			throw new IllegalArgumentException("Expected basic predicate, but got: ${predicate}");
		}
		return predicate;
	}
	
	def addRule(FormulaContainer rule, Map args) {
		boolean isFact = false;
		boolean isSquared = true;
		if (args.containsKey('constraint')) {
			if (!(args['constraint'] instanceof Boolean)) throw new IllegalArgumentException("The parameter [constraint] for a rule must be either TRUE or FALSE");
			isFact = args['constraint'];
		}
		
		if (args.containsKey('squared')) {
			if (isFact) throw new IllegalArgumentException("Cannot set squared on a fact rule.");
			if (!(args['squared'] instanceof Boolean)) throw new IllegalArgumentException("The parameter [squared] for a rule must be either TRUE or FALSE");
			isSquared = args['squared'];
		}
		
		double weight = Double.NaN;
		if (args.containsKey('weight')) {
			if (isFact) throw new IllegalArgumentException("Cannot set a weight on a fact rule.");
			if (!isNumber(args['weight']))
				throw new IllegalArgumentException("The weight parameter is expected to be a real number: ${args['weight'].class}");
			weight = args['weight'];
		}
		
		if (!Double.isNaN(weight) && isFact)
			throw new IllegalArgumentException("A rule cannot be a constraint and have a weight.");

		Formula ruleformula = rule.getFormula();
		
		AbstractRuleKernel pslrule;
		if (isFact) {
			pslrule = new ConstraintRuleKernel(ruleformula);
		} else {
			pslrule = new CompatibilityRuleKernel(ruleformula, weight, isSquared);
		}
		
		addKernel(pslrule);
		return pslrule;
	}
	
	def addAvgConjRule(FormulaContainer rule, Map args) {
		boolean isSquared = true;
		if (args.containsKey('constraint')) {
			throw new IllegalArgumentException("Average conjunction rules cannot be constraints");
		}
		
		if (args.containsKey('squared')) {
			if (isFact) throw new IllegalArgumentException("Cannot set squared on a fact rule.");
			if (!(args['squared'] instanceof Boolean)) throw new IllegalArgumentException("The parameter [squared] for a rule must be either TRUE or FALSE");
			isSquared = args['squared'];
		}
		
		double weight = Double.NaN;
		if (args.containsKey('weight')) {
			if (!isNumber(args['weight']))
				throw new IllegalArgumentException("The weight parameter is expected to be a real number: ${args['weight'].class}");
			weight = args['weight'];
		}
		
		Formula ruleformula = rule.getFormula();
		
		Formula avgConjRuleFormula = new AvgConjRule(ruleformula);

		AbstractRuleKernel pslrule = new CompatibilityAveragingRuleKernel(avgConjRuleFormula, weight, isSquared);
		
		addKernel(pslrule);
		return pslrule;
	}
	
	private boolean isNumber(n) {
		return (n instanceof Double || n instanceof Integer || n instanceof BigDecimal);
	}
	
	def addConstraint(PredicateConstraint type, Map args) {
		StandardPredicate predicate = getBasicPredicate(args,'on');
		Kernel con;
		if (PredicateConstraint.Symmetric.equals(type)) {
			con = new SymmetryConstraintKernel(predicate);
		} else if (args.containsKey("valueMap")) {
			con = new DomainRangeConstraintKernel(predicate, type.getPSLConstraint(), args.get("valueMap"));
		}
		else {
			con = new DomainRangeConstraintKernel(predicate, type.getPSLConstraint());
		}
		addKernel(con);
		return con;
	}
	
	def getPredicate(String name) {
		return pf.getPredicate(name);
	}
}
