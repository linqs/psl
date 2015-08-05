// Generated from PSL.g4 by ANTLR 4.5
package edu.umd.cs.psl.cli.modelloader;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link PSLParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface PSLVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link PSLParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProgram(PSLParser.ProgramContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#psl_rule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPsl_rule(PSLParser.Psl_ruleContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#predicate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPredicate(PSLParser.PredicateContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtom(PSLParser.AtomContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral(PSLParser.LiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#term}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTerm(PSLParser.TermContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#variable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariable(PSLParser.VariableContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#constant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstant(PSLParser.ConstantContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#logical_rule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogical_rule(PSLParser.Logical_ruleContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#weighted_logical_rule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWeighted_logical_rule(PSLParser.Weighted_logical_ruleContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#unweighted_logical_rule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnweighted_logical_rule(PSLParser.Unweighted_logical_ruleContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#logical_rule_expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogical_rule_expression(PSLParser.Logical_rule_expressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#disjunctive_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDisjunctive_clause(PSLParser.Disjunctive_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#conjunctive_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConjunctive_clause(PSLParser.Conjunctive_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#arithmetic_rule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithmetic_rule(PSLParser.Arithmetic_ruleContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#weighted_arithmetic_rule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWeighted_arithmetic_rule(PSLParser.Weighted_arithmetic_ruleContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#unweighted_arithmetic_rule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnweighted_arithmetic_rule(PSLParser.Unweighted_arithmetic_ruleContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#arithmetic_rule_expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithmetic_rule_expression(PSLParser.Arithmetic_rule_expressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#arithmetic_rule_operand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithmetic_rule_operand(PSLParser.Arithmetic_rule_operandContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#sum_augmented_atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSum_augmented_atom(PSLParser.Sum_augmented_atomContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#coefficient}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCoefficient(PSLParser.CoefficientContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#select_statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_statement(PSLParser.Select_statementContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#bool_expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expression(PSLParser.Bool_expressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#weight_expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWeight_expression(PSLParser.Weight_expressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link PSLParser#number}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumber(PSLParser.NumberContext ctx);
}