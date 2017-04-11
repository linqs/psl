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
package org.linqs.psl.parser;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Add;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Cardinality;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Divide;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Max;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Min;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Multiply;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Subtract;
import org.linqs.psl.model.rule.logical.UnweightedLogicalRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.parser.antlr.PSLBaseVisitor;
import org.linqs.psl.parser.antlr.PSLLexer;
import org.linqs.psl.parser.antlr.PSLParser;
import org.linqs.psl.parser.antlr.PSLParser.ArithmeticRuleExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.ArithmeticRuleOperandContext;
import org.linqs.psl.parser.antlr.PSLParser.ArithmeticRuleRelationContext;
import org.linqs.psl.parser.antlr.PSLParser.AtomContext;
import org.linqs.psl.parser.antlr.PSLParser.BooleanExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.BooleanConjunctiveExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.BooleanDisjunctiveExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.BooleanValueContext;
import org.linqs.psl.parser.antlr.PSLParser.CoefficientContext;
import org.linqs.psl.parser.antlr.PSLParser.CoefficientAdditiveExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.CoefficientExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.CoefficientFunctionContext;
import org.linqs.psl.parser.antlr.PSLParser.CoefficientMultiplicativeExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.CoefficientOperatorContext;
import org.linqs.psl.parser.antlr.PSLParser.ConjunctiveClauseContext;
import org.linqs.psl.parser.antlr.PSLParser.ConstantContext;
import org.linqs.psl.parser.antlr.PSLParser.DisjunctiveClauseContext;
import org.linqs.psl.parser.antlr.PSLParser.LinearOperatorContext;
import org.linqs.psl.parser.antlr.PSLParser.LiteralContext;
import org.linqs.psl.parser.antlr.PSLParser.LogicalRuleExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.NumberContext;
import org.linqs.psl.parser.antlr.PSLParser.PredicateContext;
import org.linqs.psl.parser.antlr.PSLParser.ProgramContext;
import org.linqs.psl.parser.antlr.PSLParser.PslRuleContext;
import org.linqs.psl.parser.antlr.PSLParser.PslRulePartialContext;
import org.linqs.psl.parser.antlr.PSLParser.SelectStatementContext;
import org.linqs.psl.parser.antlr.PSLParser.SummationAtomContext;
import org.linqs.psl.parser.antlr.PSLParser.SummationVariableContext;
import org.linqs.psl.parser.antlr.PSLParser.TermContext;
import org.linqs.psl.parser.antlr.PSLParser.UnweightedArithmeticRuleContext;
import org.linqs.psl.parser.antlr.PSLParser.UnweightedLogicalRuleContext;
import org.linqs.psl.parser.antlr.PSLParser.VariableContext;
import org.linqs.psl.parser.antlr.PSLParser.WeightExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.WeightedArithmeticRuleContext;
import org.linqs.psl.parser.antlr.PSLParser.WeightedLogicalRuleContext;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.io.Reader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ModelLoader extends PSLBaseVisitor<Object> {
	/**
	 * Parse a string into either a full PSL Rule or a rule without weight or potential squaring information.
	 */
	public static RulePartial loadRulePartial(DataStore data, String input) throws IOException {
		PSLParser parser = getParser(input);
		PslRulePartialContext context = null;

		try {
			context = parser.pslRulePartial();
		} catch (ParseCancellationException ex) {
			// Cancel the parse and rethrow the cause.
			throw (RuntimeException)ex.getCause();
		}

		ModelLoader visitor = new ModelLoader(data);
		return visitor.visitPslRulePartial(context);
	}

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
	public static Model load(DataStore data, Reader input) throws IOException {
		PSLParser parser = getParser(input);
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

	/**
	 * Get a parser over the given input.
	 */
	private static PSLParser getParser(Reader input) throws IOException {
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

		return parser;
	}

	private static PSLParser getParser(String input) throws IOException {
		return getParser(new StringReader(input));
	}

	// Non-static

	private final DataStore data;

	private ModelLoader(DataStore data) {
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
	public RulePartial visitPslRulePartial(PslRulePartialContext ctx) {
		if (ctx == null || ctx.getChildCount() < 2) {
			throw new IllegalStateException();
		}

		// The first child is a rule (logical or arithmetic), formula, or arithmetic expression.
		Object ruleCore = visit(ctx.getChild(0));
		if (!(ruleCore instanceof Rule) && !(ruleCore instanceof Formula) && !(ruleCore instanceof ArithmeticRuleExpression)) {
			throw new IllegalStateException();
		}

		if (ctx.getChildCount() == 2) {
			return new RulePartial(ruleCore);
		}

		// Any remaining children are select statements.
		// So, the core must be an ArithmeticRuleExpression.
		if (!(ruleCore instanceof ArithmeticRuleExpression)) {
			throw new IllegalStateException();
		}

		Map<SummationVariable, Formula> selectStatements = new HashMap<SummationVariable, Formula>();
		// Skip the initial node (ruleCore) and the EOF at the end.
		for (int i = 1; i < ctx.getChildCount() - 1; i++) {
			SelectStatement selectStatement = visitSelectStatement((SelectStatementContext)ctx.getChild(i));
			selectStatements.put(selectStatement.v, selectStatement.f);
		}

		return new RulePartial((ArithmeticRuleExpression)ruleCore, selectStatements);
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
		if (ctx.atom() != null) {
			return visitAtom(ctx.atom());
		}
		else if (ctx.not() != null) {
			return new Negation(visitLiteral(ctx.literal()));
		}
		else if (ctx.LPAREN() != null) {
			return visitLiteral(ctx.literal());
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
		// The operands to the arithmetic rule.
		List<ArithmeticRuleOperand> lhsOperands = new LinkedList<ArithmeticRuleOperand>();
		List<ArithmeticRuleOperand> rhsOperands = new LinkedList<ArithmeticRuleOperand>();
		List<ArithmeticRuleOperand> currentOperands = lhsOperands;

		// The sign of each operand.
		// The entire arithmetic expression will eventually become a linear
		// combination of all the operands compared against a single number.
		// ie we will simplify the expression by moving RHS terms to the LHS.
		// true means that the corresponding operand is positive on its respective side,
		// (lhs is true on the left, rhs is true on the right) false is negative.
		List<Boolean> lhsOperandSigns = new LinkedList<Boolean>();
		List<Boolean> rhsOperandSigns = new LinkedList<Boolean>();
		List<Boolean> currentOperandSigns = lhsOperandSigns;

		// There must be at least one operand on each side, and they are always positive.
		// Note that any negative sign here is associated with the coefficient.
		lhsOperandSigns.add(true);
		rhsOperandSigns.add(true);

		// The relational operator between the LHS and RHS.
		FunctionComparator relationalComparison = null;

		for (int i = 0; i < ctx.getChildCount(); i++) {
			// Even children are ArithmeticRuleOperand's, while odd children
			// will either be a LinearOperator or an ArithmeticRuleRelation.
			if (i % 2 == 0) {
				currentOperands.add(visitArithmeticRuleOperand(((ArithmeticRuleOperandContext)ctx.getChild(i))));
			} else {
				if (ctx.getChild(i).getPayload() instanceof ArithmeticRuleRelationContext) {
					relationalComparison = (FunctionComparator)visit(ctx.getChild(i));
					currentOperands = rhsOperands;
					currentOperandSigns = rhsOperandSigns;
				} else {
					currentOperandSigns.add(visitLinearOperator((LinearOperatorContext)ctx.getChild(i)));
				}
			}
		}

		List<Coefficient> coeffs = new LinkedList<Coefficient>();
		List<SummationAtomOrAtom> atoms = new LinkedList<SummationAtomOrAtom>();
		Coefficient finalCoeff = null;

		// Processes the LHS.
		for (int i = 0; i < lhsOperands.size(); i++) {
			// Check if it is just a coefficient
			if (lhsOperands.get(i).atom == null) {
				// If it is the first coefficient, uses it to start the final coefficient
				if (finalCoeff == null) {
					if (lhsOperandSigns.get(i)) {
						finalCoeff = new Multiply(new ConstantNumber(-1.0), lhsOperands.get(i).coefficient);
					} else {
						finalCoeff = lhsOperands.get(i).coefficient;
					}
				// Else, add it to the final coefficient
				} else {
					if (lhsOperandSigns.get(i)) {
						finalCoeff = new Subtract(finalCoeff, lhsOperands.get(i).coefficient);
					} else {
						finalCoeff = new Add(finalCoeff, lhsOperands.get(i).coefficient);
					}
				}
			// Else, process the SummationAtomOrAtom
			} else {
				Coefficient coeff = null;
				if (lhsOperands.get(i).coefficient == null) {
					coeff = new ConstantNumber(1.0);
				} else {
					coeff = lhsOperands.get(i).coefficient;
				}

				// Check the sign.
				if (!lhsOperandSigns.get(i).booleanValue()) {
					coeff = new Multiply(new ConstantNumber(-1.0), coeff);
				}

				coeffs.add(coeff);
				atoms.add(lhsOperands.get(i).atom);
			}
		}

		// Processes the RHS.
		for (int i = 0; i < rhsOperands.size(); i++) {
			// Check if it is just a coefficient
			if (rhsOperands.get(i).atom == null) {
				// If it is the first coefficient, uses it to start the final coefficient
				if (finalCoeff == null) {
					finalCoeff = rhsOperands.get(i).coefficient;
				// Else, add it to the final coefficient
				} else {
					if (rhsOperandSigns.get(i)) {
						finalCoeff = new Add(finalCoeff, rhsOperands.get(i).coefficient);
					} else {
						finalCoeff = new Subtract(finalCoeff, rhsOperands.get(i).coefficient);
					}
				}
			// Else, process the SummationAtomOrAtom
			} else {
				Coefficient coeff = null;
				if (rhsOperands.get(i).coefficient == null) {
					coeff = new ConstantNumber(1.0);
				} else {
					coeff = rhsOperands.get(i).coefficient;
				}

				// Check the sign.
				// Remember that the sign will swap once when we move the RHS to the LHS.
				if (rhsOperandSigns.get(i).booleanValue()) {
					coeff = new Multiply(new ConstantNumber(-1.0), coeff);
				}

				coeffs.add(coeff);
				atoms.add(rhsOperands.get(i).atom);
			}
		}

		if (finalCoeff == null) {
			finalCoeff = new ConstantNumber(0.0);
		}

		return new ArithmeticRuleExpression(coeffs, atoms, relationalComparison, finalCoeff);
	}

	@Override
	public ArithmeticRuleOperand visitArithmeticRuleOperand(ArithmeticRuleOperandContext ctx) {
		ArithmeticRuleOperand operand = new ArithmeticRuleOperand();

		// Check if the operand is just a coefficient
		if (ctx.getChildCount() == 1 && ctx.getChild(0).getPayload() instanceof CoefficientExpressionContext) {
			operand.coefficient = visitCoefficientExpression((CoefficientExpressionContext)ctx.getChild(0));
			return operand;
		}

		// Checks if there is a prepended multiplier coefficient
		int atomIndex = 0;
		if (ctx.getChild(0).getPayload() instanceof CoefficientExpressionContext) {
			operand.coefficient = visitCoefficientExpression((CoefficientExpressionContext)ctx.getChild(0));

			// Skip the optional '*'.
			atomIndex = (ctx.getChild(1).getPayload() instanceof CommonToken) ? 2 : 1;
		}

		// Parses SummationAtom or Atom
		operand.atom = (SummationAtomOrAtom)visit(ctx.getChild(atomIndex));

		// Checks if there is an appended divisor coefficient
		if (ctx.getChildCount() > atomIndex + 1) {
			Coefficient divisor = visitCoefficientExpression((CoefficientExpressionContext)ctx.getChild(atomIndex + 2));
			if (operand.coefficient == null) {
				operand.coefficient = new Divide(new ConstantNumber(1.0), divisor);
			} else {
				operand.coefficient = new Divide(operand.coefficient, divisor);
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
	public Coefficient visitCoefficientExpression(CoefficientExpressionContext ctx) {
		return visitCoefficientAdditiveExpression((CoefficientAdditiveExpressionContext)ctx.getChild(0));
	}

	@Override
	public Coefficient visitCoefficientAdditiveExpression(CoefficientAdditiveExpressionContext ctx) {
		// Passthrough to multiplicative expression.
		if (ctx.getChildCount() == 1) {
			return visitCoefficientMultiplicativeExpression((CoefficientMultiplicativeExpressionContext)ctx.getChild(0));
		}

		// Binary addative operation.
		Coefficient lhs = visitCoefficientAdditiveExpression((CoefficientAdditiveExpressionContext)ctx.getChild(0));
		Coefficient rhs = visitCoefficientMultiplicativeExpression((CoefficientMultiplicativeExpressionContext)ctx.getChild(2));

		if (ctx.PLUS() != null) {
			return new Add(lhs, rhs);
		} else {
			return new Subtract(lhs, rhs);
		}
	}

	@Override
	public Coefficient visitCoefficientMultiplicativeExpression(CoefficientMultiplicativeExpressionContext ctx) {
		// Passthrough to coefficient.
		if (ctx.getChildCount() == 1) {
			return visitCoefficient((CoefficientContext)ctx.getChild(0));
		}

		// Binary multiplicative operation.
		Coefficient lhs = visitCoefficientMultiplicativeExpression((CoefficientMultiplicativeExpressionContext)ctx.getChild(0));
		Coefficient rhs = visitCoefficient((CoefficientContext)ctx.getChild(2));

		if (ctx.MULT() != null) {
			return new Multiply(lhs, rhs);
		} else {
			return new Divide(lhs, rhs);
		}
	}

	@Override
	public Coefficient visitCoefficient(CoefficientContext ctx) {
		// Just a number
		if (ctx.number() != null) {
			return new ConstantNumber(visitNumber(ctx.number()));
		}

		// Coefficient surrounded by parens.
		if (ctx.getChildCount() == 3) {
			return visitCoefficientExpression((CoefficientExpressionContext)ctx.getChild(1));
		}

		// Coefficient Operator (cardinality, min, max).
		return visitCoefficientOperator((CoefficientOperatorContext)ctx.getChild(0));
	}

	@Override
	public Coefficient visitCoefficientOperator(CoefficientOperatorContext ctx) {
		// Cardinality.
		if (ctx.getChildCount() == 3) {
			return new Cardinality(new SummationVariable(ctx.variable().getText()));
		}

		// Coefficient function (min / max)
		return visitCoefficientFunction((CoefficientFunctionContext)ctx.getChild(0));
	}

	@Override
	public Coefficient visitCoefficientFunction(CoefficientFunctionContext ctx) {
		// Children: [function, open, lhs, comma, rhs, close]

		// All functions are binary.
		Coefficient lhs = visitCoefficientExpression((CoefficientExpressionContext)ctx.getChild(2));
		Coefficient rhs = visitCoefficientExpression((CoefficientExpressionContext)ctx.getChild(4));

		if (ctx.coefficientFunctionOperator().MAX() != null) {
			return new Max(lhs, rhs);
		} else {
			return new Min(lhs, rhs);
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
		} else if (ctx.MINUS() != null) {
			return false;
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public SelectStatement visitSelectStatement(SelectStatementContext ctx) {
		SelectStatement select = new SelectStatement();
		select.v = new SummationVariable(ctx.variable().getText());
		select.f = visitBooleanExpression(ctx.booleanExpression());

		return select;
	}

	@Override
	public Formula visitBooleanValue(BooleanValueContext ctx) {
		// A plain literal.
		if (ctx.getChildCount() == 1) {
			return visitLiteral(ctx.literal());
		}

		// Bool expression with parens.
		return visitBooleanExpression(ctx.booleanExpression());
	}

	@Override
	public Formula visitBooleanConjunctiveExpression(BooleanConjunctiveExpressionContext ctx) {
		// Passthrough to booleanValue.
		if (ctx.getChildCount() == 1) {
			return visitBooleanValue(ctx.booleanValue());
		}

		// Conjunction.
		Formula lhs = visitBooleanConjunctiveExpression(ctx.booleanConjunctiveExpression());
		Formula rhs = visitBooleanValue(ctx.booleanValue());
		return new Conjunction(lhs, rhs);
	}

	@Override
	public Formula visitBooleanDisjunctiveExpression(BooleanDisjunctiveExpressionContext ctx) {
		// Passthrough to booleanConjunctiveExpression.
		if (ctx.getChildCount() == 1) {
			return visitBooleanConjunctiveExpression(ctx.booleanConjunctiveExpression());
		}

		// Conjunction.
		Formula lhs = visitBooleanDisjunctiveExpression(ctx.booleanDisjunctiveExpression());
		Formula rhs = visitBooleanConjunctiveExpression(ctx.booleanConjunctiveExpression());
		return new Disjunction(lhs, rhs);
	}

	@Override
	public Formula visitBooleanExpression(BooleanExpressionContext ctx) {
		return visitBooleanDisjunctiveExpression(ctx.booleanDisjunctiveExpression());
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
			else if (ctx.termOperator().nonSymmetric() != null) {
				p = SpecialPredicate.NonSymmetric;
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

	private static class ArithmeticRuleOperand {
		SummationAtomOrAtom atom;
		Coefficient coefficient;

		private ArithmeticRuleOperand() {
			atom = null;
			coefficient = null;
		}
	}

	private static class SelectStatement {
		SummationVariable v;
		Formula f;
	}
}
