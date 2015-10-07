// Generated from PSL.g4 by ANTLR 4.1
package edu.umd.cs.psl.parser;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link PSLParser}.
 */
public interface PSLListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link PSLParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(@NotNull PSLParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(@NotNull PSLParser.ExpressionContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#argument}.
	 * @param ctx the parse tree
	 */
	void enterArgument(@NotNull PSLParser.ArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#argument}.
	 * @param ctx the parse tree
	 */
	void exitArgument(@NotNull PSLParser.ArgumentContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterAtom(@NotNull PSLParser.AtomContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitAtom(@NotNull PSLParser.AtomContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#intConstant}.
	 * @param ctx the parse tree
	 */
	void enterIntConstant(@NotNull PSLParser.IntConstantContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#intConstant}.
	 * @param ctx the parse tree
	 */
	void exitIntConstant(@NotNull PSLParser.IntConstantContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterConstant(@NotNull PSLParser.ConstantContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitConstant(@NotNull PSLParser.ConstantContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#strConstant}.
	 * @param ctx the parse tree
	 */
	void enterStrConstant(@NotNull PSLParser.StrConstantContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#strConstant}.
	 * @param ctx the parse tree
	 */
	void exitStrConstant(@NotNull PSLParser.StrConstantContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#weight}.
	 * @param ctx the parse tree
	 */
	void enterWeight(@NotNull PSLParser.WeightContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#weight}.
	 * @param ctx the parse tree
	 */
	void exitWeight(@NotNull PSLParser.WeightContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#predicateDefinition}.
	 * @param ctx the parse tree
	 */
	void enterPredicateDefinition(@NotNull PSLParser.PredicateDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#predicateDefinition}.
	 * @param ctx the parse tree
	 */
	void exitPredicateDefinition(@NotNull PSLParser.PredicateDefinitionContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterPredicate(@NotNull PSLParser.PredicateContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitPredicate(@NotNull PSLParser.PredicateContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#constraint}.
	 * @param ctx the parse tree
	 */
	void enterConstraint(@NotNull PSLParser.ConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#constraint}.
	 * @param ctx the parse tree
	 */
	void exitConstraint(@NotNull PSLParser.ConstraintContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#kernel}.
	 * @param ctx the parse tree
	 */
	void enterKernel(@NotNull PSLParser.KernelContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#kernel}.
	 * @param ctx the parse tree
	 */
	void exitKernel(@NotNull PSLParser.KernelContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(@NotNull PSLParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(@NotNull PSLParser.ProgramContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#constraintType}.
	 * @param ctx the parse tree
	 */
	void enterConstraintType(@NotNull PSLParser.ConstraintTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#constraintType}.
	 * @param ctx the parse tree
	 */
	void exitConstraintType(@NotNull PSLParser.ConstraintTypeContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#argumentType}.
	 * @param ctx the parse tree
	 */
	void enterArgumentType(@NotNull PSLParser.ArgumentTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#argumentType}.
	 * @param ctx the parse tree
	 */
	void exitArgumentType(@NotNull PSLParser.ArgumentTypeContext ctx);

	/**
	 * Enter a parse tree produced by {@link PSLParser#variable}.
	 * @param ctx the parse tree
	 */
	void enterVariable(@NotNull PSLParser.VariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link PSLParser#variable}.
	 * @param ctx the parse tree
	 */
	void exitVariable(@NotNull PSLParser.VariableContext ctx);
}
