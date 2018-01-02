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
package org.linqs.psl.groovy;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.groovy.syntax.FormulaContainer;
import org.linqs.psl.groovy.syntax.GenericVariable;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.model.rule.logical.UnweightedLogicalRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.parser.RulePartial;

/**
 * Groovy class representing a PSL model.
 *
 * @author Matthias Broecheler
 * @author Eric Norris <enorris@cs.umd.edu>
 */
public class PSLModel extends Model {
	// Keys for Groovy syntactic sugar
	private static final String predicateKey = 'predicate';
	private static final String predicateArgsKey = 'types';
	private static final String functionKey = 'function';
	private static final String ruleKey = 'rule';

	private static final String auxPredicateSeparator = '__';

	// Storage for set comparisons
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
		} else if (name == 'when') {
			return args[0];
		} else
			throw new RuntimeException("Unknown method: " + name);
	}

	/*
	 * Allows for syntactic sugar when creating rules, functions, and set comparisons.
	 */
	public Object add(Map args) {
		if (args.containsKey(predicateKey)) {
			String predicatename = args[predicateKey];
			args.remove predicateKey;
			return addPredicate(predicatename, args);
		} else if (args.containsKey(functionKey)) {
			String functionname = args[functionKey];
			args.remove functionKey;
			return addFunction(functionname, args);
		} else if (args.containsKey(ruleKey)) {
			if (args[ruleKey] instanceof FormulaContainer) {
				FormulaContainer ruledef = args[ruleKey];
				args.remove ruleKey;
				return addRule(ruledef, args);
			}

			if (args[ruleKey] instanceof String) {
				String ruledef = args[ruleKey];
				args.remove ruleKey;
				return addRule(ruledef, args);
			}

			throw new IllegalArgumentException("Expected a formula or string, but got: ${args[ruleKey]}");
		} else {
			throw new IllegalArgumentException("Unrecognized element added to model: ${args}");
		}
	}

	private Predicate addPredicate(String name, Map args) {
		if (args.containsKey(predicateArgsKey)) {
			ConstantType[] predArgs;
			if (args[predicateArgsKey] instanceof List<?>) {
				predArgs = ((List<?>) args[predicateArgsKey]).toArray(new ConstantType[1]);
			} else if (args[predicateArgsKey] instanceof ConstantType) {
				predArgs = new ConstantType[1];
				predArgs[0] = args[predicateArgsKey];
			} else {
				throw new IllegalArgumentException("Must provide at least one ConstantType. " +
					"Include multiple arguments as a list wrapped in [...].");
			}

			StandardPredicate pred = pf.createStandardPredicate(name, predArgs);
			ds.registerPredicate(pred);
			return pred;
		} else {
			throw new IllegalArgumentException("Must provide at least one ConstantType. " +
				"Include multiple arguments as a list wrapped in [...].");
		}
	}

	private FunctionalPredicate addFunction(String name, Map args) {
		if (pf.getPredicate(name) != null) {
			throw new IllegalArgumentException("A similarity function with the name [${name}] has already been defined.");
		}

		ExternalFunction implementation = null;
		if (args.containsKey('implementation')) {
			if (args['implementation'] instanceof ExternalFunction) {
				implementation = args['implementation'];
			} else {
				throw new IllegalArgumentException("The implementation of an external function must implement the ExternalFunction interface");
			}
			args.remove 'implementation';
		}

		return pf.createExternalFunctionalPredicate(name, implementation);
	}

	private StandardPredicate getBasicPredicate(Map args, String key) {
		Predicate predicate = args[key];
		if (predicate == null) {
			throw new IllegalArgumentException("Need to define predicate via [${key}] argument label.");
		}

		if (!(predicate instanceof StandardPredicate)) {
			throw new IllegalArgumentException("Expected basic predicate, but got: ${predicate}");
		}

		return predicate;
	}

	/**
	 * Alternative interface to addRules().
	 */
	public void addRules(String rules) {
		addRules(new StringReader(rules));
	}

	/**
	 * Add all the rules from a reader.
	 * Rules must be fully specified in their string form.
	 */
	public void addRules(Reader rules) {
		Model model = ModelLoader.load(ds, rules);
		for (Rule rule : model.getRules()) {
			addRule(rule);
		}
	}

	private Rule addRule(String stringRule, Map args) {
		RulePartial partial = ModelLoader.loadRulePartial(ds, stringRule);
		Rule rule;

		Double weight = null;
		Boolean squared = null;

		if (args.containsKey("weight") && isNumber(args.get('weight'))) {
			weight = args.get("weight").doubleValue();
		}

		if (args.containsKey("squared") && args.get('squared') instanceof Boolean) {
			squared = args.get("squared").booleanValue();
		}

		if (partial.isRule()) {
			if (weight != null || squared != null) {
				throw new IllegalArgumentException("Rules with weight/squared specified in the string cannot accept any arguments.");
			}

			rule = partial.toRule();
		} else {
			if (weight == null && squared == null) {
				throw new IllegalArgumentException("Rules without weight/squared specified in the string must accept them as arguments.");
			}

			rule = partial.toRule(weight, squared);
		}

		addRule(rule);
		return rule;
	}

	private Rule addRule(FormulaContainer rule, Map args) {
		boolean isFact = false;
		boolean isSquared = true;

		if (args.containsKey('constraint')) {
			if (!(args['constraint'] instanceof Boolean)) throw new IllegalArgumentException("The parameter [constraint] for a rule must be either TRUE or FALSE");
			isFact = args['constraint'];
		}

		if (args.containsKey('squared')) {
			if (isFact) {
				throw new IllegalArgumentException("Cannot set squared on a fact rule.");
			}

			if (!(args['squared'] instanceof Boolean)) {
				throw new IllegalArgumentException("The parameter [squared] for a rule must be either TRUE or FALSE");
			}

			isSquared = args['squared'];
		}

		double weight = Double.NaN;
		if (args.containsKey('weight')) {
			if (isFact) {
				throw new IllegalArgumentException("Cannot set a weight on a fact rule.");
			}

			if (!isNumber(args['weight'])) {
				throw new IllegalArgumentException("The weight parameter is expected to be a real number: ${args['weight'].class}");
			}

			weight = args['weight'];
		}

		if (!Double.isNaN(weight) && isFact) {
			throw new IllegalArgumentException("A rule cannot be a constraint and have a weight.");
		}

		Formula ruleformula = rule.getFormula();

		AbstractLogicalRule pslrule;
		if (isFact) {
			pslrule = new UnweightedLogicalRule(ruleformula);
		} else {
			pslrule = new WeightedLogicalRule(ruleformula, weight, isSquared);
		}

		addRule(pslrule);
		return pslrule;
	}

	private boolean isNumber(n) {
		return (n instanceof Double || n instanceof Integer || n instanceof BigDecimal);
	}

	public Predicate getPredicate(String name) {
		return pf.getPredicate(name);
	}
}
