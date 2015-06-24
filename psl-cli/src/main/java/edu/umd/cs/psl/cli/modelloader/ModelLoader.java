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

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import edu.umd.cs.psl.cli.modelloader.PSLParser.Arithmetic_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Arithmetic_rule_expressionContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Arithmetic_rule_operandContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.AtomContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Bool_expressionContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.CoefficientContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Conjunctive_clauseContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.ConstantContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Disjunctive_clauseContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Exponent_expressionContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.LiteralContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Logical_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Logical_rule_expressionContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.PredicateContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.ProgramContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Psl_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Select_statementContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Sum_augmented_atomContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.TermContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Unweighted_arithmetic_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Unweighted_logical_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.VariableContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Weight_expressionContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Weighted_arithmetic_ruleContext;
import edu.umd.cs.psl.cli.modelloader.PSLParser.Weighted_logical_ruleContext;
import edu.umd.cs.psl.model.Model;

public class ModelLoader implements PSLListener {

	static public Model parseModel(InputStream input) throws IOException  {
		Model model = new Model();
		PSLLexer lexer = new PSLLexer(new ANTLRInputStream(input));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		PSLParser parser = new PSLParser(tokens);
		ParserRuleContext tree = parser.getContext();
		
		ParseTreeWalker walker = new ParseTreeWalker();
		ModelLoader extractor = new ModelLoader(model);
		walker.walk(extractor, tree);
		
		return model;
	}
	
	protected final Model model;

	public ModelLoader(Model model) {
		this.model = model;
	}

	@Override
	public void enterEveryRule(ParserRuleContext arg0) {
		System.out.println("Entering a rule: " + arg0.getText());
		
	}

	@Override
	public void exitEveryRule(ParserRuleContext arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitErrorNode(ErrorNode arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTerminal(TerminalNode arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterProgram(ProgramContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitProgram(ProgramContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterPsl_rule(Psl_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitPsl_rule(Psl_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterPredicate(PredicateContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitPredicate(PredicateContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterAtom(AtomContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitAtom(AtomContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterLiteral(LiteralContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitLiteral(LiteralContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterTerm(TermContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitTerm(TermContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterVariable(VariableContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitVariable(VariableContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterConstant(ConstantContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitConstant(ConstantContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterLogical_rule(Logical_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitLogical_rule(Logical_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterWeighted_logical_rule(Weighted_logical_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitWeighted_logical_rule(Weighted_logical_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterUnweighted_logical_rule(Unweighted_logical_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitUnweighted_logical_rule(Unweighted_logical_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterLogical_rule_expression(Logical_rule_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitLogical_rule_expression(Logical_rule_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterDisjunctive_clause(Disjunctive_clauseContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitDisjunctive_clause(Disjunctive_clauseContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterConjunctive_clause(Conjunctive_clauseContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitConjunctive_clause(Conjunctive_clauseContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterArithmetic_rule(Arithmetic_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitArithmetic_rule(Arithmetic_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterWeighted_arithmetic_rule(
			Weighted_arithmetic_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitWeighted_arithmetic_rule(Weighted_arithmetic_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterUnweighted_arithmetic_rule(
			Unweighted_arithmetic_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitUnweighted_arithmetic_rule(
			Unweighted_arithmetic_ruleContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterArithmetic_rule_expression(
			Arithmetic_rule_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitArithmetic_rule_expression(
			Arithmetic_rule_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterArithmetic_rule_operand(Arithmetic_rule_operandContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitArithmetic_rule_operand(Arithmetic_rule_operandContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterSum_augmented_atom(Sum_augmented_atomContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitSum_augmented_atom(Sum_augmented_atomContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterCoefficient(CoefficientContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitCoefficient(CoefficientContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterSelect_statement(Select_statementContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitSelect_statement(Select_statementContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterBool_expression(Bool_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitBool_expression(Bool_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterWeight_expression(Weight_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitWeight_expression(Weight_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void enterExponent_expression(Exponent_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exitExponent_expression(Exponent_expressionContext ctx) {
		// TODO Auto-generated method stub
		
	}
}
