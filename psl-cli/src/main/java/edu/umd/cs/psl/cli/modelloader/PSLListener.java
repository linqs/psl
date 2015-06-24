// Generated from PSL.g4 by ANTLR 4.5
package edu.umd.cs.psl.cli.modelloader;
import org.antlr.v4.runtime.misc.NotNull;
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
	 * Enter a parse tree produced by {@link PSLParser#psl_rule}.
	 * @param ctx the parse tree
	 */
	void enterPsl_rule(PSLParser.Psl_ruleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#psl_rule}.
	 * @param ctx the parse tree
	 */
	void exitPsl_rule(PSLParser.Psl_ruleContext ctx);
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
	 * Enter a parse tree produced by {@link PSLParser#logical_rule}.
	 * @param ctx the parse tree
	 */
	void enterLogical_rule(PSLParser.Logical_ruleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#logical_rule}.
	 * @param ctx the parse tree
	 */
	void exitLogical_rule(PSLParser.Logical_ruleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#weighted_logical_rule}.
	 * @param ctx the parse tree
	 */
	void enterWeighted_logical_rule(PSLParser.Weighted_logical_ruleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#weighted_logical_rule}.
	 * @param ctx the parse tree
	 */
	void exitWeighted_logical_rule(PSLParser.Weighted_logical_ruleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#unweighted_logical_rule}.
	 * @param ctx the parse tree
	 */
	void enterUnweighted_logical_rule(PSLParser.Unweighted_logical_ruleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#unweighted_logical_rule}.
	 * @param ctx the parse tree
	 */
	void exitUnweighted_logical_rule(PSLParser.Unweighted_logical_ruleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#logical_rule_expression}.
	 * @param ctx the parse tree
	 */
	void enterLogical_rule_expression(PSLParser.Logical_rule_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#logical_rule_expression}.
	 * @param ctx the parse tree
	 */
	void exitLogical_rule_expression(PSLParser.Logical_rule_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#disjunctive_clause}.
	 * @param ctx the parse tree
	 */
	void enterDisjunctive_clause(PSLParser.Disjunctive_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#disjunctive_clause}.
	 * @param ctx the parse tree
	 */
	void exitDisjunctive_clause(PSLParser.Disjunctive_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#conjunctive_clause}.
	 * @param ctx the parse tree
	 */
	void enterConjunctive_clause(PSLParser.Conjunctive_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#conjunctive_clause}.
	 * @param ctx the parse tree
	 */
	void exitConjunctive_clause(PSLParser.Conjunctive_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#arithmetic_rule}.
	 * @param ctx the parse tree
	 */
	void enterArithmetic_rule(PSLParser.Arithmetic_ruleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#arithmetic_rule}.
	 * @param ctx the parse tree
	 */
	void exitArithmetic_rule(PSLParser.Arithmetic_ruleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#weighted_arithmetic_rule}.
	 * @param ctx the parse tree
	 */
	void enterWeighted_arithmetic_rule(PSLParser.Weighted_arithmetic_ruleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#weighted_arithmetic_rule}.
	 * @param ctx the parse tree
	 */
	void exitWeighted_arithmetic_rule(PSLParser.Weighted_arithmetic_ruleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#unweighted_arithmetic_rule}.
	 * @param ctx the parse tree
	 */
	void enterUnweighted_arithmetic_rule(PSLParser.Unweighted_arithmetic_ruleContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#unweighted_arithmetic_rule}.
	 * @param ctx the parse tree
	 */
	void exitUnweighted_arithmetic_rule(PSLParser.Unweighted_arithmetic_ruleContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#arithmetic_rule_expression}.
	 * @param ctx the parse tree
	 */
	void enterArithmetic_rule_expression(PSLParser.Arithmetic_rule_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#arithmetic_rule_expression}.
	 * @param ctx the parse tree
	 */
	void exitArithmetic_rule_expression(PSLParser.Arithmetic_rule_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#arithmetic_rule_operand}.
	 * @param ctx the parse tree
	 */
	void enterArithmetic_rule_operand(PSLParser.Arithmetic_rule_operandContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#arithmetic_rule_operand}.
	 * @param ctx the parse tree
	 */
	void exitArithmetic_rule_operand(PSLParser.Arithmetic_rule_operandContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#sum_augmented_atom}.
	 * @param ctx the parse tree
	 */
	void enterSum_augmented_atom(PSLParser.Sum_augmented_atomContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#sum_augmented_atom}.
	 * @param ctx the parse tree
	 */
	void exitSum_augmented_atom(PSLParser.Sum_augmented_atomContext ctx);
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
	 * Enter a parse tree produced by {@link PSLParser#select_statement}.
	 * @param ctx the parse tree
	 */
	void enterSelect_statement(PSLParser.Select_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#select_statement}.
	 * @param ctx the parse tree
	 */
	void exitSelect_statement(PSLParser.Select_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#bool_expression}.
	 * @param ctx the parse tree
	 */
	void enterBool_expression(PSLParser.Bool_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#bool_expression}.
	 * @param ctx the parse tree
	 */
	void exitBool_expression(PSLParser.Bool_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#weight_expression}.
	 * @param ctx the parse tree
	 */
	void enterWeight_expression(PSLParser.Weight_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#weight_expression}.
	 * @param ctx the parse tree
	 */
	void exitWeight_expression(PSLParser.Weight_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link PSLParser#exponent_expression}.
	 * @param ctx the parse tree
	 */
	void enterExponent_expression(PSLParser.Exponent_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#exponent_expression}.
	 * @param ctx the parse tree
	 */
	void exitExponent_expression(PSLParser.Exponent_expressionContext ctx);
}