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
package edu.umd.cs.psl.parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.IntegerAttribute;
import edu.umd.cs.psl.model.argument.StringAttribute;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Disjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.formula.Rule;
import edu.umd.cs.psl.model.kernel.predicateconstraint.DomainRangeConstraintKernel;
import edu.umd.cs.psl.model.kernel.predicateconstraint.DomainRangeConstraintType;
import edu.umd.cs.psl.model.kernel.predicateconstraint.SymmetryConstraintKernel;
import edu.umd.cs.psl.model.kernel.rule.CompatibilityRuleKernel;
import edu.umd.cs.psl.model.kernel.rule.ConstraintRuleKernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.SpecialPredicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * This class operates both as a utility class to load PSL models as well as the 
 * visitor implementation for the ANTLR PSL model parser.
 * 
 * TODO: Implement set predicates and external functions (perhaps a standard library of built-in external functions)
 * 
 * @author Bert Huang <bert@cs.umd.edu>
 *
 */
public class PSLModelLoader extends PSLBaseVisitor<Formula> {

	private static final Logger log = LoggerFactory.getLogger(PSLModelLoader.class);
			
	private Model model;

	private PredicateFactory pf;

	private DataStore ds;
	
	private Map<String, Variable> varMap;

	/**
	 * 
	 * @param model the model object to load predicates, rules, and constraints from file into
	 * @param ds The DataStore being used with this model
	 */
	public PSLModelLoader(Model model, DataStore ds) {
		this.model = model;
		this.ds = ds;
		varMap = new HashMap<String, Variable>();
		pf = PredicateFactory.getFactory(); 
	}
	
	/**
	 * 
	 * @return The model
	 */
	public Model getModel() { return model; }

	@Override
	public Formula visitPredicateDefinition(PSLParser.PredicateDefinitionContext ctx) {

		String predicate = ctx.predicate().getText();

		ArgumentType[] arguments = new ArgumentType[ctx.argumentType().size()];

		// iterate over all arguments
		int i = 0;
		for (PSLParser.ArgumentTypeContext arg : ctx.argumentType()) {
			if (arg.getText().equals("UniqueID"))
				arguments[i] = ArgumentType.UniqueID;
			else if (arg.getText().equals("String"))
				arguments[i] = ArgumentType.String;
			else if (arg.getText().equals("Double"))
				arguments[i] = ArgumentType.Double;
			else if (arg.getText().equals("Integer"))
				arguments[i] = ArgumentType.Integer;
			else
				throw new UnsupportedOperationException("Unknown argument type " + arg.getText());
			i++;
		}

		// register the predicate with the PredicateFactory
		pf.createStandardPredicate(predicate, arguments);
		
		// register the predicate with the DataStore
		ds.registerPredicate((StandardPredicate) pf.getPredicate(predicate));

		log.debug("Created predicate " + pf.getPredicate(predicate));

		return null;
	}

	@Override 
	public Formula visitExpression(PSLParser.ExpressionContext ctx) {

		if (ctx.atom() != null) {
			// if the expression is an atom
			String predicate = ctx.atom().predicate().getText();

			Predicate p = pf.getPredicate(predicate);

			Term[] arguments = new Term[ctx.atom().argument().size()];

			// iterate over all arguments
			int i = 0;
			for (PSLParser.ArgumentContext arg : ctx.atom().argument()) {
				if (arg.variable() != null) {
					arguments[i] = getVariable(arg.getText());
				} else {
					// argument is a constant
					PSLParser.ConstantContext constant = arg.constant();
					if (p.getArgumentType(i) == ArgumentType.UniqueID) {
						arguments[i] = ds.getUniqueID(constant.getText());
					} else if (constant.strConstant() != null) {
						String rawString = constant.getText();
						arguments[i] = new StringAttribute(rawString.substring(1, rawString.length()-1));
					} else {
						arguments[i] = new IntegerAttribute(Integer.parseInt(constant.getText()));
					}

				}
				i++;
			}
			
			return new QueryAtom(p, arguments);

		} else if (ctx.AND() != null) {
			return new Conjunction(visit(ctx.expression(0)), visit(ctx.expression(1)));
		} else if (ctx.OR() != null) {
			return new Disjunction(visit(ctx.expression(0)), visit(ctx.expression(1)));
		} else if (ctx.THEN() != null) {
			return new Rule(visit(ctx.expression(0)), visit(ctx.expression(1)));
		} else if (ctx.IMPLIEDBY() != null) {
			return new Rule(visit(ctx.expression(1)), visit(ctx.expression(0)));
		} else if (ctx.NOT() != null) {
			return new Negation(visit(ctx.expression(0)));
		} else if (ctx.SYMMETRIC() != null) {
			return new QueryAtom(SpecialPredicate.NonSymmetric, 
					getVariable(ctx.argument(0).getText()), getVariable(ctx.argument(1).getText()));
		} else if (ctx.NOTEQUAL() != null) {
			return new QueryAtom(SpecialPredicate.NotEqual, 
					getVariable(ctx.argument(0).getText()), getVariable(ctx.argument(1).getText()));
		}
			
		return visit(ctx.expression(0));
	}

	/**
	 * Gets the variable from our local variable map
	 * @param name String key for variable
	 * @return Variable object with given name. If the Variable was not previously registered, this method creates it
	 */
	private Variable getVariable(String name) {
		Variable var = varMap.get(name);
		if (var != null)
			return var;
		
		var = new Variable(name);
		varMap.put(name, var);
		return var;
	}

	@Override 
	public Formula visitKernel(PSLParser.KernelContext ctx) { 
		PSLParser.WeightContext weight = ctx.weight();

		Formula f = visit(ctx.expression());
		
		if (weight.CONSTRAINT() == null) {
			// create compatibility kernel
			boolean squared = (ctx.SQUARED() != null);
			double w = Double.parseDouble(ctx.weight().NUMBER().getText());
			CompatibilityRuleKernel kernel = new CompatibilityRuleKernel(f, w, squared);
			model.addKernel(kernel);
		} else {
			// create constraint kernel
			ConstraintRuleKernel kernel = new ConstraintRuleKernel(f);
			model.addKernel(kernel);				
		}

		return null;
	}
	
	@Override
	public Formula visitConstraint(PSLParser.ConstraintContext ctx) {
		StandardPredicate p = (StandardPredicate) pf.getPredicate(ctx.predicate().getText());
		
		DomainRangeConstraintType type = null;
		if (ctx.constraintType().getText().equals("Functional"))
			type = DomainRangeConstraintType.Functional;
		else if (ctx.constraintType().getText().equals("InverseFunctional"))
			type = DomainRangeConstraintType.InverseFunctional;
		else if (ctx.constraintType().getText().equals("PartialFunctional"))
			type = DomainRangeConstraintType.PartialFunctional;
		else if (ctx.constraintType().getText().equals("PartialInverseFunctional"))
			type = DomainRangeConstraintType.PartialInverseFunctional;
		else if (ctx.constraintType().getText().equals("Symmetric"))
			model.addKernel(new SymmetryConstraintKernel(p));
		
		model.addKernel(new DomainRangeConstraintKernel(p, type));

		return null;
	}

	/**
	 * loads a model from the file system
	 * @param filename 
	 * @param ds DataStore to associate with the model
	 * @return
	 */
	public static Model loadModel(String filename, DataStore ds) {
		PSLLexer lexer;
		Model model = new Model();
		try {
			lexer = new PSLLexer(new ANTLRFileStream(filename));
			CommonTokenStream tokens = new CommonTokenStream(lexer); 
			PSLParser parser = new PSLParser(tokens); 
			PSLParser.ProgramContext program = parser.program(); 
			
			PSLModelLoader loader = new PSLModelLoader(model, ds);

			// start parsing
			loader.visit(program);
			
			log.debug(loader.getModel().toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return model;
	}

	/**
	 * Print a Model in the proper format for reading
	 * @param filename
	 * @param model
	 */
	public static void outputModel(String filename, Model model) {
		try {
			File file = new File(filename);
			if (file.getParentFile() != null)
				file.getParentFile().mkdirs();
			FileWriter fw = new FileWriter(file);
			BufferedWriter bw = new BufferedWriter(fw);
			for (Predicate predicate : PredicateFactory.getFactory().getPredicates())
				bw.write(predicate.toString() + "\n");
			bw.write(model.toString());

			bw.close();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
