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
package edu.umd.cs.psl.cli.modelloader;

import java.io.IOException;
import java.io.InputStream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;

import edu.umd.cs.psl.cli.modelloader.PSLParser.AtomContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Conjunctive_clauseContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.ConstantContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Disjunctive_clauseContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.LiteralContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Logical_rule_expressionContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.PredicateContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.ProgramContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Psl_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.TermContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Unweighted_logical_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.VariableContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Weight_expressionContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Weighted_logical_ruleContext;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Disjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.formula.Implication;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.SpecialPredicate;
import edu.umd.cs.psl.model.rule.Rule;
import edu.umd.cs.psl.model.rule.logical.AbstractLogicalRule;
import edu.umd.cs.psl.model.rule.logical.WeightedLogicalRule;
import edu.umd.cs.psl.model.rule.logical.UnweightedLogicalRule;

public class ModelLoader extends PSLBaseVisitor<Object> {

	static public Model load(DataStore data, InputStream input) throws IOException  {
		PSLLexer lexer = new PSLLexer(new ANTLRInputStream(input));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		PSLParser parser = new PSLParser(tokens);
		ProgramContext program = parser.program();
		ModelLoader visitor = new ModelLoader(data);
		return visitor.visitProgram(program);
	}
	
	protected final DataStore data;

	public ModelLoader(DataStore data) {
		this.data = data;
	}
	
	@Override
	public Model visitProgram(ProgramContext ctx) {
		Model model = new Model();
		for (Psl_ruleContext psl_rule : ctx.psl_rule()) {
			Rule k = visitPsl_rule(psl_rule);
			model.addKernel(k);
		}
		return model;
	}
	
	@Override
	public Rule visitPsl_rule(Psl_ruleContext ctx) {
		if (ctx.logical_rule() != null) {
			return visitLogical_rule(ctx.logical_rule());
		}
		else if (ctx.arithmetic_rule() != null) {
			throw new IllegalStateException("(Line " + ctx.getStart().getLine()+ ") Arithmetic rules are not supported!");
		}
		else {
			throw new IllegalStateException("(Line " + ctx.getStart().getLine()+ ") Rule not recognized as logical or arithmetic.");
		}
	}

	@Override
	public AbstractLogicalRule visitLogical_rule(PSLParser.Logical_ruleContext ctx) {
		if (ctx.weighted_logical_rule() != null) {
			return visitWeighted_logical_rule(ctx.weighted_logical_rule());
		}
		else if (ctx.unweighted_logical_rule() != null) {
			return visitUnweighted_logical_rule(ctx.unweighted_logical_rule());
		}
		else {
			throw new IllegalStateException();
		}
	}
	
	@Override
	public WeightedLogicalRule visitWeighted_logical_rule(Weighted_logical_ruleContext ctx) {
		Double w = visitWeight_expression(ctx.weight_expression());
		Formula f = visitLogical_rule_expression(ctx.logical_rule_expression());
		Boolean sq = false;
		if (ctx.EXPONENT_EXPRESSION() != null) {
			sq = ctx.EXPONENT_EXPRESSION().getText().equals("^2");
		}
		
		return new WeightedLogicalRule(f, w, sq);
	}
	
	@Override
	public Double visitWeight_expression(Weight_expressionContext ctx) {
		return Double.parseDouble(ctx.NONNEGATIVE_NUMBER().getText());
	}
	
	@Override
	public UnweightedLogicalRule visitUnweighted_logical_rule(Unweighted_logical_ruleContext ctx) {
		Formula f = visitLogical_rule_expression(ctx.logical_rule_expression());
		return new UnweightedLogicalRule(f);
	}
	
	@Override
	public Formula visitLogical_rule_expression(Logical_rule_expressionContext ctx) {
		if (ctx.children.size() == 3) {
			if (ctx.conjunctive_clause() != null & ctx.disjunctive_clause() != null) {
				Formula body = visitConjunctive_clause(ctx.conjunctive_clause());
				Formula head = visitDisjunctive_clause(ctx.disjunctive_clause());
				return new Implication(body, head);
			}
			else {
				throw new IllegalStateException();
			}
		}
		else if (ctx.children.size() == 1) {
			if (ctx.disjunctive_clause() != null) {
				return visitDisjunctive_clause(ctx.disjunctive_clause());
			}
			else {
				throw new IllegalStateException();
			}
		}
		else {
			throw new IllegalStateException();
		}
	}
	
	@Override
	public Formula visitConjunctive_clause(Conjunctive_clauseContext ctx) {
		Formula[] literals = new Formula[ctx.literal().size()];
		for (int i = 0; i < literals.length; i++) {
			literals[i] = visitLiteral(ctx.literal(i));
		}
		if (literals.length == 1) {
			return literals[0];
		}
		else {
			return new Conjunction(literals);
		}
	}
	
	@Override
	public Formula visitDisjunctive_clause(Disjunctive_clauseContext ctx) {
		Formula[] literals = new Formula[ctx.literal().size()];
		for (int i = 0; i < literals.length; i++) {
			literals[i] = visitLiteral(ctx.literal(i));
		}
		if (literals.length == 1) {
			return literals[0];
		}
		else {
			return new Disjunction(literals);
		}
	}
	
	@Override
	public Formula visitLiteral(LiteralContext ctx) {
		if (ctx.children.size() == 2) {
			return new Negation(visitLiteral(ctx.literal()));
		}
		else if (ctx.atom() != null) {
			return visitAtom(ctx.atom());
		}
		else {
			throw new IllegalStateException();
		}
	}
	
	@Override
	public Atom visitAtom(AtomContext ctx) {
		if (ctx.predicate() != null) {
			Predicate p = visitPredicate(ctx.predicate());
			Term[] args = new Term[ctx.term().size()];
			for (int i = 0; i < args.length; i++) {
				args[i] = visitTerm(ctx.term(i));
			}
			return new QueryAtom(p, args);
		}
		else if (ctx.TERM_OPERATOR() != null) {
			// TODO: There might be a better way to look up which operator it is
			SpecialPredicate p;
			if (("'" + ctx.TERM_OPERATOR().getText() + "'").equals(PSLParser.VOCABULARY.getLiteralName(PSLParser.NOT_EQUAL))) {
				p = SpecialPredicate.NotEqual;
			}
			else if (("'" + ctx.TERM_OPERATOR().getText() + "'").equals(PSLParser.VOCABULARY.getLiteralName(PSLParser.TERM_EQUAL))) {
				p = SpecialPredicate.Equal;
			}
			else {
				throw new IllegalStateException();
			}
			return new QueryAtom(p, visitTerm(ctx.term(0)), visitTerm(ctx.term(1)));
		}
		else {
			throw new IllegalStateException();
		}
	}
	
	@Override
	public Predicate visitPredicate(PredicateContext ctx) {
		PredicateFactory pf = PredicateFactory.getFactory();
		Predicate p = pf.getPredicate(ctx.IDENTIFIER().getText());
		if (p != null) {
			return p;
		}
		else {
			throw new IllegalStateException("Undefined predicate " + ctx.IDENTIFIER().getText());
		}
	}
	
	@Override
	public Term visitTerm(TermContext ctx) {
		if (ctx.variable() != null) {
			return visitVariable(ctx.variable());
		}
		else if (ctx.constant() != null) {
			return visitConstant(ctx.constant());
		}
		else {
			throw new IllegalStateException();
		}
	}
	
	@Override
	public Variable visitVariable(VariableContext ctx) {
		return new Variable(ctx.IDENTIFIER().getText());
	}
	
	@Override
	public UniqueID visitConstant(ConstantContext ctx) {
		return data.getUniqueID(ctx.IDENTIFIER().getText());
	}
}
