// Generated from PSL.g4 by ANTLR 4.5.3
package edu.umd.cs.psl.cli.modelloader;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class PSLParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.5.3", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		EXPONENT_EXPRESSION=1, NOT=2, AND=3, OR=4, THEN=5, IMPLIED_BY=6, TERM_EQUAL=7, 
		NOT_EQUAL=8, LESS_THAN_EQUAL=9, GREATER_THAN_EQUAL=10, EQUAL=11, PLUS=12, 
		MINUS=13, MULT=14, DIV=15, MAX=16, MIN=17, IDENTIFIER=18, NONNEGATIVE_NUMBER=19, 
		PERIOD=20, COMMA=21, COLON=22, PIPE=23, LPAREN=24, RPAREN=25, LBRACE=26, 
		RBRACE=27, LBRACKET=28, RBRACKET=29, SINGLE_QUOTE=30, DOUBLE_QUOTE=31, 
		WS=32, COMMENT=33, LINE_COMMENT=34, PYTHON_COMMENT=35;
	public static final int
		RULE_program = 0, RULE_pslRule = 1, RULE_predicate = 2, RULE_atom = 3, 
		RULE_literal = 4, RULE_term = 5, RULE_variable = 6, RULE_constant = 7, 
		RULE_logicalRule = 8, RULE_weightedLogicalRule = 9, RULE_unweightedLogicalRule = 10, 
		RULE_logicalRuleExpression = 11, RULE_disjunctiveClause = 12, RULE_conjunctiveClause = 13, 
		RULE_arithmeticRule = 14, RULE_weightedArithmeticRule = 15, RULE_unweightedArithmeticRule = 16, 
		RULE_arithmeticRuleExpression = 17, RULE_arithmeticRuleOperand = 18, RULE_summationAtom = 19, 
		RULE_summationVariable = 20, RULE_coefficient = 21, RULE_selectStatement = 22, 
		RULE_boolExpression = 23, RULE_weightExpression = 24, RULE_termOperator = 25, 
		RULE_arithmeticRuleRelation = 26, RULE_arithmeticOperator = 27, RULE_linearOperator = 28, 
		RULE_coeffOperator = 29, RULE_number = 30;
	public static final String[] ruleNames = {
		"program", "pslRule", "predicate", "atom", "literal", "term", "variable", 
		"constant", "logicalRule", "weightedLogicalRule", "unweightedLogicalRule", 
		"logicalRuleExpression", "disjunctiveClause", "conjunctiveClause", "arithmeticRule", 
		"weightedArithmeticRule", "unweightedArithmeticRule", "arithmeticRuleExpression", 
		"arithmeticRuleOperand", "summationAtom", "summationVariable", "coefficient", 
		"selectStatement", "boolExpression", "weightExpression", "termOperator", 
		"arithmeticRuleRelation", "arithmeticOperator", "linearOperator", "coeffOperator", 
		"number"
	};

	private static final String[] _LITERAL_NAMES = {
		null, null, null, null, null, null, null, "'=='", "'!='", "'<='", "'>='", 
		"'='", "'+'", "'-'", "'*'", "'/'", "'@Max'", "'@Min'", null, null, "'.'", 
		"','", "':'", "'|'", "'('", "')'", "'{'", "'}'", "'['", "']'", "'''", 
		"'\"'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, "EXPONENT_EXPRESSION", "NOT", "AND", "OR", "THEN", "IMPLIED_BY", 
		"TERM_EQUAL", "NOT_EQUAL", "LESS_THAN_EQUAL", "GREATER_THAN_EQUAL", "EQUAL", 
		"PLUS", "MINUS", "MULT", "DIV", "MAX", "MIN", "IDENTIFIER", "NONNEGATIVE_NUMBER", 
		"PERIOD", "COMMA", "COLON", "PIPE", "LPAREN", "RPAREN", "LBRACE", "RBRACE", 
		"LBRACKET", "RBRACKET", "SINGLE_QUOTE", "DOUBLE_QUOTE", "WS", "COMMENT", 
		"LINE_COMMENT", "PYTHON_COMMENT"
	};
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "PSL.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public PSLParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}
	public static class ProgramContext extends ParserRuleContext {
		public List<PslRuleContext> pslRule() {
			return getRuleContexts(PslRuleContext.class);
		}
		public PslRuleContext pslRule(int i) {
			return getRuleContext(PslRuleContext.class,i);
		}
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterProgram(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitProgram(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitProgram(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(63); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(62);
				pslRule();
				}
				}
				setState(65); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NOT) | (1L << MINUS) | (1L << MAX) | (1L << MIN) | (1L << IDENTIFIER) | (1L << NONNEGATIVE_NUMBER) | (1L << PIPE) | (1L << LPAREN) | (1L << SINGLE_QUOTE) | (1L << DOUBLE_QUOTE))) != 0) );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PslRuleContext extends ParserRuleContext {
		public LogicalRuleContext logicalRule() {
			return getRuleContext(LogicalRuleContext.class,0);
		}
		public ArithmeticRuleContext arithmeticRule() {
			return getRuleContext(ArithmeticRuleContext.class,0);
		}
		public PslRuleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pslRule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterPslRule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitPslRule(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitPslRule(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PslRuleContext pslRule() throws RecognitionException {
		PslRuleContext _localctx = new PslRuleContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_pslRule);
		try {
			setState(69);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(67);
				logicalRule();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(68);
				arithmeticRule();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PredicateContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(PSLParser.IDENTIFIER, 0); }
		public PredicateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_predicate; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterPredicate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitPredicate(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitPredicate(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PredicateContext predicate() throws RecognitionException {
		PredicateContext _localctx = new PredicateContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_predicate);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(71);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AtomContext extends ParserRuleContext {
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(PSLParser.LPAREN, 0); }
		public List<TermContext> term() {
			return getRuleContexts(TermContext.class);
		}
		public TermContext term(int i) {
			return getRuleContext(TermContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(PSLParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(PSLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(PSLParser.COMMA, i);
		}
		public TermOperatorContext termOperator() {
			return getRuleContext(TermOperatorContext.class,0);
		}
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_atom);
		int _la;
		try {
			setState(89);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(73);
				predicate();
				setState(74);
				match(LPAREN);
				setState(75);
				term();
				setState(80);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(76);
					match(COMMA);
					setState(77);
					term();
					}
					}
					setState(82);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(83);
				match(RPAREN);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(85);
				term();
				setState(86);
				termOperator();
				setState(87);
				term();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LiteralContext extends ParserRuleContext {
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public TerminalNode NOT() { return getToken(PSLParser.NOT, 0); }
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_literal);
		try {
			setState(94);
			switch (_input.LA(1)) {
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 1);
				{
				setState(91);
				atom();
				}
				break;
			case NOT:
				enterOuterAlt(_localctx, 2);
				{
				setState(92);
				match(NOT);
				setState(93);
				literal();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TermContext extends ParserRuleContext {
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public ConstantContext constant() {
			return getRuleContext(ConstantContext.class,0);
		}
		public TermContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_term; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterTerm(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitTerm(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitTerm(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TermContext term() throws RecognitionException {
		TermContext _localctx = new TermContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_term);
		try {
			setState(98);
			switch (_input.LA(1)) {
			case IDENTIFIER:
				enterOuterAlt(_localctx, 1);
				{
				setState(96);
				variable();
				}
				break;
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(97);
				constant();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VariableContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(PSLParser.IDENTIFIER, 0); }
		public VariableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterVariable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitVariable(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitVariable(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableContext variable() throws RecognitionException {
		VariableContext _localctx = new VariableContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_variable);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(100);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ConstantContext extends ParserRuleContext {
		public List<TerminalNode> SINGLE_QUOTE() { return getTokens(PSLParser.SINGLE_QUOTE); }
		public TerminalNode SINGLE_QUOTE(int i) {
			return getToken(PSLParser.SINGLE_QUOTE, i);
		}
		public TerminalNode IDENTIFIER() { return getToken(PSLParser.IDENTIFIER, 0); }
		public List<TerminalNode> DOUBLE_QUOTE() { return getTokens(PSLParser.DOUBLE_QUOTE); }
		public TerminalNode DOUBLE_QUOTE(int i) {
			return getToken(PSLParser.DOUBLE_QUOTE, i);
		}
		public ConstantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constant; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterConstant(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitConstant(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitConstant(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstantContext constant() throws RecognitionException {
		ConstantContext _localctx = new ConstantContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_constant);
		try {
			setState(108);
			switch (_input.LA(1)) {
			case SINGLE_QUOTE:
				enterOuterAlt(_localctx, 1);
				{
				setState(102);
				match(SINGLE_QUOTE);
				setState(103);
				match(IDENTIFIER);
				setState(104);
				match(SINGLE_QUOTE);
				}
				break;
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(105);
				match(DOUBLE_QUOTE);
				setState(106);
				match(IDENTIFIER);
				setState(107);
				match(DOUBLE_QUOTE);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LogicalRuleContext extends ParserRuleContext {
		public WeightedLogicalRuleContext weightedLogicalRule() {
			return getRuleContext(WeightedLogicalRuleContext.class,0);
		}
		public UnweightedLogicalRuleContext unweightedLogicalRule() {
			return getRuleContext(UnweightedLogicalRuleContext.class,0);
		}
		public LogicalRuleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logicalRule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterLogicalRule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitLogicalRule(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitLogicalRule(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LogicalRuleContext logicalRule() throws RecognitionException {
		LogicalRuleContext _localctx = new LogicalRuleContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_logicalRule);
		try {
			setState(112);
			switch (_input.LA(1)) {
			case NONNEGATIVE_NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(110);
				weightedLogicalRule();
				}
				break;
			case NOT:
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(111);
				unweightedLogicalRule();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class WeightedLogicalRuleContext extends ParserRuleContext {
		public WeightExpressionContext weightExpression() {
			return getRuleContext(WeightExpressionContext.class,0);
		}
		public LogicalRuleExpressionContext logicalRuleExpression() {
			return getRuleContext(LogicalRuleExpressionContext.class,0);
		}
		public TerminalNode EXPONENT_EXPRESSION() { return getToken(PSLParser.EXPONENT_EXPRESSION, 0); }
		public WeightedLogicalRuleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_weightedLogicalRule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterWeightedLogicalRule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitWeightedLogicalRule(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitWeightedLogicalRule(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WeightedLogicalRuleContext weightedLogicalRule() throws RecognitionException {
		WeightedLogicalRuleContext _localctx = new WeightedLogicalRuleContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_weightedLogicalRule);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(114);
			weightExpression();
			setState(115);
			logicalRuleExpression();
			setState(117);
			_la = _input.LA(1);
			if (_la==EXPONENT_EXPRESSION) {
				{
				setState(116);
				match(EXPONENT_EXPRESSION);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class UnweightedLogicalRuleContext extends ParserRuleContext {
		public LogicalRuleExpressionContext logicalRuleExpression() {
			return getRuleContext(LogicalRuleExpressionContext.class,0);
		}
		public TerminalNode PERIOD() { return getToken(PSLParser.PERIOD, 0); }
		public UnweightedLogicalRuleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unweightedLogicalRule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterUnweightedLogicalRule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitUnweightedLogicalRule(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitUnweightedLogicalRule(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnweightedLogicalRuleContext unweightedLogicalRule() throws RecognitionException {
		UnweightedLogicalRuleContext _localctx = new UnweightedLogicalRuleContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_unweightedLogicalRule);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(119);
			logicalRuleExpression();
			setState(120);
			match(PERIOD);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LogicalRuleExpressionContext extends ParserRuleContext {
		public DisjunctiveClauseContext disjunctiveClause() {
			return getRuleContext(DisjunctiveClauseContext.class,0);
		}
		public TerminalNode IMPLIED_BY() { return getToken(PSLParser.IMPLIED_BY, 0); }
		public ConjunctiveClauseContext conjunctiveClause() {
			return getRuleContext(ConjunctiveClauseContext.class,0);
		}
		public TerminalNode THEN() { return getToken(PSLParser.THEN, 0); }
		public LogicalRuleExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logicalRuleExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterLogicalRuleExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitLogicalRuleExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitLogicalRuleExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LogicalRuleExpressionContext logicalRuleExpression() throws RecognitionException {
		LogicalRuleExpressionContext _localctx = new LogicalRuleExpressionContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_logicalRuleExpression);
		try {
			setState(131);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,9,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(122);
				disjunctiveClause();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(123);
				disjunctiveClause();
				setState(124);
				match(IMPLIED_BY);
				setState(125);
				conjunctiveClause();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(127);
				conjunctiveClause();
				setState(128);
				match(THEN);
				setState(129);
				disjunctiveClause();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DisjunctiveClauseContext extends ParserRuleContext {
		public List<LiteralContext> literal() {
			return getRuleContexts(LiteralContext.class);
		}
		public LiteralContext literal(int i) {
			return getRuleContext(LiteralContext.class,i);
		}
		public List<TerminalNode> OR() { return getTokens(PSLParser.OR); }
		public TerminalNode OR(int i) {
			return getToken(PSLParser.OR, i);
		}
		public DisjunctiveClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_disjunctiveClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterDisjunctiveClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitDisjunctiveClause(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitDisjunctiveClause(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DisjunctiveClauseContext disjunctiveClause() throws RecognitionException {
		DisjunctiveClauseContext _localctx = new DisjunctiveClauseContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_disjunctiveClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(133);
			literal();
			setState(138);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(134);
				match(OR);
				setState(135);
				literal();
				}
				}
				setState(140);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ConjunctiveClauseContext extends ParserRuleContext {
		public List<LiteralContext> literal() {
			return getRuleContexts(LiteralContext.class);
		}
		public LiteralContext literal(int i) {
			return getRuleContext(LiteralContext.class,i);
		}
		public List<TerminalNode> AND() { return getTokens(PSLParser.AND); }
		public TerminalNode AND(int i) {
			return getToken(PSLParser.AND, i);
		}
		public ConjunctiveClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_conjunctiveClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterConjunctiveClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitConjunctiveClause(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitConjunctiveClause(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConjunctiveClauseContext conjunctiveClause() throws RecognitionException {
		ConjunctiveClauseContext _localctx = new ConjunctiveClauseContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_conjunctiveClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(141);
			literal();
			setState(146);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(142);
				match(AND);
				setState(143);
				literal();
				}
				}
				setState(148);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArithmeticRuleContext extends ParserRuleContext {
		public WeightedArithmeticRuleContext weightedArithmeticRule() {
			return getRuleContext(WeightedArithmeticRuleContext.class,0);
		}
		public UnweightedArithmeticRuleContext unweightedArithmeticRule() {
			return getRuleContext(UnweightedArithmeticRuleContext.class,0);
		}
		public ArithmeticRuleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmeticRule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArithmeticRule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArithmeticRule(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitArithmeticRule(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArithmeticRuleContext arithmeticRule() throws RecognitionException {
		ArithmeticRuleContext _localctx = new ArithmeticRuleContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_arithmeticRule);
		try {
			setState(151);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,12,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(149);
				weightedArithmeticRule();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(150);
				unweightedArithmeticRule();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class WeightedArithmeticRuleContext extends ParserRuleContext {
		public WeightExpressionContext weightExpression() {
			return getRuleContext(WeightExpressionContext.class,0);
		}
		public ArithmeticRuleExpressionContext arithmeticRuleExpression() {
			return getRuleContext(ArithmeticRuleExpressionContext.class,0);
		}
		public TerminalNode EXPONENT_EXPRESSION() { return getToken(PSLParser.EXPONENT_EXPRESSION, 0); }
		public List<SelectStatementContext> selectStatement() {
			return getRuleContexts(SelectStatementContext.class);
		}
		public SelectStatementContext selectStatement(int i) {
			return getRuleContext(SelectStatementContext.class,i);
		}
		public WeightedArithmeticRuleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_weightedArithmeticRule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterWeightedArithmeticRule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitWeightedArithmeticRule(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitWeightedArithmeticRule(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WeightedArithmeticRuleContext weightedArithmeticRule() throws RecognitionException {
		WeightedArithmeticRuleContext _localctx = new WeightedArithmeticRuleContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_weightedArithmeticRule);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(153);
			weightExpression();
			setState(154);
			arithmeticRuleExpression();
			setState(156);
			_la = _input.LA(1);
			if (_la==EXPONENT_EXPRESSION) {
				{
				setState(155);
				match(EXPONENT_EXPRESSION);
				}
			}

			setState(161);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LBRACE) {
				{
				{
				setState(158);
				selectStatement();
				}
				}
				setState(163);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class UnweightedArithmeticRuleContext extends ParserRuleContext {
		public ArithmeticRuleExpressionContext arithmeticRuleExpression() {
			return getRuleContext(ArithmeticRuleExpressionContext.class,0);
		}
		public TerminalNode PERIOD() { return getToken(PSLParser.PERIOD, 0); }
		public List<SelectStatementContext> selectStatement() {
			return getRuleContexts(SelectStatementContext.class);
		}
		public SelectStatementContext selectStatement(int i) {
			return getRuleContext(SelectStatementContext.class,i);
		}
		public UnweightedArithmeticRuleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unweightedArithmeticRule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterUnweightedArithmeticRule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitUnweightedArithmeticRule(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitUnweightedArithmeticRule(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnweightedArithmeticRuleContext unweightedArithmeticRule() throws RecognitionException {
		UnweightedArithmeticRuleContext _localctx = new UnweightedArithmeticRuleContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_unweightedArithmeticRule);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(164);
			arithmeticRuleExpression();
			setState(165);
			match(PERIOD);
			setState(169);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LBRACE) {
				{
				{
				setState(166);
				selectStatement();
				}
				}
				setState(171);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArithmeticRuleExpressionContext extends ParserRuleContext {
		public List<ArithmeticRuleOperandContext> arithmeticRuleOperand() {
			return getRuleContexts(ArithmeticRuleOperandContext.class);
		}
		public ArithmeticRuleOperandContext arithmeticRuleOperand(int i) {
			return getRuleContext(ArithmeticRuleOperandContext.class,i);
		}
		public ArithmeticRuleRelationContext arithmeticRuleRelation() {
			return getRuleContext(ArithmeticRuleRelationContext.class,0);
		}
		public List<LinearOperatorContext> linearOperator() {
			return getRuleContexts(LinearOperatorContext.class);
		}
		public LinearOperatorContext linearOperator(int i) {
			return getRuleContext(LinearOperatorContext.class,i);
		}
		public ArithmeticRuleExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmeticRuleExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArithmeticRuleExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArithmeticRuleExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitArithmeticRuleExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArithmeticRuleExpressionContext arithmeticRuleExpression() throws RecognitionException {
		ArithmeticRuleExpressionContext _localctx = new ArithmeticRuleExpressionContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_arithmeticRuleExpression);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(172);
			arithmeticRuleOperand();
			setState(178);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==PLUS || _la==MINUS) {
				{
				{
				setState(173);
				linearOperator();
				setState(174);
				arithmeticRuleOperand();
				}
				}
				setState(180);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(181);
			arithmeticRuleRelation();
			setState(182);
			arithmeticRuleOperand();
			setState(188);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(183);
					linearOperator();
					setState(184);
					arithmeticRuleOperand();
					}
					} 
				}
				setState(190);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArithmeticRuleOperandContext extends ParserRuleContext {
		public SummationAtomContext summationAtom() {
			return getRuleContext(SummationAtomContext.class,0);
		}
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public List<CoefficientContext> coefficient() {
			return getRuleContexts(CoefficientContext.class);
		}
		public CoefficientContext coefficient(int i) {
			return getRuleContext(CoefficientContext.class,i);
		}
		public TerminalNode DIV() { return getToken(PSLParser.DIV, 0); }
		public TerminalNode MULT() { return getToken(PSLParser.MULT, 0); }
		public ArithmeticRuleOperandContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmeticRuleOperand; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArithmeticRuleOperand(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArithmeticRuleOperand(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitArithmeticRuleOperand(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArithmeticRuleOperandContext arithmeticRuleOperand() throws RecognitionException {
		ArithmeticRuleOperandContext _localctx = new ArithmeticRuleOperandContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_arithmeticRuleOperand);
		int _la;
		try {
			setState(206);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,22,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(195);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << MINUS) | (1L << MAX) | (1L << MIN) | (1L << NONNEGATIVE_NUMBER) | (1L << PIPE) | (1L << LPAREN))) != 0)) {
					{
					setState(191);
					coefficient(0);
					setState(193);
					_la = _input.LA(1);
					if (_la==MULT) {
						{
						setState(192);
						match(MULT);
						}
					}

					}
				}

				setState(199);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
				case 1:
					{
					setState(197);
					summationAtom();
					}
					break;
				case 2:
					{
					setState(198);
					atom();
					}
					break;
				}
				setState(203);
				_la = _input.LA(1);
				if (_la==DIV) {
					{
					setState(201);
					match(DIV);
					setState(202);
					coefficient(0);
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(205);
				coefficient(0);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SummationAtomContext extends ParserRuleContext {
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(PSLParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(PSLParser.RPAREN, 0); }
		public List<SummationVariableContext> summationVariable() {
			return getRuleContexts(SummationVariableContext.class);
		}
		public SummationVariableContext summationVariable(int i) {
			return getRuleContext(SummationVariableContext.class,i);
		}
		public List<TermContext> term() {
			return getRuleContexts(TermContext.class);
		}
		public TermContext term(int i) {
			return getRuleContext(TermContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(PSLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(PSLParser.COMMA, i);
		}
		public SummationAtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_summationAtom; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterSummationAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitSummationAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitSummationAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SummationAtomContext summationAtom() throws RecognitionException {
		SummationAtomContext _localctx = new SummationAtomContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_summationAtom);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(208);
			predicate();
			setState(209);
			match(LPAREN);
			setState(212);
			switch (_input.LA(1)) {
			case PLUS:
				{
				setState(210);
				summationVariable();
				}
				break;
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				{
				setState(211);
				term();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(221);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(214);
				match(COMMA);
				setState(217);
				switch (_input.LA(1)) {
				case PLUS:
					{
					setState(215);
					summationVariable();
					}
					break;
				case IDENTIFIER:
				case SINGLE_QUOTE:
				case DOUBLE_QUOTE:
					{
					setState(216);
					term();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				}
				setState(223);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(224);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SummationVariableContext extends ParserRuleContext {
		public TerminalNode PLUS() { return getToken(PSLParser.PLUS, 0); }
		public TerminalNode IDENTIFIER() { return getToken(PSLParser.IDENTIFIER, 0); }
		public SummationVariableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_summationVariable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterSummationVariable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitSummationVariable(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitSummationVariable(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SummationVariableContext summationVariable() throws RecognitionException {
		SummationVariableContext _localctx = new SummationVariableContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_summationVariable);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(226);
			match(PLUS);
			setState(227);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CoefficientContext extends ParserRuleContext {
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public List<TerminalNode> PIPE() { return getTokens(PSLParser.PIPE); }
		public TerminalNode PIPE(int i) {
			return getToken(PSLParser.PIPE, i);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public CoeffOperatorContext coeffOperator() {
			return getRuleContext(CoeffOperatorContext.class,0);
		}
		public TerminalNode LBRACKET() { return getToken(PSLParser.LBRACKET, 0); }
		public List<CoefficientContext> coefficient() {
			return getRuleContexts(CoefficientContext.class);
		}
		public CoefficientContext coefficient(int i) {
			return getRuleContext(CoefficientContext.class,i);
		}
		public TerminalNode RBRACKET() { return getToken(PSLParser.RBRACKET, 0); }
		public List<TerminalNode> COMMA() { return getTokens(PSLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(PSLParser.COMMA, i);
		}
		public TerminalNode LPAREN() { return getToken(PSLParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(PSLParser.RPAREN, 0); }
		public ArithmeticOperatorContext arithmeticOperator() {
			return getRuleContext(ArithmeticOperatorContext.class,0);
		}
		public CoefficientContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_coefficient; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterCoefficient(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitCoefficient(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitCoefficient(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CoefficientContext coefficient() throws RecognitionException {
		return coefficient(0);
	}

	private CoefficientContext coefficient(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		CoefficientContext _localctx = new CoefficientContext(_ctx, _parentState);
		CoefficientContext _prevctx = _localctx;
		int _startState = 42;
		enterRecursionRule(_localctx, 42, RULE_coefficient, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(250);
			switch (_input.LA(1)) {
			case MINUS:
			case NONNEGATIVE_NUMBER:
				{
				setState(230);
				number();
				}
				break;
			case PIPE:
				{
				setState(231);
				match(PIPE);
				setState(232);
				variable();
				setState(233);
				match(PIPE);
				}
				break;
			case MAX:
			case MIN:
				{
				setState(235);
				coeffOperator();
				setState(236);
				match(LBRACKET);
				setState(237);
				coefficient(0);
				setState(240); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(238);
					match(COMMA);
					setState(239);
					coefficient(0);
					}
					}
					setState(242); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==COMMA );
				setState(244);
				match(RBRACKET);
				}
				break;
			case LPAREN:
				{
				setState(246);
				match(LPAREN);
				setState(247);
				coefficient(0);
				setState(248);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(258);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,28,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new CoefficientContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_coefficient);
					setState(252);
					if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
					setState(253);
					arithmeticOperator();
					setState(254);
					coefficient(4);
					}
					} 
				}
				setState(260);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,28,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class SelectStatementContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(PSLParser.LBRACE, 0); }
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public TerminalNode COLON() { return getToken(PSLParser.COLON, 0); }
		public BoolExpressionContext boolExpression() {
			return getRuleContext(BoolExpressionContext.class,0);
		}
		public TerminalNode RBRACE() { return getToken(PSLParser.RBRACE, 0); }
		public SelectStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_selectStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterSelectStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitSelectStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitSelectStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SelectStatementContext selectStatement() throws RecognitionException {
		SelectStatementContext _localctx = new SelectStatementContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_selectStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(261);
			match(LBRACE);
			setState(262);
			variable();
			setState(263);
			match(COLON);
			setState(264);
			boolExpression(0);
			setState(265);
			match(RBRACE);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BoolExpressionContext extends ParserRuleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(PSLParser.LPAREN, 0); }
		public List<BoolExpressionContext> boolExpression() {
			return getRuleContexts(BoolExpressionContext.class);
		}
		public BoolExpressionContext boolExpression(int i) {
			return getRuleContext(BoolExpressionContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(PSLParser.RPAREN, 0); }
		public TerminalNode OR() { return getToken(PSLParser.OR, 0); }
		public TerminalNode AND() { return getToken(PSLParser.AND, 0); }
		public BoolExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_boolExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterBoolExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitBoolExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitBoolExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BoolExpressionContext boolExpression() throws RecognitionException {
		return boolExpression(0);
	}

	private BoolExpressionContext boolExpression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		BoolExpressionContext _localctx = new BoolExpressionContext(_ctx, _parentState);
		BoolExpressionContext _prevctx = _localctx;
		int _startState = 46;
		enterRecursionRule(_localctx, 46, RULE_boolExpression, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(273);
			switch (_input.LA(1)) {
			case NOT:
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				{
				setState(268);
				literal();
				}
				break;
			case LPAREN:
				{
				setState(269);
				match(LPAREN);
				setState(270);
				boolExpression(0);
				setState(271);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(283);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,31,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(281);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,30,_ctx) ) {
					case 1:
						{
						_localctx = new BoolExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_boolExpression);
						setState(275);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(276);
						match(OR);
						setState(277);
						boolExpression(3);
						}
						break;
					case 2:
						{
						_localctx = new BoolExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_boolExpression);
						setState(278);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(279);
						match(AND);
						setState(280);
						boolExpression(2);
						}
						break;
					}
					} 
				}
				setState(285);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,31,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class WeightExpressionContext extends ParserRuleContext {
		public TerminalNode NONNEGATIVE_NUMBER() { return getToken(PSLParser.NONNEGATIVE_NUMBER, 0); }
		public TerminalNode COLON() { return getToken(PSLParser.COLON, 0); }
		public WeightExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_weightExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterWeightExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitWeightExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitWeightExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WeightExpressionContext weightExpression() throws RecognitionException {
		WeightExpressionContext _localctx = new WeightExpressionContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_weightExpression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(286);
			match(NONNEGATIVE_NUMBER);
			setState(287);
			match(COLON);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TermOperatorContext extends ParserRuleContext {
		public TerminalNode TERM_EQUAL() { return getToken(PSLParser.TERM_EQUAL, 0); }
		public TerminalNode NOT_EQUAL() { return getToken(PSLParser.NOT_EQUAL, 0); }
		public TermOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_termOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterTermOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitTermOperator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitTermOperator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TermOperatorContext termOperator() throws RecognitionException {
		TermOperatorContext _localctx = new TermOperatorContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_termOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(289);
			_la = _input.LA(1);
			if ( !(_la==TERM_EQUAL || _la==NOT_EQUAL) ) {
			_errHandler.recoverInline(this);
			} else {
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArithmeticRuleRelationContext extends ParserRuleContext {
		public TerminalNode LESS_THAN_EQUAL() { return getToken(PSLParser.LESS_THAN_EQUAL, 0); }
		public TerminalNode GREATER_THAN_EQUAL() { return getToken(PSLParser.GREATER_THAN_EQUAL, 0); }
		public TerminalNode EQUAL() { return getToken(PSLParser.EQUAL, 0); }
		public ArithmeticRuleRelationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmeticRuleRelation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArithmeticRuleRelation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArithmeticRuleRelation(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitArithmeticRuleRelation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArithmeticRuleRelationContext arithmeticRuleRelation() throws RecognitionException {
		ArithmeticRuleRelationContext _localctx = new ArithmeticRuleRelationContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_arithmeticRuleRelation);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(291);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LESS_THAN_EQUAL) | (1L << GREATER_THAN_EQUAL) | (1L << EQUAL))) != 0)) ) {
			_errHandler.recoverInline(this);
			} else {
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArithmeticOperatorContext extends ParserRuleContext {
		public TerminalNode PLUS() { return getToken(PSLParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(PSLParser.MINUS, 0); }
		public TerminalNode MULT() { return getToken(PSLParser.MULT, 0); }
		public TerminalNode DIV() { return getToken(PSLParser.DIV, 0); }
		public ArithmeticOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmeticOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArithmeticOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArithmeticOperator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitArithmeticOperator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArithmeticOperatorContext arithmeticOperator() throws RecognitionException {
		ArithmeticOperatorContext _localctx = new ArithmeticOperatorContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_arithmeticOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(293);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << PLUS) | (1L << MINUS) | (1L << MULT) | (1L << DIV))) != 0)) ) {
			_errHandler.recoverInline(this);
			} else {
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LinearOperatorContext extends ParserRuleContext {
		public TerminalNode PLUS() { return getToken(PSLParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(PSLParser.MINUS, 0); }
		public LinearOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_linearOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterLinearOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitLinearOperator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitLinearOperator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LinearOperatorContext linearOperator() throws RecognitionException {
		LinearOperatorContext _localctx = new LinearOperatorContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_linearOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(295);
			_la = _input.LA(1);
			if ( !(_la==PLUS || _la==MINUS) ) {
			_errHandler.recoverInline(this);
			} else {
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CoeffOperatorContext extends ParserRuleContext {
		public TerminalNode MAX() { return getToken(PSLParser.MAX, 0); }
		public TerminalNode MIN() { return getToken(PSLParser.MIN, 0); }
		public CoeffOperatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_coeffOperator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterCoeffOperator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitCoeffOperator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitCoeffOperator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CoeffOperatorContext coeffOperator() throws RecognitionException {
		CoeffOperatorContext _localctx = new CoeffOperatorContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_coeffOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(297);
			_la = _input.LA(1);
			if ( !(_la==MAX || _la==MIN) ) {
			_errHandler.recoverInline(this);
			} else {
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NumberContext extends ParserRuleContext {
		public TerminalNode NONNEGATIVE_NUMBER() { return getToken(PSLParser.NONNEGATIVE_NUMBER, 0); }
		public TerminalNode MINUS() { return getToken(PSLParser.MINUS, 0); }
		public NumberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_number; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterNumber(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitNumber(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitNumber(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NumberContext number() throws RecognitionException {
		NumberContext _localctx = new NumberContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_number);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(300);
			_la = _input.LA(1);
			if (_la==MINUS) {
				{
				setState(299);
				match(MINUS);
				}
			}

			setState(302);
			match(NONNEGATIVE_NUMBER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 21:
			return coefficient_sempred((CoefficientContext)_localctx, predIndex);
		case 23:
			return boolExpression_sempred((BoolExpressionContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean coefficient_sempred(CoefficientContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 3);
		}
		return true;
	}
	private boolean boolExpression_sempred(BoolExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return precpred(_ctx, 2);
		case 2:
			return precpred(_ctx, 1);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\3%\u0133\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \3\2"+
		"\6\2B\n\2\r\2\16\2C\3\3\3\3\5\3H\n\3\3\4\3\4\3\5\3\5\3\5\3\5\3\5\7\5Q"+
		"\n\5\f\5\16\5T\13\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5\\\n\5\3\6\3\6\3\6\5\6"+
		"a\n\6\3\7\3\7\5\7e\n\7\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\t\5\to\n\t\3\n\3"+
		"\n\5\ns\n\n\3\13\3\13\3\13\5\13x\n\13\3\f\3\f\3\f\3\r\3\r\3\r\3\r\3\r"+
		"\3\r\3\r\3\r\3\r\5\r\u0086\n\r\3\16\3\16\3\16\7\16\u008b\n\16\f\16\16"+
		"\16\u008e\13\16\3\17\3\17\3\17\7\17\u0093\n\17\f\17\16\17\u0096\13\17"+
		"\3\20\3\20\5\20\u009a\n\20\3\21\3\21\3\21\5\21\u009f\n\21\3\21\7\21\u00a2"+
		"\n\21\f\21\16\21\u00a5\13\21\3\22\3\22\3\22\7\22\u00aa\n\22\f\22\16\22"+
		"\u00ad\13\22\3\23\3\23\3\23\3\23\7\23\u00b3\n\23\f\23\16\23\u00b6\13\23"+
		"\3\23\3\23\3\23\3\23\3\23\7\23\u00bd\n\23\f\23\16\23\u00c0\13\23\3\24"+
		"\3\24\5\24\u00c4\n\24\5\24\u00c6\n\24\3\24\3\24\5\24\u00ca\n\24\3\24\3"+
		"\24\5\24\u00ce\n\24\3\24\5\24\u00d1\n\24\3\25\3\25\3\25\3\25\5\25\u00d7"+
		"\n\25\3\25\3\25\3\25\5\25\u00dc\n\25\7\25\u00de\n\25\f\25\16\25\u00e1"+
		"\13\25\3\25\3\25\3\26\3\26\3\26\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27"+
		"\3\27\3\27\3\27\6\27\u00f3\n\27\r\27\16\27\u00f4\3\27\3\27\3\27\3\27\3"+
		"\27\3\27\5\27\u00fd\n\27\3\27\3\27\3\27\3\27\7\27\u0103\n\27\f\27\16\27"+
		"\u0106\13\27\3\30\3\30\3\30\3\30\3\30\3\30\3\31\3\31\3\31\3\31\3\31\3"+
		"\31\5\31\u0114\n\31\3\31\3\31\3\31\3\31\3\31\3\31\7\31\u011c\n\31\f\31"+
		"\16\31\u011f\13\31\3\32\3\32\3\32\3\33\3\33\3\34\3\34\3\35\3\35\3\36\3"+
		"\36\3\37\3\37\3 \5 \u012f\n \3 \3 \3 \2\4,\60!\2\4\6\b\n\f\16\20\22\24"+
		"\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>\2\7\3\2\t\n\3\2\13\r\3\2\16\21"+
		"\3\2\16\17\3\2\22\23\u0137\2A\3\2\2\2\4G\3\2\2\2\6I\3\2\2\2\b[\3\2\2\2"+
		"\n`\3\2\2\2\fd\3\2\2\2\16f\3\2\2\2\20n\3\2\2\2\22r\3\2\2\2\24t\3\2\2\2"+
		"\26y\3\2\2\2\30\u0085\3\2\2\2\32\u0087\3\2\2\2\34\u008f\3\2\2\2\36\u0099"+
		"\3\2\2\2 \u009b\3\2\2\2\"\u00a6\3\2\2\2$\u00ae\3\2\2\2&\u00d0\3\2\2\2"+
		"(\u00d2\3\2\2\2*\u00e4\3\2\2\2,\u00fc\3\2\2\2.\u0107\3\2\2\2\60\u0113"+
		"\3\2\2\2\62\u0120\3\2\2\2\64\u0123\3\2\2\2\66\u0125\3\2\2\28\u0127\3\2"+
		"\2\2:\u0129\3\2\2\2<\u012b\3\2\2\2>\u012e\3\2\2\2@B\5\4\3\2A@\3\2\2\2"+
		"BC\3\2\2\2CA\3\2\2\2CD\3\2\2\2D\3\3\2\2\2EH\5\22\n\2FH\5\36\20\2GE\3\2"+
		"\2\2GF\3\2\2\2H\5\3\2\2\2IJ\7\24\2\2J\7\3\2\2\2KL\5\6\4\2LM\7\32\2\2M"+
		"R\5\f\7\2NO\7\27\2\2OQ\5\f\7\2PN\3\2\2\2QT\3\2\2\2RP\3\2\2\2RS\3\2\2\2"+
		"SU\3\2\2\2TR\3\2\2\2UV\7\33\2\2V\\\3\2\2\2WX\5\f\7\2XY\5\64\33\2YZ\5\f"+
		"\7\2Z\\\3\2\2\2[K\3\2\2\2[W\3\2\2\2\\\t\3\2\2\2]a\5\b\5\2^_\7\4\2\2_a"+
		"\5\n\6\2`]\3\2\2\2`^\3\2\2\2a\13\3\2\2\2be\5\16\b\2ce\5\20\t\2db\3\2\2"+
		"\2dc\3\2\2\2e\r\3\2\2\2fg\7\24\2\2g\17\3\2\2\2hi\7 \2\2ij\7\24\2\2jo\7"+
		" \2\2kl\7!\2\2lm\7\24\2\2mo\7!\2\2nh\3\2\2\2nk\3\2\2\2o\21\3\2\2\2ps\5"+
		"\24\13\2qs\5\26\f\2rp\3\2\2\2rq\3\2\2\2s\23\3\2\2\2tu\5\62\32\2uw\5\30"+
		"\r\2vx\7\3\2\2wv\3\2\2\2wx\3\2\2\2x\25\3\2\2\2yz\5\30\r\2z{\7\26\2\2{"+
		"\27\3\2\2\2|\u0086\5\32\16\2}~\5\32\16\2~\177\7\b\2\2\177\u0080\5\34\17"+
		"\2\u0080\u0086\3\2\2\2\u0081\u0082\5\34\17\2\u0082\u0083\7\7\2\2\u0083"+
		"\u0084\5\32\16\2\u0084\u0086\3\2\2\2\u0085|\3\2\2\2\u0085}\3\2\2\2\u0085"+
		"\u0081\3\2\2\2\u0086\31\3\2\2\2\u0087\u008c\5\n\6\2\u0088\u0089\7\6\2"+
		"\2\u0089\u008b\5\n\6\2\u008a\u0088\3\2\2\2\u008b\u008e\3\2\2\2\u008c\u008a"+
		"\3\2\2\2\u008c\u008d\3\2\2\2\u008d\33\3\2\2\2\u008e\u008c\3\2\2\2\u008f"+
		"\u0094\5\n\6\2\u0090\u0091\7\5\2\2\u0091\u0093\5\n\6\2\u0092\u0090\3\2"+
		"\2\2\u0093\u0096\3\2\2\2\u0094\u0092\3\2\2\2\u0094\u0095\3\2\2\2\u0095"+
		"\35\3\2\2\2\u0096\u0094\3\2\2\2\u0097\u009a\5 \21\2\u0098\u009a\5\"\22"+
		"\2\u0099\u0097\3\2\2\2\u0099\u0098\3\2\2\2\u009a\37\3\2\2\2\u009b\u009c"+
		"\5\62\32\2\u009c\u009e\5$\23\2\u009d\u009f\7\3\2\2\u009e\u009d\3\2\2\2"+
		"\u009e\u009f\3\2\2\2\u009f\u00a3\3\2\2\2\u00a0\u00a2\5.\30\2\u00a1\u00a0"+
		"\3\2\2\2\u00a2\u00a5\3\2\2\2\u00a3\u00a1\3\2\2\2\u00a3\u00a4\3\2\2\2\u00a4"+
		"!\3\2\2\2\u00a5\u00a3\3\2\2\2\u00a6\u00a7\5$\23\2\u00a7\u00ab\7\26\2\2"+
		"\u00a8\u00aa\5.\30\2\u00a9\u00a8\3\2\2\2\u00aa\u00ad\3\2\2\2\u00ab\u00a9"+
		"\3\2\2\2\u00ab\u00ac\3\2\2\2\u00ac#\3\2\2\2\u00ad\u00ab\3\2\2\2\u00ae"+
		"\u00b4\5&\24\2\u00af\u00b0\5:\36\2\u00b0\u00b1\5&\24\2\u00b1\u00b3\3\2"+
		"\2\2\u00b2\u00af\3\2\2\2\u00b3\u00b6\3\2\2\2\u00b4\u00b2\3\2\2\2\u00b4"+
		"\u00b5\3\2\2\2\u00b5\u00b7\3\2\2\2\u00b6\u00b4\3\2\2\2\u00b7\u00b8\5\66"+
		"\34\2\u00b8\u00be\5&\24\2\u00b9\u00ba\5:\36\2\u00ba\u00bb\5&\24\2\u00bb"+
		"\u00bd\3\2\2\2\u00bc\u00b9\3\2\2\2\u00bd\u00c0\3\2\2\2\u00be\u00bc\3\2"+
		"\2\2\u00be\u00bf\3\2\2\2\u00bf%\3\2\2\2\u00c0\u00be\3\2\2\2\u00c1\u00c3"+
		"\5,\27\2\u00c2\u00c4\7\20\2\2\u00c3\u00c2\3\2\2\2\u00c3\u00c4\3\2\2\2"+
		"\u00c4\u00c6\3\2\2\2\u00c5\u00c1\3\2\2\2\u00c5\u00c6\3\2\2\2\u00c6\u00c9"+
		"\3\2\2\2\u00c7\u00ca\5(\25\2\u00c8\u00ca\5\b\5\2\u00c9\u00c7\3\2\2\2\u00c9"+
		"\u00c8\3\2\2\2\u00ca\u00cd\3\2\2\2\u00cb\u00cc\7\21\2\2\u00cc\u00ce\5"+
		",\27\2\u00cd\u00cb\3\2\2\2\u00cd\u00ce\3\2\2\2\u00ce\u00d1\3\2\2\2\u00cf"+
		"\u00d1\5,\27\2\u00d0\u00c5\3\2\2\2\u00d0\u00cf\3\2\2\2\u00d1\'\3\2\2\2"+
		"\u00d2\u00d3\5\6\4\2\u00d3\u00d6\7\32\2\2\u00d4\u00d7\5*\26\2\u00d5\u00d7"+
		"\5\f\7\2\u00d6\u00d4\3\2\2\2\u00d6\u00d5\3\2\2\2\u00d7\u00df\3\2\2\2\u00d8"+
		"\u00db\7\27\2\2\u00d9\u00dc\5*\26\2\u00da\u00dc\5\f\7\2\u00db\u00d9\3"+
		"\2\2\2\u00db\u00da\3\2\2\2\u00dc\u00de\3\2\2\2\u00dd\u00d8\3\2\2\2\u00de"+
		"\u00e1\3\2\2\2\u00df\u00dd\3\2\2\2\u00df\u00e0\3\2\2\2\u00e0\u00e2\3\2"+
		"\2\2\u00e1\u00df\3\2\2\2\u00e2\u00e3\7\33\2\2\u00e3)\3\2\2\2\u00e4\u00e5"+
		"\7\16\2\2\u00e5\u00e6\7\24\2\2\u00e6+\3\2\2\2\u00e7\u00e8\b\27\1\2\u00e8"+
		"\u00fd\5> \2\u00e9\u00ea\7\31\2\2\u00ea\u00eb\5\16\b\2\u00eb\u00ec\7\31"+
		"\2\2\u00ec\u00fd\3\2\2\2\u00ed\u00ee\5<\37\2\u00ee\u00ef\7\36\2\2\u00ef"+
		"\u00f2\5,\27\2\u00f0\u00f1\7\27\2\2\u00f1\u00f3\5,\27\2\u00f2\u00f0\3"+
		"\2\2\2\u00f3\u00f4\3\2\2\2\u00f4\u00f2\3\2\2\2\u00f4\u00f5\3\2\2\2\u00f5"+
		"\u00f6\3\2\2\2\u00f6\u00f7\7\37\2\2\u00f7\u00fd\3\2\2\2\u00f8\u00f9\7"+
		"\32\2\2\u00f9\u00fa\5,\27\2\u00fa\u00fb\7\33\2\2\u00fb\u00fd\3\2\2\2\u00fc"+
		"\u00e7\3\2\2\2\u00fc\u00e9\3\2\2\2\u00fc\u00ed\3\2\2\2\u00fc\u00f8\3\2"+
		"\2\2\u00fd\u0104\3\2\2\2\u00fe\u00ff\f\5\2\2\u00ff\u0100\58\35\2\u0100"+
		"\u0101\5,\27\6\u0101\u0103\3\2\2\2\u0102\u00fe\3\2\2\2\u0103\u0106\3\2"+
		"\2\2\u0104\u0102\3\2\2\2\u0104\u0105\3\2\2\2\u0105-\3\2\2\2\u0106\u0104"+
		"\3\2\2\2\u0107\u0108\7\34\2\2\u0108\u0109\5\16\b\2\u0109\u010a\7\30\2"+
		"\2\u010a\u010b\5\60\31\2\u010b\u010c\7\35\2\2\u010c/\3\2\2\2\u010d\u010e"+
		"\b\31\1\2\u010e\u0114\5\n\6\2\u010f\u0110\7\32\2\2\u0110\u0111\5\60\31"+
		"\2\u0111\u0112\7\33\2\2\u0112\u0114\3\2\2\2\u0113\u010d\3\2\2\2\u0113"+
		"\u010f\3\2\2\2\u0114\u011d\3\2\2\2\u0115\u0116\f\4\2\2\u0116\u0117\7\6"+
		"\2\2\u0117\u011c\5\60\31\5\u0118\u0119\f\3\2\2\u0119\u011a\7\5\2\2\u011a"+
		"\u011c\5\60\31\4\u011b\u0115\3\2\2\2\u011b\u0118\3\2\2\2\u011c\u011f\3"+
		"\2\2\2\u011d\u011b\3\2\2\2\u011d\u011e\3\2\2\2\u011e\61\3\2\2\2\u011f"+
		"\u011d\3\2\2\2\u0120\u0121\7\25\2\2\u0121\u0122\7\30\2\2\u0122\63\3\2"+
		"\2\2\u0123\u0124\t\2\2\2\u0124\65\3\2\2\2\u0125\u0126\t\3\2\2\u0126\67"+
		"\3\2\2\2\u0127\u0128\t\4\2\2\u01289\3\2\2\2\u0129\u012a\t\5\2\2\u012a"+
		";\3\2\2\2\u012b\u012c\t\6\2\2\u012c=\3\2\2\2\u012d\u012f\7\17\2\2\u012e"+
		"\u012d\3\2\2\2\u012e\u012f\3\2\2\2\u012f\u0130\3\2\2\2\u0130\u0131\7\25"+
		"\2\2\u0131?\3\2\2\2#CGR[`dnrw\u0085\u008c\u0094\u0099\u009e\u00a3\u00ab"+
		"\u00b4\u00be\u00c3\u00c5\u00c9\u00cd\u00d0\u00d6\u00db\u00df\u00f4\u00fc"+
		"\u0104\u0113\u011b\u011d\u012e";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}