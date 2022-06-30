/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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

import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
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
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.parser.antlr.PSLBaseVisitor;
import org.linqs.psl.parser.antlr.PSLLexer;
import org.linqs.psl.parser.antlr.PSLParser;
import org.linqs.psl.parser.antlr.PSLParser.ArithmeticRuleExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.ArithmeticCoefficientOperandAtomContext;
import org.linqs.psl.parser.antlr.PSLParser.ArithmeticCoefficientOperandContext;
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
import org.linqs.psl.parser.antlr.PSLParser.ConstantContext;
import org.linqs.psl.parser.antlr.PSLParser.LinearArithmeticExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.LinearArithmeticOperandContext;
import org.linqs.psl.parser.antlr.PSLParser.LinearOperatorContext;
import org.linqs.psl.parser.antlr.PSLParser.LogicalConjunctiveExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.LogicalConjunctiveValueContext;
import org.linqs.psl.parser.antlr.PSLParser.LogicalDisjunctiveExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.LogicalDisjunctiveValueContext;
import org.linqs.psl.parser.antlr.PSLParser.LogicalImplicationExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.LogicalNegationValueContext;
import org.linqs.psl.parser.antlr.PSLParser.LogicalRuleExpressionContext;
import org.linqs.psl.parser.antlr.PSLParser.NumberContext;
import org.linqs.psl.parser.antlr.PSLParser.PredicateContext;
import org.linqs.psl.parser.antlr.PSLParser.ProgramContext;
import org.linqs.psl.parser.antlr.PSLParser.PslRuleContext;
import org.linqs.psl.parser.antlr.PSLParser.PslRulePartialContext;
import org.linqs.psl.parser.antlr.PSLParser.FilterClauseContext;
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

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.Interval;
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
     * Parse a string into either a full PSL Rule or a rule without weight or potential squaring information.
     */
    public static RulePartial loadRulePartial(String input) {
        PSLParser parser = null;
        try {
            parser = getParser(input);
        } catch (IOException ex) {
            // Cancel the lex and rethrow.
            throw new RuntimeException("Failed to lex rule partial.", ex);
        }

        PslRulePartialContext context = null;
        try {
            context = parser.pslRulePartial();
        } catch (ParseCancellationException ex) {
            // Cancel the parse and rethrow the cause.
            throw (RuntimeException)ex.getCause();
        }

        ModelLoader visitor = new ModelLoader();
        return visitor.visitPslRulePartial(context);
    }

    /**
     * Parse and return a single rule.
     * If exactly one rule is not specified, an exception is thrown.
     */
    public static Rule loadRule(String input) {
        Model model = load(new StringReader(input));

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
    public static Model load(String input) {
        return load(new StringReader(input));
    }

    /**
     * Parse and return a Model (collection of rules).
     * The input should only contain rules and the DataStore should contain all the predicates
     * used by the rules.
     */
    public static Model load(Reader input) {
        PSLParser parser = null;
        try {
            parser = getParser(input);
        } catch (IOException ex) {
            // Cancel the lex and rethrow.
            throw new RuntimeException("Failed to lex rule partial.", ex);
        }

        ProgramContext program = null;
        try {
            program = parser.program();
        } catch (ParseCancellationException ex) {
            // Cancel the parse and rethrow the cause.
            throw (RuntimeException)ex.getCause();
        }

        ModelLoader visitor = new ModelLoader();
        return visitor.visitProgram(program, parser);
    }

    public static Atom loadAtom(String input) {
        return loadAtom(new StringReader(input));
    }

    public static Atom loadAtom(Reader input) {
        PSLParser parser = null;
        try {
            parser = getParser(input);
        } catch (IOException ex) {
            // Cancel the lex and rethrow.
            throw new RuntimeException("Failed to lex atom.", ex);
        }

        AtomContext atomContext = null;
        try {
            atomContext = parser.atom();
        } catch (ParseCancellationException ex) {
            // Cancel the parse and rethrow the cause.
            throw (RuntimeException)ex.getCause();
        }

        ModelLoader visitor = new ModelLoader();
        return visitor.visitAtom(atomContext);
    }

    /**
     * Get a parser over the given input.
     */
    private static PSLParser getParser(Reader input) throws IOException {
        PSLLexer lexer = new PSLLexer(CharStreams.fromReader(input));

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

    private ModelLoader() {}

    public Model visitProgram(ProgramContext ctx, PSLParser parser) {
        Model model = new Model();
        for (PslRuleContext ruleCtx : ctx.pslRule()) {
            try {
                model.addRule((Rule) visit(ruleCtx));
            } catch (RuntimeException ex) {
                throw new RuntimeException("Failed to compile rule: [" + parser.getTokenStream().getText(ruleCtx) + "]", ex);
            }
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

        // Any remaining children are filter statements.
        // So, the core must be an ArithmeticRuleExpression.
        if (!(ruleCore instanceof ArithmeticRuleExpression)) {
            throw new IllegalStateException();
        }

        Map<SummationVariable, Formula> filterClauses = new HashMap<SummationVariable, Formula>();
        // Skip the initial node (ruleCore) and the EOF at the end.
        for (int i = 1; i < ctx.getChildCount() - 1; i++) {
            FilterClause filterClause = visitFilterClause((FilterClauseContext)ctx.getChild(i));
            filterClauses.put(filterClause.v, filterClause.f);
        }

        return new RulePartial((ArithmeticRuleExpression)ruleCore, filterClauses);
    }

    @Override
    public WeightedLogicalRule visitWeightedLogicalRule(WeightedLogicalRuleContext ctx) {
        Float w = visitWeightExpression(ctx.weightExpression());
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
        if (ctx.logicalDisjunctiveExpression() != null) {
            return visitLogicalDisjunctiveExpression(ctx.logicalDisjunctiveExpression());
        }

        if (ctx.logicalImplicationExpression() != null) {
            return visitLogicalImplicationExpression(ctx.logicalImplicationExpression());
        }

        throw new IllegalStateException();
    }

    @Override
    public Formula visitLogicalImplicationExpression(LogicalImplicationExpressionContext ctx) {
        Formula body = visitLogicalConjunctiveExpression(ctx.logicalConjunctiveExpression());
        Formula head = visitLogicalDisjunctiveExpression(ctx.logicalDisjunctiveExpression());

        return new Implication(body, head);
    }

    @Override
    public Formula visitLogicalDisjunctiveExpression(LogicalDisjunctiveExpressionContext ctx) {
        // Passthrough to disjunctive value.
        if (ctx.getChildCount() == 1) {
            return visitLogicalDisjunctiveValue(ctx.logicalDisjunctiveValue());
        }

        // Binary disjunction.
        Formula lhs = visitLogicalDisjunctiveExpression(ctx.logicalDisjunctiveExpression());
        Formula rhs = visitLogicalDisjunctiveValue(ctx.logicalDisjunctiveValue());

        return new Disjunction(lhs, rhs).flatten();
    }

    @Override
    public Formula visitLogicalConjunctiveExpression(LogicalConjunctiveExpressionContext ctx) {
        // Passthrough to conjunctive value.
        if (ctx.getChildCount() == 1) {
            return visitLogicalConjunctiveValue(ctx.logicalConjunctiveValue());
        }

        // Binary disjunction.
        Formula lhs = visitLogicalConjunctiveExpression(ctx.logicalConjunctiveExpression());
        Formula rhs = visitLogicalConjunctiveValue(ctx.logicalConjunctiveValue());

        return new Conjunction(lhs, rhs).flatten();
    }

    @Override
    public Formula visitLogicalDisjunctiveValue(LogicalDisjunctiveValueContext ctx) {
        if (ctx.getChildCount() == 1) {
            return visitLogicalNegationValue(ctx.logicalNegationValue());
        }

        // Parens
        return visitLogicalDisjunctiveExpression(ctx.logicalDisjunctiveExpression());
    }

    @Override
    public Formula visitLogicalConjunctiveValue(LogicalConjunctiveValueContext ctx) {
        if (ctx.getChildCount() == 1) {
            return visitLogicalNegationValue(ctx.logicalNegationValue());
        }

        // Parens
        return visitLogicalConjunctiveExpression(ctx.logicalConjunctiveExpression());
    }

    @Override
    public Formula visitLogicalNegationValue(LogicalNegationValueContext ctx) {
        // Bare atom
        if (ctx.getChildCount() == 1) {
            return visitAtom(ctx.atom());
        }

        // Negation
        if (ctx.getChildCount() == 2) {
            return new Negation(visitLogicalNegationValue(ctx.logicalNegationValue()));
        }

        // Parens
        return visitLogicalNegationValue(ctx.logicalNegationValue());
    }

    @Override
    public WeightedArithmeticRule visitWeightedArithmeticRule(WeightedArithmeticRuleContext ctx) {
        Float w = visitWeightExpression(ctx.weightExpression());
        ArithmeticRuleExpression expression = (ArithmeticRuleExpression) visitArithmeticRuleExpression(ctx.arithmeticRuleExpression());
        Map<SummationVariable, Formula> filterClauses = new HashMap<SummationVariable, Formula>();
        for (int i = 0; i < ctx.filterClause().size(); i++) {
            FilterClause filterClause = visitFilterClause(ctx.filterClause(i));
            filterClauses.put(filterClause.v, filterClause.f);
        }
        Boolean sq = false;
        if (ctx.EXPONENT_EXPRESSION() != null) {
            sq = ctx.EXPONENT_EXPRESSION().getText().equals("^2");
        }
        return new WeightedArithmeticRule(expression, filterClauses, w, sq);
    }

    @Override
    public UnweightedArithmeticRule visitUnweightedArithmeticRule(UnweightedArithmeticRuleContext ctx) {
        ArithmeticRuleExpression expression = (ArithmeticRuleExpression) visitArithmeticRuleExpression(ctx.arithmeticRuleExpression());
        Map<SummationVariable, Formula> filterClauses = new HashMap<SummationVariable, Formula>();
        for (int i = 0; i < ctx.filterClause().size(); i++) {
            FilterClause filterClause = visitFilterClause(ctx.filterClause(i));
            filterClauses.put(filterClause.v, filterClause.f);
        }
        return new UnweightedArithmeticRule(expression, filterClauses);
    }

    @Override
    public ArithmeticRuleExpression visitArithmeticRuleExpression(ArithmeticRuleExpressionContext ctx) {
        LinearArithmeticExpression lhs = visitLinearArithmeticExpression((LinearArithmeticExpressionContext)ctx.getChild(0));
        FunctionComparator relationalComparison = visitArithmeticRuleRelation((ArithmeticRuleRelationContext)ctx.getChild(1));
        LinearArithmeticExpression rhs = visitLinearArithmeticExpression((LinearArithmeticExpressionContext)ctx.getChild(2));

        // Start the terms from the lhs.
        List<Coefficient> coefficients = lhs.coefficients;
        List<SummationAtomOrAtom> atoms = lhs.atoms;
        Coefficient finalCoefficient = null;

        // Add in the RHS terms, negating the coefficients.
        for (int i = 0; i < rhs.atoms.size(); i++) {
            coefficients.add(new Multiply(new ConstantNumber(-1.0f), rhs.coefficients.get(i)));
            atoms.add(rhs.atoms.get(i));
        }

        // Now the final coefficient which will appearon the RHS.
        // Note that we could start the coefficient at 0 and just add terms in, but we want to match legacy behavior.
        if (lhs.nonAtomCoefficient != null) {
            finalCoefficient = new Multiply(new ConstantNumber(-1.0f), lhs.nonAtomCoefficient);
        }

        if (rhs.nonAtomCoefficient != null) {
            if (finalCoefficient == null) {
                finalCoefficient = rhs.nonAtomCoefficient;
            } else {
                finalCoefficient = new Add(finalCoefficient, rhs.nonAtomCoefficient);
            }
        }

        if (finalCoefficient == null) {
            finalCoefficient = new ConstantNumber(0.0f);
        }

        // Finally, simplify all coefficients.
        for (int i = 0; i < coefficients.size(); i++) {
            coefficients.set(i, coefficients.get(i).simplify());
        }
        finalCoefficient = finalCoefficient.simplify();

        return new ArithmeticRuleExpression(coefficients, atoms, relationalComparison, finalCoefficient);
    }

    @Override
    public LinearArithmeticExpression visitLinearArithmeticExpression(LinearArithmeticExpressionContext ctx) {
        // Just an operand.
        if (ctx.getChildCount() == 1) {
            return visitLinearArithmeticOperand((LinearArithmeticOperandContext)ctx.getChild(0));
        }

        if (ctx.getChildCount() != 3) {
            throw new IllegalStateException("Expeciting three children.");
        }

        LinearArithmeticExpression lhs = visitLinearArithmeticExpression((LinearArithmeticExpressionContext)ctx.getChild(0));
        boolean isAddition = visitLinearOperator((LinearOperatorContext)ctx.getChild(1)).booleanValue();
        LinearArithmeticExpression rhs = visitLinearArithmeticOperand((LinearArithmeticOperandContext)ctx.getChild(2));

        // Start with the LHS.
        LinearArithmeticExpression expression = lhs;

        // Add in each term from the rhs, negating if the operator is a subtraction.
        for (int i = 0; i < rhs.atoms.size(); i++) {
            Coefficient coefficient = rhs.coefficients.get(i);
            if (!isAddition) {
                coefficient = new Multiply(new ConstantNumber(-1.0f), coefficient);
            }

            expression.atoms.add(rhs.atoms.get(i));
            expression.coefficients.add(coefficient);
        }

        // Note the additional, non-atom coefficients.
        Coefficient nonAtomCoefficient = null;
        if (lhs.nonAtomCoefficient != null) {
            nonAtomCoefficient = lhs.nonAtomCoefficient;
        }

        if (rhs.nonAtomCoefficient != null) {
            if (nonAtomCoefficient == null) {
                if (isAddition) {
                    nonAtomCoefficient = rhs.nonAtomCoefficient;
                } else {
                    nonAtomCoefficient = new Multiply(new ConstantNumber(-1.0f), rhs.nonAtomCoefficient);
                }
            } else {
                if (isAddition) {
                    nonAtomCoefficient = new Add(nonAtomCoefficient, rhs.nonAtomCoefficient);
                } else {
                    nonAtomCoefficient = new Subtract(nonAtomCoefficient, rhs.nonAtomCoefficient);
                }
            }
        }

        expression.nonAtomCoefficient = nonAtomCoefficient;

        return expression;
    }

    @Override
    public LinearArithmeticExpression visitLinearArithmeticOperand(LinearArithmeticOperandContext ctx) {
        // Must be a parenthesis expression.
        if (ctx.getChildCount() == 3) {
            return visitLinearArithmeticExpression((LinearArithmeticExpressionContext)ctx.getChild(1));
        }

        // A ArithmeticCoefficientOperand
        if (ctx.getChildCount() != 1) {
            throw new IllegalStateException("Expeciting three children.");
        }

        LinearArithmeticExpression expression = new LinearArithmeticExpression();

        ArithmeticCoefficientOperand operand = visitArithmeticCoefficientOperand((ArithmeticCoefficientOperandContext)ctx.getChild(0));
        Coefficient coefficient = new ConstantNumber(1.0f);
        if (operand.coefficient != null) {
            coefficient = operand.coefficient;
        }

        if (operand.atom != null) {
            expression.coefficients.add(coefficient);
            expression.atoms.add(operand.atom);
        } else {
            expression.nonAtomCoefficient = coefficient;
        }

        return expression;
    }

    @Override
    public ArithmeticCoefficientOperand visitArithmeticCoefficientOperand(ArithmeticCoefficientOperandContext ctx) {
        ArithmeticCoefficientOperand operand = new ArithmeticCoefficientOperand();

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
                operand.coefficient = new Divide(new ConstantNumber(1.0f), divisor);
            } else {
                operand.coefficient = new Divide(operand.coefficient, divisor);
            }
        }

        return operand;
    }

    @Override
    public SummationAtomOrAtom visitArithmeticCoefficientOperandAtom(ArithmeticCoefficientOperandAtomContext ctx) {
        // Must be a parenthesis expression.
        if (ctx.getChildCount() == 3) {
            return visitArithmeticCoefficientOperandAtom((ArithmeticCoefficientOperandAtomContext)ctx.getChild(1));
        }

        return (SummationAtomOrAtom)visit(ctx.getChild(0));
    }

    @Override
    public SummationAtomOrAtom visitSummationAtom(SummationAtomContext ctx) {
        Predicate predicate = visitPredicate(ctx.predicate());

        // We have strange numbering because of the predicate, parens, commas.
        SummationVariableOrTerm[] args = new SummationVariableOrTerm[ctx.getChildCount() / 2 - 1];
        for (int i = 1; i < ctx.getChildCount() / 2; i++) {
            if (ctx.getChild(i * 2).getPayload() instanceof SummationVariableContext) {
                args[i - 1] = visitSummationVariable((SummationVariableContext) ctx.getChild(i * 2).getPayload());
            } else if (ctx.getChild(i * 2).getPayload() instanceof TermContext) {
                args[i - 1] = (Term) visit(ctx.getChild(i * 2));
            } else {
                throw new IllegalStateException();
            }
        }

        // If we have any summation variables, then we have a SummationAtom, otherwise we have a GetAtom.
        boolean isSummation = false;
        for (SummationVariableOrTerm arg : args) {
            if (arg instanceof SummationVariable) {
                isSummation = true;
                break;
            }
        }

        if (isSummation) {
            return new SummationAtom(predicate, args);
        } else {
            Term[] termArgs = new Term[args.length];
            for (int i = 0; i < termArgs.length; i++) {
                termArgs[i] = (Term)args[i];
            }

            return new QueryAtom(predicate, termArgs);
        }
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
            return new ConstantNumber(visitNumber(ctx.number()).floatValue());
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
            return FunctionComparator.EQ;
        } else if (ctx.LESS_THAN_EQUAL() != null) {
            return FunctionComparator.LTE;
        } else if (ctx.GREATER_THAN_EQUAL() != null) {
            return FunctionComparator.GTE;
        } else {
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
    public FilterClause visitFilterClause(FilterClauseContext ctx) {
        FilterClause filter = new FilterClause();
        filter.v = new SummationVariable(ctx.variable().getText());
        filter.f = visitBooleanExpression(ctx.booleanExpression());

        return filter;
    }

    @Override
    public Formula visitBooleanValue(BooleanValueContext ctx) {
        // A logical value.
        if (ctx.logicalNegationValue() != null) {
            return visitLogicalNegationValue(ctx.logicalNegationValue());
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
    public Float visitWeightExpression(WeightExpressionContext ctx) {
        return Float.parseFloat(ctx.number().getText());
    }

    @Override
    public Atom visitAtom(AtomContext ctx) {
        if (ctx.predicate() != null) {
            Predicate predicate = visitPredicate(ctx.predicate());
            Term[] args = new Term[ctx.term().size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = (Term) visit(ctx.term(i));
            }
            return new QueryAtom(predicate, args);
        } else if (ctx.termOperator() != null) {
            GroundingOnlyPredicate predicate;
            if (ctx.termOperator().notEqual() != null) {
                predicate = GroundingOnlyPredicate.NotEqual;
            } else if (ctx.termOperator().termEqual() != null) {
                predicate = GroundingOnlyPredicate.Equal;
            } else if (ctx.termOperator().nonSymmetric() != null) {
                predicate = GroundingOnlyPredicate.NonSymmetric;
            } else {
                throw new IllegalStateException();
            }
            return new QueryAtom(predicate, (Term)visit(ctx.term(0)), (Term)visit(ctx.term(1)));
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public Predicate visitPredicate(PredicateContext ctx) {
        Predicate predicate = Predicate.get(ctx.IDENTIFIER().getText());
        if (predicate != null) {
            return predicate;
        } else {
            throw new IllegalStateException("Undefined predicate " + ctx.IDENTIFIER().getText());
        }
    }

    @Override
    public Variable visitVariable(VariableContext ctx) {
        return new Variable(ctx.IDENTIFIER().getText());
    }

    @Override
    public Constant visitConstant(ConstantContext ctx) {
        // We need to jump through these hoops to preserve whitespace.
        int contextStart = ctx.start.getStartIndex();
        int contextEnd = ctx.stop.getStopIndex();
        Interval interval = new Interval(contextStart, contextEnd);
        String text = ctx.start.getInputStream().getText(interval);

        // Strip the quotes (first and last characters).
        text = text.substring(1, text.length() - 1);
        text = replaceLiterals(text);

        return new StringAttribute(text);
    }

    private String replaceLiterals(String text) {
        if (!text.contains("\\")) {
            return text;
        }

        text = text.replace("\\'", "'");
        text = text.replace("\\\"", "\"");
        text = text.replace("\\t", "\t");
        text = text.replace("\\n", "\n");
        text = text.replace("\\r", "\r");
        text = text.replace("\\\\", "\\");

        return text;
    }

    @Override
    public Float visitNumber(NumberContext ctx) {
        return Float.parseFloat(ctx.getText());
    }

    private static class ArithmeticCoefficientOperand {
        SummationAtomOrAtom atom;
        Coefficient coefficient;

        private ArithmeticCoefficientOperand() {
            atom = null;
            coefficient = null;
        }
    }

    // Minus operations will be attached to the corresponding coefficient.
    // There will be no gaps in |coefficients| or |atoms|.
    // Solo coefficients will be combined toegther into |nonAtomCoefficient|.
    // Solo atoms will get a coefficient of one.
    private static class LinearArithmeticExpression {
        public List<Coefficient> coefficients;
        public List<SummationAtomOrAtom> atoms;
        public Coefficient nonAtomCoefficient;

        public LinearArithmeticExpression() {
            coefficients = new LinkedList<Coefficient>();
            atoms = new LinkedList<SummationAtomOrAtom>();
            nonAtomCoefficient = null;
        }
    }

    private static class FilterClause {
        SummationVariable v;
        Formula f;
    }
}
