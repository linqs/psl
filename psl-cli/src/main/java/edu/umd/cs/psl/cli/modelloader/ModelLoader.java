/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
import java.util.LinkedList;
import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;

import edu.umd.cs.psl.cli.modelloader.PSLParser.AtomContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Disjunctive_clauseContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.LiteralContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Logical_rule_expressionContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.PredicateContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.ProgramContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Psl_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.TermContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Unweighted_logical_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Weighted_logical_ruleContext;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.formula.Disjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.kernel.rule.AbstractRuleKernel;
import edu.umd.cs.psl.model.kernel.rule.CompatibilityRuleKernel;
import edu.umd.cs.psl.model.kernel.rule.ConstraintRuleKernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;

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
			Kernel k = visitPsl_rule(psl_rule);
			model.addKernel(k);
		}
		return model;
	}
	
	@Override
	public Kernel visitPsl_rule(Psl_ruleContext ctx) {
		if (ctx.logical_rule() != null) {
			return visitLogical_rule(ctx.logical_rule());
		}
		else if (ctx.arithmetic_rule() != null) {
			System.out.println("Arithmetic rules are not supported!");
			throw new IllegalStateException();
		}
		else {
			throw new IllegalStateException();
		}
	}

	@Override
	public AbstractRuleKernel visitLogical_rule(PSLParser.Logical_ruleContext ctx) {
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
	public CompatibilityRuleKernel visitWeighted_logical_rule(Weighted_logical_ruleContext ctx) {
		System.out.println("Visiting weighted logical rule: " + ctx.getText());
		return null;
	}
	
	@Override
	public ConstraintRuleKernel visitUnweighted_logical_rule(Unweighted_logical_ruleContext ctx) {
		Formula f = visitLogical_rule_expression(ctx.logical_rule_expression());
		return new ConstraintRuleKernel(f);
	}
	
	@Override
	public Formula visitLogical_rule_expression(Logical_rule_expressionContext ctx) {
		if (ctx.children.size() == 3) {
			throw new IllegalStateException();
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
	public Disjunction visitDisjunctive_clause(Disjunctive_clauseContext ctx) {
		Formula[] literals = new Formula[ctx.literal().size()];
		for (int i = 0; i < literals.length; i++) {
			literals[i] = visitLiteral(ctx.literal(i));
		}
		return new Disjunction(literals);
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
		Predicate p = visitPredicate(ctx.predicate());
		Term[] args = new Term[ctx.term().size()];
		for (int i = 0; i < args.length; i++) {
			args[i] = visitTerm(ctx.term(i));
		}
		return new QueryAtom(p, args);
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
		return null;
	}
}
