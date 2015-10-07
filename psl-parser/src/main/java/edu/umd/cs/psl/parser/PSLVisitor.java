// Generated from PSL.g4 by ANTLR 4.1
package edu.umd.cs.psl.parser;
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
	 * Visit a parse tree produced by {@link PSLParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(@NotNull PSLParser.ExpressionContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#argument}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgument(@NotNull PSLParser.ArgumentContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtom(@NotNull PSLParser.AtomContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#intConstant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntConstant(@NotNull PSLParser.IntConstantContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#constant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstant(@NotNull PSLParser.ConstantContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#strConstant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStrConstant(@NotNull PSLParser.StrConstantContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#weight}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWeight(@NotNull PSLParser.WeightContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#predicateDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPredicateDefinition(@NotNull PSLParser.PredicateDefinitionContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#predicate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPredicate(@NotNull PSLParser.PredicateContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#constraint}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstraint(@NotNull PSLParser.ConstraintContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#kernel}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitKernel(@NotNull PSLParser.KernelContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProgram(@NotNull PSLParser.ProgramContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#constraintType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstraintType(@NotNull PSLParser.ConstraintTypeContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#argumentType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgumentType(@NotNull PSLParser.ArgumentTypeContext ctx);

	/**
	 * Visit a parse tree produced by {@link PSLParser#variable}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariable(@NotNull PSLParser.VariableContext ctx);
}
