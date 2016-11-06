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

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Disjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.Implication;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.SpecialPredicate;
import edu.umd.cs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Add;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Cardinality;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Divide;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Max;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Min;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Multiply;
import edu.umd.cs.psl.model.rule.arithmetic.expression.coefficient.Subtract;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationAtom;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariable;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import edu.umd.cs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import edu.umd.cs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import edu.umd.cs.psl.model.rule.logical.UnweightedLogicalRule;
import edu.umd.cs.psl.model.rule.logical.WeightedLogicalRule;
import edu.umd.cs.psl.model.rule.Rule;
import edu.umd.cs.psl.model.term.Term;
import edu.umd.cs.psl.model.term.UniqueID;
import edu.umd.cs.psl.model.term.Variable;
import edu.umd.cs.psl.parser.antlr.PSLBaseVisitor;
import edu.umd.cs.psl.parser.antlr.PSLLexer;
import edu.umd.cs.psl.parser.antlr.PSLParser;
import edu.umd.cs.psl.parser.antlr.PSLParser.ArithmeticRuleExpressionContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.ArithmeticRuleOperandContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.ArithmeticRuleRelationContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.AtomContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.BoolExpressionContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.CoefficientContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.ConjunctiveClauseContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.ConstantContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.DisjunctiveClauseContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.LinearOperatorContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.LiteralContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.LogicalRuleExpressionContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.NumberContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.PredicateContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.ProgramContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.PslRuleContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.SelectStatementContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.SummationAtomContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.SummationVariableContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.TermContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.UnweightedArithmeticRuleContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.UnweightedLogicalRuleContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.VariableContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.WeightedArithmeticRuleContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.WeightedLogicalRuleContext;
import edu.umd.cs.psl.parser.antlr.PSLParser.WeightExpressionContext;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.Reader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ModelLoader extends PSLBaseVisitor<Object> {
	/**
	 * Parse and return a single rule.
	 * If exactly one rule is not specified, an exception is thrown.
	 */
	public static Rule loadRule(DataStore data, String input) throws IOException {
		Model model = load(data, new StringReader(input));

		int ruleCount = 0;
		Rule targetRule = null;

		for (Rule rule : model.getRules()) {
			if (ruleCount == 0) {
				targetRule = rule;
			}
			ruleCount++;
		}

		if (ruleCount != 1) {
			throw new IllegalArgumentException(String.format("Expected 1 rule, found %d.", ruleCount));
		}

		return targetRule;
	}

	/**
	 * Convenience interface to load().
	 */
	public static Model load(DataStore data, String input) throws IOException {
		return load(data, new StringReader(input));
	}

	/**
	 * Parse and return a Model (collection of rules).
	 * The input should only contain rules and the DataStore should contain all the predicates
	 * used by the rules.
	 */
	public static Model load(DataStore data, Reader input) throws IOException  {
		PSLLexer lexer = new PSLLexer(new ANTLRInputStream(input));

		// We need to add a error listener to the lexer so we halt on lex errors.
		lexer.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(
					Recognizer<?, ?> recognizer,
					Object offendingSymbol,
					int line,
					int charPositionInLine,
					String msg,
					RecognitionException ex) throws ParseCancellationException {
				throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg, ex);
			}
		});
		CommonTokenStream tokens = new CommonTokenStream(lexer);

		PSLParser parser = new PSLParser(tokens);
		parser.setErrorHandler(new BailErrorStrategy());
		ProgramContext program = null;

		try {
			program = parser.program();
		} catch (ParseCancellationException ex) {
			// Cancel the parse and rethrow the cause.
			throw (RuntimeException)ex.getCause();
		}

		ModelLoader visitor = new ModelLoader(data);
		return visitor.visitProgram(program);
	}

	private final DataStore data;

	public ModelLoader(DataStore data) {
		this.data = data;
	}

	@Override
	public Model visitProgram(ProgramContext ctx) {
		Model model = new Model();
		for (PslRuleContext ruleCtx : ctx.pslRule()) {
			model.addRule((Rule) visit(ruleCtx));
		}
		return model;
	}

	@Override
	public WeightedLogicalRule visitWeightedLogicalRule(WeightedLogicalRuleContext ctx) {
		Double w = visitWeightExpression(ctx.weightExpression());
		Formula f = visitLogicalRuleExpression(ctx.logicalRuleExpression());
		Boolean sq = false;
		if (ctx.EXPONENT_EXPRESSION() != null) {
			sq = ctx.EXPONENT_EXPRESSION().getText().equals("^2");
		}

		return new WeightedLogicalRule(f, w, sq);
	}

	@Override
	public UnweightedLogicalRule visitUnweightedLogicalRule(UnweightedLogicalRuleContext ctx) {
		Formula f = visitLogicalRuleExpression(ctx.logicalRuleExpression());
		return new UnweightedLogicalRule(f);
	}

	@Override
	public Formula visitLogicalRuleExpression(LogicalRuleExpressionContext ctx) {
		if (ctx.children.size() == 3) {
			if (ctx.conjunctiveClause() != null & ctx.disjunctiveClause() != null) {
				Formula body = visitConjunctiveClause(ctx.conjunctiveClause());
				Formula head = visitDisjunctiveClause(ctx.disjunctiveClause());
				return new Implication(body, head);
			}
			else {
				throw new IllegalStateException();
			}
		}
		else if (ctx.children.size() == 1) {
			if (ctx.disjunctiveClause() != null) {
				return visitDisjunctiveClause(ctx.disjunctiveClause());
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
	public Formula visitConjunctiveClause(ConjunctiveClauseContext ctx) {
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
	public Formula visitDisjunctiveClause(DisjunctiveClauseContext ctx) {
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
	public WeightedArithmeticRule visitWeightedArithmeticRule(WeightedArithmeticRuleContext ctx) {
		Double w = visitWeightExpression(ctx.weightExpression());
		ArithmeticRuleExpression expression = (ArithmeticRuleExpression) visitArithmeticRuleExpression(ctx.arithmeticRuleExpression());
		Map<SummationVariable, Formula> selectStatements = new HashMap<SummationVariable, Formula>();
		for (int i = 0; i < ctx.selectStatement().size(); i++) {
			SelectStatement ss = visitSelectStatement(ctx.selectStatement(i));
			selectStatements.put(ss.v, ss.f);
		}
		Boolean sq = false;
		if (ctx.EXPONENT_EXPRESSION() != null) {
			sq = ctx.EXPONENT_EXPRESSION().getText().equals("^2");
		}
		return new WeightedArithmeticRule(expression, selectStatements, w, sq);
	}

	@Override
	public UnweightedArithmeticRule visitUnweightedArithmeticRule(UnweightedArithmeticRuleContext ctx) {
		ArithmeticRuleExpression expression = (ArithmeticRuleExpression) visitArithmeticRuleExpression(ctx.arithmeticRuleExpression());
		Map<SummationVariable, Formula> selectStatements = new HashMap<SummationVariable, Formula>();
		for (int i = 0; i < ctx.selectStatement().size(); i++) {
			SelectStatement ss = visitSelectStatement(ctx.selectStatement(i));
			selectStatements.put(ss.v, ss.f);
		}
		return new UnweightedArithmeticRule(expression, selectStatements);
	}

	@Override
	public ArithmeticRuleExpression visitArithmeticRuleExpression(ArithmeticRuleExpressionContext ctx) {
		List<ArithmeticRuleOperand> ops1, ops2, currentOps;
		ops1 = new LinkedList<ArithmeticRuleOperand>();
		ops2 = new LinkedList<ArithmeticRuleOperand>();
		currentOps = ops1;

		List<Boolean> pos1, pos2, currentPos;
		pos1 = new LinkedList<Boolean>();
		pos1.add(true);
		pos2 = new LinkedList<Boolean>();
		pos2.add(true);
		currentPos = pos1;

		FunctionComparator comp = null;

		for (int i = 0; i < ctx.getChildCount(); i++) {
			if (i % 2 == 0) {
				currentOps.add((ArithmeticRuleOperand) visit(ctx.getChild(i)));
			}
			else {
				if (ctx.getChild(i).getPayload() instanceof ArithmeticRuleRelationContext) {
					comp = (FunctionComparator) visit(ctx.getChild(i));
					currentOps = ops2;
					currentPos = pos2;
				}
				else {
					currentPos.add((Boolean) visit(ctx.getChild(i)));
				}
			}
		}

		List<Coefficient> coeffs = new LinkedList<Coefficient>();
		List<SummationAtomOrAtom> atoms = new LinkedList<SummationAtomOrAtom>();
		Coefficient finalCoeff = null;

		/*
		 * Processes first subexpression
		 */
		for (int i = 0; i < ops1.size(); i++) {
			// Checks if it is just a coefficient
			if (ops1.get(i).atom == null) {
				// If it is the first coefficient, uses it to start the final coefficient
				if (finalCoeff == null) {
					if (pos1.get(i)) {
						finalCoeff = new Multiply(new ConstantNumber(-1.0), ops1.get(i).coefficient);
					}
					else {
						finalCoeff = ops1.get(i).coefficient;
					}
				}
				// Else, adds it to the final coefficient
				else {
					if (pos1.get(i)) {
						finalCoeff = new Subtract(finalCoeff, ops1.get(i).coefficient);
					}
					else {
						finalCoeff = new Add(finalCoeff, ops1.get(i).coefficient);
					}
				}
			}
			// Else, processes the SummationAtomOrAtom
			else {
				if (ops1.get(i).coefficient == null) {
					coeffs.add(new ConstantNumber(1.0));
				}
				else {
					coeffs.add(ops1.get(i).coefficient);
				}
				atoms.add(ops1.get(i).atom);
			}
		}

		/*
		 * Processes second subexpression
		 */
		for (int i = 0; i < ops2.size(); i++) {
			// Checks if it is just a coefficient
			if (ops2.get(i).atom == null) {
				// If it is the first coefficient, uses it to start the final coefficient
				if (finalCoeff == null) {
					finalCoeff = ops2.get(i).coefficient;
				}
				// Else, adds it to the final coefficient
				else {
					if (pos2.get(i)) {
						finalCoeff = new Add(finalCoeff, ops2.get(i).coefficient);
					}
					else {
						finalCoeff = new Subtract(finalCoeff, ops2.get(i).coefficient);
					}
				}
			}
			// Else, processes the SummationAtomOrAtom
			else {
				if (ops2.get(i).coefficient == null) {
					coeffs.add(new ConstantNumber(-1.0));
				}
				else {
					coeffs.add(new Multiply(new ConstantNumber(-1.0), ops2.get(i).coefficient));
				}
				atoms.add(ops2.get(i).atom);
			}
		}

		return new ArithmeticRuleExpression(coeffs, atoms, comp, finalCoeff);
	}

	private static class ArithmeticRuleOperand {
		SummationAtomOrAtom atom;
		Coefficient coefficient;

		private ArithmeticRuleOperand() {
			atom = null;
			coefficient = null;
		}
	}

	@Override
	public ArithmeticRuleOperand visitArithmeticRuleOperand(ArithmeticRuleOperandContext ctx) {
		ArithmeticRuleOperand operand = new ArithmeticRuleOperand();
		// Checks if the operand is just a coefficient
		if (ctx.getChildCount() == 1 && ctx.getChild(0).getPayload() instanceof CoefficientContext) {
			operand.coefficient = visitCoefficient((CoefficientContext) ctx.getChild(0));
		}
		else {
			// Checks if there is a prepended multiplier coefficient
			int atomIndex = 0;
			if (ctx.getChild(0).getPayload() instanceof CoefficientContext) {
				operand.coefficient = visitCoefficient((CoefficientContext) ctx.getChild(0).getPayload());
				atomIndex = (ctx.getChild(1).getPayload() instanceof CommonToken) ? 2 : 1;
			}

			// Parses SummationAtom or Atom
			operand.atom = (SummationAtomOrAtom) visit(ctx.getChild(atomIndex));

			// Checks if there is an appended divisor coefficient
			if (ctx.getChildCount() > atomIndex + 1) {
				Coefficient divisor = visitCoefficient((CoefficientContext) ctx.getChild(atomIndex + 2));
				if (operand.coefficient == null) {
					operand.coefficient = new Divide(new ConstantNumber(1.0), divisor);
				}
				else {
					operand.coefficient = new Divide(operand.coefficient, divisor);
				}
			}
		}
		return operand;
	}

	@Override
	public SummationAtom visitSummationAtom(SummationAtomContext ctx) {
		Predicate p = visitPredicate(ctx.predicate());
		SummationVariableOrTerm[] args = new SummationVariableOrTerm[ctx.getChildCount() / 2 - 1];
		for (int i = 1; i < ctx.getChildCount() / 2; i++) {
			if (ctx.getChild(i*2).getPayload() instanceof SummationVariableContext) {
				args[i - 1] = visitSummationVariable((SummationVariableContext) ctx.getChild(i*2).getPayload());
			}
			else if (ctx.getChild(i*2).getPayload() instanceof TermContext) {
				args[i - 1] = (Term) visit(ctx.getChild(i*2));
			}
			else {
				throw new IllegalStateException();
			}
		}

		return new SummationAtom(p, args);
	}

	@Override
	public SummationVariable visitSummationVariable(SummationVariableContext ctx) {
		return new SummationVariable(ctx.IDENTIFIER().getText());
	}

	@Override
	public Coefficient visitCoefficient(CoefficientContext ctx) {
		if (ctx.number() != null) {
			return new ConstantNumber(visitNumber(ctx.number()));
		}
		else if (ctx.variable() != null) {
			return new Cardinality(new SummationVariable(ctx.variable().getText()));
		}
		else if (ctx.arithmeticOperator() != null) {
			Coefficient c1 = (Coefficient) visit(ctx.coefficient(0));
			Coefficient c2 = (Coefficient) visit(ctx.coefficient(1));

			if (ctx.arithmeticOperator().PLUS() != null) {
				return new Add(c1 , c2);
			}
			else if (ctx.arithmeticOperator().MINUS() != null) {
				return new Subtract(c1 , c2);
			}
			else if (ctx.arithmeticOperator().MULT() != null) {
				return new Multiply(c1 , c2);
			}
			else if (ctx.arithmeticOperator().DIV() != null) {
				return new Divide(c1 , c2);
			}
			else {
				throw new IllegalStateException("(Line " + ctx.getStart().getLine()+ ") Arithmetic operator not recognized.");
			}
		}
		else if (ctx.coeffOperator() != null) {
			Coefficient c1 = (Coefficient) visit(ctx.coefficient(0));
			Coefficient c2 = (Coefficient) visit(ctx.coefficient(1));

			if (ctx.coeffOperator().MAX() != null) {
				return new Max(c1, c2);
			}
			else if (ctx.coeffOperator().MIN() != null) {
				return new Min(c1, c2);
			}
			else {
				throw new IllegalStateException("(Line " + ctx.getStart().getLine()+ ") Coefficient operator not recognized.");
			}
		}
		else if (ctx.LPAREN() != null) {
			return (Coefficient) visit(ctx.getChild(1));
		}
		else {
			throw new IllegalStateException("(Line " + ctx.getStart().getLine()+ ") Coefficient expresion not recognized.");
		}
	}

	@Override
	public FunctionComparator visitArithmeticRuleRelation(ArithmeticRuleRelationContext ctx) {
		if (ctx.EQUAL() != null) {
			return FunctionComparator.Equality;
		}
		else if (ctx.LESS_THAN_EQUAL() != null) {
			return FunctionComparator.SmallerThan;
		}
		else if (ctx.GREATER_THAN_EQUAL() != null) {
			return FunctionComparator.LargerThan;
		}
		else {
			throw new IllegalStateException();
		}
	}

	@Override
	public Boolean visitLinearOperator(LinearOperatorContext ctx) {
		if (ctx.PLUS() != null) {
			return true;
		}
		else if (ctx.MINUS() != null) {
			return false;
		}
		else {
			throw new IllegalStateException();
		}
	}

	private static class SelectStatement {
		SummationVariable v;
		Formula f;
	}

	@Override
	public SelectStatement visitSelectStatement(SelectStatementContext ctx) {
		SelectStatement select = new SelectStatement();
		select.v = new SummationVariable(ctx.variable().getText());
		select.f = visitBoolExpression(ctx.boolExpression());
		return select;
	}

	@Override
	public Formula visitBoolExpression(BoolExpressionContext ctx) {
		if (ctx.literal() != null) {
			return visitLiteral(ctx.literal());
		}
		else if (ctx.or() != null) {
			return new Disjunction(visitBoolExpression(ctx.boolExpression(0)), visitBoolExpression(ctx.boolExpression(1)));
		}
		else if (ctx.and() != null) {
			return new Conjunction(visitBoolExpression(ctx.boolExpression(0)), visitBoolExpression(ctx.boolExpression(1)));
		}
		else if (ctx.boolExpression() != null) {
			return visitBoolExpression(ctx.boolExpression(0));
		}
		else {
			throw new IllegalStateException("(Line " + ctx.getStart().getLine()+ ") Boolean expresion not recognized.");
		}
	}

	@Override
	public Double visitWeightExpression(WeightExpressionContext ctx) {
		return Double.parseDouble(ctx.NONNEGATIVE_NUMBER().getText());
	}

	@Override
	public Atom visitAtom(AtomContext ctx) {
		if (ctx.predicate() != null) {
			Predicate p = visitPredicate(ctx.predicate());
			Term[] args = new Term[ctx.term().size()];
			for (int i = 0; i < args.length; i++) {
				args[i] = (Term) visit(ctx.term(i));
			}
			return new QueryAtom(p, args);
		}
		else if (ctx.termOperator() != null) {
			SpecialPredicate p;
			if (ctx.termOperator().notEqual() != null) {
				p = SpecialPredicate.NotEqual;
			}
			else if (ctx.termOperator().termEqual() != null) {
				p = SpecialPredicate.Equal;
			}
			else {
				throw new IllegalStateException();
			}
			return new QueryAtom(p, (Term) visit(ctx.term(0)), (Term) visit(ctx.term(1)));
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
	public Variable visitVariable(VariableContext ctx) {
		return new Variable(ctx.IDENTIFIER().getText());
	}

	@Override
	public UniqueID visitConstant(ConstantContext ctx) {
		return data.getUniqueID(ctx.IDENTIFIER().getText());
	}

	@Override
	public Double visitNumber(NumberContext ctx) {
		return Double.parseDouble(ctx.getText());
	}
}
