// Generated from PSL.g4 by ANTLR 4.5.3
package edu.umd.cs.psl.cli.modelloader;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link PSLParser}.
 */
public interface PSLListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link PSLParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(PSLParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(PSLParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#pslRule}.
	 * @param ctx the parse tree
	 */
	void enterPslRule(PSLParser.PslRuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#pslRule}.
	 * @param ctx the parse tree
	 */
	void exitPslRule(PSLParser.PslRuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterPredicate(PSLParser.PredicateContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitPredicate(PSLParser.PredicateContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterAtom(PSLParser.AtomContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitAtom(PSLParser.AtomContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(PSLParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(PSLParser.LiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#term}.
	 * @param ctx the parse tree
	 */
	void enterTerm(PSLParser.TermContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#term}.
	 * @param ctx the parse tree
	 */
	void exitTerm(PSLParser.TermContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#variable}.
	 * @param ctx the parse tree
	 */
	void enterVariable(PSLParser.VariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#variable}.
	 * @param ctx the parse tree
	 */
	void exitVariable(PSLParser.VariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterConstant(PSLParser.ConstantContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitConstant(PSLParser.ConstantContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#logicalRule}.
	 * @param ctx the parse tree
	 */
	void enterLogicalRule(PSLParser.LogicalRuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#logicalRule}.
	 * @param ctx the parse tree
	 */
	void exitLogicalRule(PSLParser.LogicalRuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#weightedLogicalRule}.
	 * @param ctx the parse tree
	 */
	void enterWeightedLogicalRule(PSLParser.WeightedLogicalRuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#weightedLogicalRule}.
	 * @param ctx the parse tree
	 */
	void exitWeightedLogicalRule(PSLParser.WeightedLogicalRuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#unweightedLogicalRule}.
	 * @param ctx the parse tree
	 */
	void enterUnweightedLogicalRule(PSLParser.UnweightedLogicalRuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#unweightedLogicalRule}.
	 * @param ctx the parse tree
	 */
	void exitUnweightedLogicalRule(PSLParser.UnweightedLogicalRuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#logicalRuleExpression}.
	 * @param ctx the parse tree
	 */
	void enterLogicalRuleExpression(PSLParser.LogicalRuleExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#logicalRuleExpression}.
	 * @param ctx the parse tree
	 */
	void exitLogicalRuleExpression(PSLParser.LogicalRuleExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#disjunctiveClause}.
	 * @param ctx the parse tree
	 */
	void enterDisjunctiveClause(PSLParser.DisjunctiveClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#disjunctiveClause}.
	 * @param ctx the parse tree
	 */
	void exitDisjunctiveClause(PSLParser.DisjunctiveClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#conjunctiveClause}.
	 * @param ctx the parse tree
	 */
	void enterConjunctiveClause(PSLParser.ConjunctiveClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#conjunctiveClause}.
	 * @param ctx the parse tree
	 */
	void exitConjunctiveClause(PSLParser.ConjunctiveClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#arithmeticRule}.
	 * @param ctx the parse tree
	 */
	void enterArithmeticRule(PSLParser.ArithmeticRuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#arithmeticRule}.
	 * @param ctx the parse tree
	 */
	void exitArithmeticRule(PSLParser.ArithmeticRuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#weightedArithmeticRule}.
	 * @param ctx the parse tree
	 */
	void enterWeightedArithmeticRule(PSLParser.WeightedArithmeticRuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#weightedArithmeticRule}.
	 * @param ctx the parse tree
	 */
	void exitWeightedArithmeticRule(PSLParser.WeightedArithmeticRuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#unweightedArithmeticRule}.
	 * @param ctx the parse tree
	 */
	void enterUnweightedArithmeticRule(PSLParser.UnweightedArithmeticRuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#unweightedArithmeticRule}.
	 * @param ctx the parse tree
	 */
	void exitUnweightedArithmeticRule(PSLParser.UnweightedArithmeticRuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#arithmeticRuleExpression}.
	 * @param ctx the parse tree
	 */
	void enterArithmeticRuleExpression(PSLParser.ArithmeticRuleExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#arithmeticRuleExpression}.
	 * @param ctx the parse tree
	 */
	void exitArithmeticRuleExpression(PSLParser.ArithmeticRuleExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#arithmeticRuleOperand}.
	 * @param ctx the parse tree
	 */
	void enterArithmeticRuleOperand(PSLParser.ArithmeticRuleOperandContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#arithmeticRuleOperand}.
	 * @param ctx the parse tree
	 */
	void exitArithmeticRuleOperand(PSLParser.ArithmeticRuleOperandContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#summationAtom}.
	 * @param ctx the parse tree
	 */
	void enterSummationAtom(PSLParser.SummationAtomContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#summationAtom}.
	 * @param ctx the parse tree
	 */
	void exitSummationAtom(PSLParser.SummationAtomContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#summationVariable}.
	 * @param ctx the parse tree
	 */
	void enterSummationVariable(PSLParser.SummationVariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#summationVariable}.
	 * @param ctx the parse tree
	 */
	void exitSummationVariable(PSLParser.SummationVariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#coefficient}.
	 * @param ctx the parse tree
	 */
	void enterCoefficient(PSLParser.CoefficientContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#coefficient}.
	 * @param ctx the parse tree
	 */
	void exitCoefficient(PSLParser.CoefficientContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#selectStatement}.
	 * @param ctx the parse tree
	 */
	void enterSelectStatement(PSLParser.SelectStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#selectStatement}.
	 * @param ctx the parse tree
	 */
	void exitSelectStatement(PSLParser.SelectStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#boolExpression}.
	 * @param ctx the parse tree
	 */
	void enterBoolExpression(PSLParser.BoolExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#boolExpression}.
	 * @param ctx the parse tree
	 */
	void exitBoolExpression(PSLParser.BoolExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#weightExpression}.
	 * @param ctx the parse tree
	 */
	void enterWeightExpression(PSLParser.WeightExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#weightExpression}.
	 * @param ctx the parse tree
	 */
	void exitWeightExpression(PSLParser.WeightExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#not}.
	 * @param ctx the parse tree
	 */
	void enterNot(PSLParser.NotContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#not}.
	 * @param ctx the parse tree
	 */
	void exitNot(PSLParser.NotContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#and}.
	 * @param ctx the parse tree
	 */
	void enterAnd(PSLParser.AndContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#and}.
	 * @param ctx the parse tree
	 */
	void exitAnd(PSLParser.AndContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#or}.
	 * @param ctx the parse tree
	 */
	void enterOr(PSLParser.OrContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#or}.
	 * @param ctx the parse tree
	 */
	void exitOr(PSLParser.OrContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#then}.
	 * @param ctx the parse tree
	 */
	void enterThen(PSLParser.ThenContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#then}.
	 * @param ctx the parse tree
	 */
	void exitThen(PSLParser.ThenContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#impliedBy}.
	 * @param ctx the parse tree
	 */
	void enterImpliedBy(PSLParser.ImpliedByContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#impliedBy}.
	 * @param ctx the parse tree
	 */
	void exitImpliedBy(PSLParser.ImpliedByContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#termOperator}.
	 * @param ctx the parse tree
	 */
	void enterTermOperator(PSLParser.TermOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#termOperator}.
	 * @param ctx the parse tree
	 */
	void exitTermOperator(PSLParser.TermOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#termEqual}.
	 * @param ctx the parse tree
	 */
	void enterTermEqual(PSLParser.TermEqualContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#termEqual}.
	 * @param ctx the parse tree
	 */
	void exitTermEqual(PSLParser.TermEqualContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#notEqual}.
	 * @param ctx the parse tree
	 */
	void enterNotEqual(PSLParser.NotEqualContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#notEqual}.
	 * @param ctx the parse tree
	 */
	void exitNotEqual(PSLParser.NotEqualContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#arithmeticRuleRelation}.
	 * @param ctx the parse tree
	 */
	void enterArithmeticRuleRelation(PSLParser.ArithmeticRuleRelationContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#arithmeticRuleRelation}.
	 * @param ctx the parse tree
	 */
	void exitArithmeticRuleRelation(PSLParser.ArithmeticRuleRelationContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#arithmeticOperator}.
	 * @param ctx the parse tree
	 */
	void enterArithmeticOperator(PSLParser.ArithmeticOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#arithmeticOperator}.
	 * @param ctx the parse tree
	 */
	void exitArithmeticOperator(PSLParser.ArithmeticOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#linearOperator}.
	 * @param ctx the parse tree
	 */
	void enterLinearOperator(PSLParser.LinearOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#linearOperator}.
	 * @param ctx the parse tree
	 */
	void exitLinearOperator(PSLParser.LinearOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#coeffOperator}.
	 * @param ctx the parse tree
	 */
	void enterCoeffOperator(PSLParser.CoeffOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#coeffOperator}.
	 * @param ctx the parse tree
	 */
	void exitCoeffOperator(PSLParser.CoeffOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#number}.
	 * @param ctx the parse tree
	 */
	void enterNumber(PSLParser.NumberContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#number}.
	 * @param ctx the parse tree
	 */
	void exitNumber(PSLParser.NumberContext ctx);
}