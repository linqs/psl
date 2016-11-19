// Generated from PSL.g4 by ANTLR 4.5.3
package org.linqs.psl.parser.antlr;
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
		T__0=1, T__1=2, T__2=3, T__3=4, EXPONENT_EXPRESSION=5, LESS_THAN_EQUAL=6, 
		GREATER_THAN_EQUAL=7, EQUAL=8, PLUS=9, MINUS=10, MULT=11, DIV=12, MAX=13, 
		MIN=14, IDENTIFIER=15, NONNEGATIVE_NUMBER=16, PERIOD=17, COMMA=18, COLON=19, 
		NEGATION=20, AMPERSAND=21, PIPE=22, LPAREN=23, RPAREN=24, LBRACE=25, RBRACE=26, 
		LBRACKET=27, RBRACKET=28, SINGLE_QUOTE=29, DOUBLE_QUOTE=30, WS=31, COMMENT=32, 
		LINE_COMMENT=33, PYTHON_COMMENT=34;
	public static final int
		RULE_program = 0, RULE_pslRule = 1, RULE_predicate = 2, RULE_atom = 3, 
		RULE_literal = 4, RULE_term = 5, RULE_variable = 6, RULE_constant = 7, 
		RULE_logicalRule = 8, RULE_weightedLogicalRule = 9, RULE_unweightedLogicalRule = 10, 
		RULE_logicalRuleExpression = 11, RULE_disjunctiveClause = 12, RULE_conjunctiveClause = 13, 
		RULE_arithmeticRule = 14, RULE_weightedArithmeticRule = 15, RULE_unweightedArithmeticRule = 16, 
		RULE_arithmeticRuleExpression = 17, RULE_arithmeticRuleOperand = 18, RULE_summationAtom = 19, 
		RULE_summationVariable = 20, RULE_coefficient = 21, RULE_selectStatement = 22, 
		RULE_boolExpression = 23, RULE_weightExpression = 24, RULE_not = 25, RULE_and = 26, 
		RULE_or = 27, RULE_then = 28, RULE_impliedBy = 29, RULE_termOperator = 30, 
		RULE_termEqual = 31, RULE_notEqual = 32, RULE_arithmeticRuleRelation = 33, 
		RULE_arithmeticOperator = 34, RULE_linearOperator = 35, RULE_coeffOperator = 36, 
		RULE_number = 37;
	public static final String[] ruleNames = {
		"program", "pslRule", "predicate", "atom", "literal", "term", "variable", 
		"constant", "logicalRule", "weightedLogicalRule", "unweightedLogicalRule", 
		"logicalRuleExpression", "disjunctiveClause", "conjunctiveClause", "arithmeticRule", 
		"weightedArithmeticRule", "unweightedArithmeticRule", "arithmeticRuleExpression", 
		"arithmeticRuleOperand", "summationAtom", "summationVariable", "coefficient", 
		"selectStatement", "boolExpression", "weightExpression", "not", "and", 
		"or", "then", "impliedBy", "termOperator", "termEqual", "notEqual", "arithmeticRuleRelation", 
		"arithmeticOperator", "linearOperator", "coeffOperator", "number"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'>>'", "'->'", "'<<'", "'<-'", null, "'<='", "'>='", "'='", "'+'", 
		"'-'", "'*'", "'/'", "'@Max'", "'@Min'", null, null, "'.'", "','", "':'", 
		null, "'&'", "'|'", "'('", "')'", "'{'", "'}'", "'['", "']'", "'''", "'\"'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, "EXPONENT_EXPRESSION", "LESS_THAN_EQUAL", 
		"GREATER_THAN_EQUAL", "EQUAL", "PLUS", "MINUS", "MULT", "DIV", "MAX", 
		"MIN", "IDENTIFIER", "NONNEGATIVE_NUMBER", "PERIOD", "COMMA", "COLON", 
		"NEGATION", "AMPERSAND", "PIPE", "LPAREN", "RPAREN", "LBRACE", "RBRACE", 
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
			setState(77); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(76);
				pslRule();
				}
				}
				setState(79); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << MINUS) | (1L << MAX) | (1L << MIN) | (1L << IDENTIFIER) | (1L << NONNEGATIVE_NUMBER) | (1L << NEGATION) | (1L << PIPE) | (1L << LPAREN) | (1L << SINGLE_QUOTE) | (1L << DOUBLE_QUOTE))) != 0) );
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
			setState(83);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(81);
				logicalRule();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(82);
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
			setState(85);
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
			setState(103);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(87);
				predicate();
				setState(88);
				match(LPAREN);
				setState(89);
				term();
				setState(94);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(90);
					match(COMMA);
					setState(91);
					term();
					}
					}
					setState(96);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(97);
				match(RPAREN);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(99);
				term();
				setState(100);
				termOperator();
				setState(101);
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
		public NotContext not() {
			return getRuleContext(NotContext.class,0);
		}
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
			setState(109);
			switch (_input.LA(1)) {
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 1);
				{
				setState(105);
				atom();
				}
				break;
			case NEGATION:
				enterOuterAlt(_localctx, 2);
				{
				setState(106);
				not();
				setState(107);
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
			setState(113);
			switch (_input.LA(1)) {
			case IDENTIFIER:
				enterOuterAlt(_localctx, 1);
				{
				setState(111);
				variable();
				}
				break;
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(112);
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
			setState(115);
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
			setState(123);
			switch (_input.LA(1)) {
			case SINGLE_QUOTE:
				enterOuterAlt(_localctx, 1);
				{
				setState(117);
				match(SINGLE_QUOTE);
				setState(118);
				match(IDENTIFIER);
				setState(119);
				match(SINGLE_QUOTE);
				}
				break;
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(120);
				match(DOUBLE_QUOTE);
				setState(121);
				match(IDENTIFIER);
				setState(122);
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
			setState(127);
			switch (_input.LA(1)) {
			case NONNEGATIVE_NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(125);
				weightedLogicalRule();
				}
				break;
			case IDENTIFIER:
			case NEGATION:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(126);
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
			setState(129);
			weightExpression();
			setState(130);
			logicalRuleExpression();
			setState(132);
			_la = _input.LA(1);
			if (_la==EXPONENT_EXPRESSION) {
				{
				setState(131);
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
			setState(134);
			logicalRuleExpression();
			setState(135);
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
		public ImpliedByContext impliedBy() {
			return getRuleContext(ImpliedByContext.class,0);
		}
		public ConjunctiveClauseContext conjunctiveClause() {
			return getRuleContext(ConjunctiveClauseContext.class,0);
		}
		public ThenContext then() {
			return getRuleContext(ThenContext.class,0);
		}
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
			setState(146);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,9,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(137);
				disjunctiveClause();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(138);
				disjunctiveClause();
				setState(139);
				impliedBy();
				setState(140);
				conjunctiveClause();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(142);
				conjunctiveClause();
				setState(143);
				then();
				setState(144);
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
		public List<OrContext> or() {
			return getRuleContexts(OrContext.class);
		}
		public OrContext or(int i) {
			return getRuleContext(OrContext.class,i);
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
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(148);
			literal();
			setState(154);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(149);
					or();
					setState(150);
					literal();
					}
					} 
				}
				setState(156);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
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
		public List<AndContext> and() {
			return getRuleContexts(AndContext.class);
		}
		public AndContext and(int i) {
			return getRuleContext(AndContext.class,i);
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
			setState(157);
			literal();
			setState(163);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AMPERSAND) {
				{
				{
				setState(158);
				and();
				setState(159);
				literal();
				}
				}
				setState(165);
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
			setState(168);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,12,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(166);
				weightedArithmeticRule();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(167);
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
			setState(170);
			weightExpression();
			setState(171);
			arithmeticRuleExpression();
			setState(173);
			_la = _input.LA(1);
			if (_la==EXPONENT_EXPRESSION) {
				{
				setState(172);
				match(EXPONENT_EXPRESSION);
				}
			}

			setState(178);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LBRACE) {
				{
				{
				setState(175);
				selectStatement();
				}
				}
				setState(180);
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
			setState(181);
			arithmeticRuleExpression();
			setState(182);
			match(PERIOD);
			setState(186);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LBRACE) {
				{
				{
				setState(183);
				selectStatement();
				}
				}
				setState(188);
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
			setState(189);
			arithmeticRuleOperand();
			setState(195);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==PLUS || _la==MINUS) {
				{
				{
				setState(190);
				linearOperator();
				setState(191);
				arithmeticRuleOperand();
				}
				}
				setState(197);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(198);
			arithmeticRuleRelation();
			setState(199);
			arithmeticRuleOperand();
			setState(205);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(200);
					linearOperator();
					setState(201);
					arithmeticRuleOperand();
					}
					} 
				}
				setState(207);
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
			setState(223);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,22,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(212);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << MINUS) | (1L << MAX) | (1L << MIN) | (1L << NONNEGATIVE_NUMBER) | (1L << PIPE) | (1L << LPAREN))) != 0)) {
					{
					setState(208);
					coefficient(0);
					setState(210);
					_la = _input.LA(1);
					if (_la==MULT) {
						{
						setState(209);
						match(MULT);
						}
					}

					}
				}

				setState(216);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
				case 1:
					{
					setState(214);
					summationAtom();
					}
					break;
				case 2:
					{
					setState(215);
					atom();
					}
					break;
				}
				setState(220);
				_la = _input.LA(1);
				if (_la==DIV) {
					{
					setState(218);
					match(DIV);
					setState(219);
					coefficient(0);
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(222);
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
			setState(225);
			predicate();
			setState(226);
			match(LPAREN);
			setState(229);
			switch (_input.LA(1)) {
			case PLUS:
				{
				setState(227);
				summationVariable();
				}
				break;
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				{
				setState(228);
				term();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(238);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(231);
				match(COMMA);
				setState(234);
				switch (_input.LA(1)) {
				case PLUS:
					{
					setState(232);
					summationVariable();
					}
					break;
				case IDENTIFIER:
				case SINGLE_QUOTE:
				case DOUBLE_QUOTE:
					{
					setState(233);
					term();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				}
				setState(240);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(241);
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
			setState(243);
			match(PLUS);
			setState(244);
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
		public TerminalNode COMMA() { return getToken(PSLParser.COMMA, 0); }
		public TerminalNode RBRACKET() { return getToken(PSLParser.RBRACKET, 0); }
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
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(263);
			switch (_input.LA(1)) {
			case MINUS:
			case NONNEGATIVE_NUMBER:
				{
				setState(247);
				number();
				}
				break;
			case PIPE:
				{
				setState(248);
				match(PIPE);
				setState(249);
				variable();
				setState(250);
				match(PIPE);
				}
				break;
			case MAX:
			case MIN:
				{
				setState(252);
				coeffOperator();
				setState(253);
				match(LBRACKET);
				setState(254);
				coefficient(0);
				setState(255);
				match(COMMA);
				setState(256);
				coefficient(0);
				setState(257);
				match(RBRACKET);
				}
				break;
			case LPAREN:
				{
				setState(259);
				match(LPAREN);
				setState(260);
				coefficient(0);
				setState(261);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(271);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,27,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new CoefficientContext(_parentctx, _parentState);
					pushNewRecursionContext(_localctx, _startState, RULE_coefficient);
					setState(265);
					if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
					setState(266);
					arithmeticOperator();
					setState(267);
					coefficient(4);
					}
					} 
				}
				setState(273);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,27,_ctx);
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
			setState(274);
			match(LBRACE);
			setState(275);
			variable();
			setState(276);
			match(COLON);
			setState(277);
			boolExpression(0);
			setState(278);
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
		public OrContext or() {
			return getRuleContext(OrContext.class,0);
		}
		public AndContext and() {
			return getRuleContext(AndContext.class,0);
		}
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
			setState(286);
			switch (_input.LA(1)) {
			case IDENTIFIER:
			case NEGATION:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				{
				setState(281);
				literal();
				}
				break;
			case LPAREN:
				{
				setState(282);
				match(LPAREN);
				setState(283);
				boolExpression(0);
				setState(284);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(298);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,30,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(296);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,29,_ctx) ) {
					case 1:
						{
						_localctx = new BoolExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_boolExpression);
						setState(288);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(289);
						or();
						setState(290);
						boolExpression(3);
						}
						break;
					case 2:
						{
						_localctx = new BoolExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_boolExpression);
						setState(292);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(293);
						and();
						setState(294);
						boolExpression(2);
						}
						break;
					}
					} 
				}
				setState(300);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,30,_ctx);
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
			setState(301);
			match(NONNEGATIVE_NUMBER);
			setState(302);
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

	public static class NotContext extends ParserRuleContext {
		public TerminalNode NEGATION() { return getToken(PSLParser.NEGATION, 0); }
		public NotContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_not; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterNot(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitNot(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitNot(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NotContext not() throws RecognitionException {
		NotContext _localctx = new NotContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_not);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(304);
			match(NEGATION);
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

	public static class AndContext extends ParserRuleContext {
		public List<TerminalNode> AMPERSAND() { return getTokens(PSLParser.AMPERSAND); }
		public TerminalNode AMPERSAND(int i) {
			return getToken(PSLParser.AMPERSAND, i);
		}
		public AndContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_and; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterAnd(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitAnd(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitAnd(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AndContext and() throws RecognitionException {
		AndContext _localctx = new AndContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_and);
		try {
			setState(309);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,31,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(306);
				match(AMPERSAND);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(307);
				match(AMPERSAND);
				setState(308);
				match(AMPERSAND);
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

	public static class OrContext extends ParserRuleContext {
		public List<TerminalNode> PIPE() { return getTokens(PSLParser.PIPE); }
		public TerminalNode PIPE(int i) {
			return getToken(PSLParser.PIPE, i);
		}
		public OrContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_or; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterOr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitOr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitOr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final OrContext or() throws RecognitionException {
		OrContext _localctx = new OrContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_or);
		try {
			setState(314);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,32,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(311);
				match(PIPE);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(312);
				match(PIPE);
				setState(313);
				match(PIPE);
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

	public static class ThenContext extends ParserRuleContext {
		public ThenContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_then; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterThen(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitThen(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitThen(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ThenContext then() throws RecognitionException {
		ThenContext _localctx = new ThenContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_then);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(316);
			_la = _input.LA(1);
			if ( !(_la==T__0 || _la==T__1) ) {
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

	public static class ImpliedByContext extends ParserRuleContext {
		public ImpliedByContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_impliedBy; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterImpliedBy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitImpliedBy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitImpliedBy(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImpliedByContext impliedBy() throws RecognitionException {
		ImpliedByContext _localctx = new ImpliedByContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_impliedBy);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(318);
			_la = _input.LA(1);
			if ( !(_la==T__2 || _la==T__3) ) {
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

	public static class TermOperatorContext extends ParserRuleContext {
		public TermEqualContext termEqual() {
			return getRuleContext(TermEqualContext.class,0);
		}
		public NotEqualContext notEqual() {
			return getRuleContext(NotEqualContext.class,0);
		}
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
		enterRule(_localctx, 60, RULE_termOperator);
		try {
			setState(322);
			switch (_input.LA(1)) {
			case EQUAL:
				enterOuterAlt(_localctx, 1);
				{
				setState(320);
				termEqual();
				}
				break;
			case NEGATION:
				enterOuterAlt(_localctx, 2);
				{
				setState(321);
				notEqual();
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

	public static class TermEqualContext extends ParserRuleContext {
		public List<TerminalNode> EQUAL() { return getTokens(PSLParser.EQUAL); }
		public TerminalNode EQUAL(int i) {
			return getToken(PSLParser.EQUAL, i);
		}
		public TermEqualContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_termEqual; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterTermEqual(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitTermEqual(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitTermEqual(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TermEqualContext termEqual() throws RecognitionException {
		TermEqualContext _localctx = new TermEqualContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_termEqual);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(324);
			match(EQUAL);
			setState(325);
			match(EQUAL);
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

	public static class NotEqualContext extends ParserRuleContext {
		public TerminalNode NEGATION() { return getToken(PSLParser.NEGATION, 0); }
		public TerminalNode EQUAL() { return getToken(PSLParser.EQUAL, 0); }
		public NotEqualContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_notEqual; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterNotEqual(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitNotEqual(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitNotEqual(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NotEqualContext notEqual() throws RecognitionException {
		NotEqualContext _localctx = new NotEqualContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_notEqual);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(327);
			match(NEGATION);
			setState(328);
			match(EQUAL);
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
		enterRule(_localctx, 66, RULE_arithmeticRuleRelation);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(330);
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
		enterRule(_localctx, 68, RULE_arithmeticOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(332);
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
		enterRule(_localctx, 70, RULE_linearOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(334);
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
		enterRule(_localctx, 72, RULE_coeffOperator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(336);
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
		enterRule(_localctx, 74, RULE_number);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(339);
			_la = _input.LA(1);
			if (_la==MINUS) {
				{
				setState(338);
				match(MINUS);
				}
			}

			setState(341);
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
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\3$\u015a\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\3\2\6\2P\n\2\r\2\16\2Q\3\3"+
		"\3\3\5\3V\n\3\3\4\3\4\3\5\3\5\3\5\3\5\3\5\7\5_\n\5\f\5\16\5b\13\5\3\5"+
		"\3\5\3\5\3\5\3\5\3\5\5\5j\n\5\3\6\3\6\3\6\3\6\5\6p\n\6\3\7\3\7\5\7t\n"+
		"\7\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\t\5\t~\n\t\3\n\3\n\5\n\u0082\n\n\3\13"+
		"\3\13\3\13\5\13\u0087\n\13\3\f\3\f\3\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r"+
		"\3\r\5\r\u0095\n\r\3\16\3\16\3\16\3\16\7\16\u009b\n\16\f\16\16\16\u009e"+
		"\13\16\3\17\3\17\3\17\3\17\7\17\u00a4\n\17\f\17\16\17\u00a7\13\17\3\20"+
		"\3\20\5\20\u00ab\n\20\3\21\3\21\3\21\5\21\u00b0\n\21\3\21\7\21\u00b3\n"+
		"\21\f\21\16\21\u00b6\13\21\3\22\3\22\3\22\7\22\u00bb\n\22\f\22\16\22\u00be"+
		"\13\22\3\23\3\23\3\23\3\23\7\23\u00c4\n\23\f\23\16\23\u00c7\13\23\3\23"+
		"\3\23\3\23\3\23\3\23\7\23\u00ce\n\23\f\23\16\23\u00d1\13\23\3\24\3\24"+
		"\5\24\u00d5\n\24\5\24\u00d7\n\24\3\24\3\24\5\24\u00db\n\24\3\24\3\24\5"+
		"\24\u00df\n\24\3\24\5\24\u00e2\n\24\3\25\3\25\3\25\3\25\5\25\u00e8\n\25"+
		"\3\25\3\25\3\25\5\25\u00ed\n\25\7\25\u00ef\n\25\f\25\16\25\u00f2\13\25"+
		"\3\25\3\25\3\26\3\26\3\26\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27"+
		"\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\5\27\u010a\n\27\3\27\3\27\3\27"+
		"\3\27\7\27\u0110\n\27\f\27\16\27\u0113\13\27\3\30\3\30\3\30\3\30\3\30"+
		"\3\30\3\31\3\31\3\31\3\31\3\31\3\31\5\31\u0121\n\31\3\31\3\31\3\31\3\31"+
		"\3\31\3\31\3\31\3\31\7\31\u012b\n\31\f\31\16\31\u012e\13\31\3\32\3\32"+
		"\3\32\3\33\3\33\3\34\3\34\3\34\5\34\u0138\n\34\3\35\3\35\3\35\5\35\u013d"+
		"\n\35\3\36\3\36\3\37\3\37\3 \3 \5 \u0145\n \3!\3!\3!\3\"\3\"\3\"\3#\3"+
		"#\3$\3$\3%\3%\3&\3&\3\'\5\'\u0156\n\'\3\'\3\'\3\'\2\4,\60(\2\4\6\b\n\f"+
		"\16\20\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@BDFHJL\2\b\3\2\3"+
		"\4\3\2\5\6\3\2\b\n\3\2\13\16\3\2\13\f\3\2\17\20\u0159\2O\3\2\2\2\4U\3"+
		"\2\2\2\6W\3\2\2\2\bi\3\2\2\2\no\3\2\2\2\fs\3\2\2\2\16u\3\2\2\2\20}\3\2"+
		"\2\2\22\u0081\3\2\2\2\24\u0083\3\2\2\2\26\u0088\3\2\2\2\30\u0094\3\2\2"+
		"\2\32\u0096\3\2\2\2\34\u009f\3\2\2\2\36\u00aa\3\2\2\2 \u00ac\3\2\2\2\""+
		"\u00b7\3\2\2\2$\u00bf\3\2\2\2&\u00e1\3\2\2\2(\u00e3\3\2\2\2*\u00f5\3\2"+
		"\2\2,\u0109\3\2\2\2.\u0114\3\2\2\2\60\u0120\3\2\2\2\62\u012f\3\2\2\2\64"+
		"\u0132\3\2\2\2\66\u0137\3\2\2\28\u013c\3\2\2\2:\u013e\3\2\2\2<\u0140\3"+
		"\2\2\2>\u0144\3\2\2\2@\u0146\3\2\2\2B\u0149\3\2\2\2D\u014c\3\2\2\2F\u014e"+
		"\3\2\2\2H\u0150\3\2\2\2J\u0152\3\2\2\2L\u0155\3\2\2\2NP\5\4\3\2ON\3\2"+
		"\2\2PQ\3\2\2\2QO\3\2\2\2QR\3\2\2\2R\3\3\2\2\2SV\5\22\n\2TV\5\36\20\2U"+
		"S\3\2\2\2UT\3\2\2\2V\5\3\2\2\2WX\7\21\2\2X\7\3\2\2\2YZ\5\6\4\2Z[\7\31"+
		"\2\2[`\5\f\7\2\\]\7\24\2\2]_\5\f\7\2^\\\3\2\2\2_b\3\2\2\2`^\3\2\2\2`a"+
		"\3\2\2\2ac\3\2\2\2b`\3\2\2\2cd\7\32\2\2dj\3\2\2\2ef\5\f\7\2fg\5> \2gh"+
		"\5\f\7\2hj\3\2\2\2iY\3\2\2\2ie\3\2\2\2j\t\3\2\2\2kp\5\b\5\2lm\5\64\33"+
		"\2mn\5\n\6\2np\3\2\2\2ok\3\2\2\2ol\3\2\2\2p\13\3\2\2\2qt\5\16\b\2rt\5"+
		"\20\t\2sq\3\2\2\2sr\3\2\2\2t\r\3\2\2\2uv\7\21\2\2v\17\3\2\2\2wx\7\37\2"+
		"\2xy\7\21\2\2y~\7\37\2\2z{\7 \2\2{|\7\21\2\2|~\7 \2\2}w\3\2\2\2}z\3\2"+
		"\2\2~\21\3\2\2\2\177\u0082\5\24\13\2\u0080\u0082\5\26\f\2\u0081\177\3"+
		"\2\2\2\u0081\u0080\3\2\2\2\u0082\23\3\2\2\2\u0083\u0084\5\62\32\2\u0084"+
		"\u0086\5\30\r\2\u0085\u0087\7\7\2\2\u0086\u0085\3\2\2\2\u0086\u0087\3"+
		"\2\2\2\u0087\25\3\2\2\2\u0088\u0089\5\30\r\2\u0089\u008a\7\23\2\2\u008a"+
		"\27\3\2\2\2\u008b\u0095\5\32\16\2\u008c\u008d\5\32\16\2\u008d\u008e\5"+
		"<\37\2\u008e\u008f\5\34\17\2\u008f\u0095\3\2\2\2\u0090\u0091\5\34\17\2"+
		"\u0091\u0092\5:\36\2\u0092\u0093\5\32\16\2\u0093\u0095\3\2\2\2\u0094\u008b"+
		"\3\2\2\2\u0094\u008c\3\2\2\2\u0094\u0090\3\2\2\2\u0095\31\3\2\2\2\u0096"+
		"\u009c\5\n\6\2\u0097\u0098\58\35\2\u0098\u0099\5\n\6\2\u0099\u009b\3\2"+
		"\2\2\u009a\u0097\3\2\2\2\u009b\u009e\3\2\2\2\u009c\u009a\3\2\2\2\u009c"+
		"\u009d\3\2\2\2\u009d\33\3\2\2\2\u009e\u009c\3\2\2\2\u009f\u00a5\5\n\6"+
		"\2\u00a0\u00a1\5\66\34\2\u00a1\u00a2\5\n\6\2\u00a2\u00a4\3\2\2\2\u00a3"+
		"\u00a0\3\2\2\2\u00a4\u00a7\3\2\2\2\u00a5\u00a3\3\2\2\2\u00a5\u00a6\3\2"+
		"\2\2\u00a6\35\3\2\2\2\u00a7\u00a5\3\2\2\2\u00a8\u00ab\5 \21\2\u00a9\u00ab"+
		"\5\"\22\2\u00aa\u00a8\3\2\2\2\u00aa\u00a9\3\2\2\2\u00ab\37\3\2\2\2\u00ac"+
		"\u00ad\5\62\32\2\u00ad\u00af\5$\23\2\u00ae\u00b0\7\7\2\2\u00af\u00ae\3"+
		"\2\2\2\u00af\u00b0\3\2\2\2\u00b0\u00b4\3\2\2\2\u00b1\u00b3\5.\30\2\u00b2"+
		"\u00b1\3\2\2\2\u00b3\u00b6\3\2\2\2\u00b4\u00b2\3\2\2\2\u00b4\u00b5\3\2"+
		"\2\2\u00b5!\3\2\2\2\u00b6\u00b4\3\2\2\2\u00b7\u00b8\5$\23\2\u00b8\u00bc"+
		"\7\23\2\2\u00b9\u00bb\5.\30\2\u00ba\u00b9\3\2\2\2\u00bb\u00be\3\2\2\2"+
		"\u00bc\u00ba\3\2\2\2\u00bc\u00bd\3\2\2\2\u00bd#\3\2\2\2\u00be\u00bc\3"+
		"\2\2\2\u00bf\u00c5\5&\24\2\u00c0\u00c1\5H%\2\u00c1\u00c2\5&\24\2\u00c2"+
		"\u00c4\3\2\2\2\u00c3\u00c0\3\2\2\2\u00c4\u00c7\3\2\2\2\u00c5\u00c3\3\2"+
		"\2\2\u00c5\u00c6\3\2\2\2\u00c6\u00c8\3\2\2\2\u00c7\u00c5\3\2\2\2\u00c8"+
		"\u00c9\5D#\2\u00c9\u00cf\5&\24\2\u00ca\u00cb\5H%\2\u00cb\u00cc\5&\24\2"+
		"\u00cc\u00ce\3\2\2\2\u00cd\u00ca\3\2\2\2\u00ce\u00d1\3\2\2\2\u00cf\u00cd"+
		"\3\2\2\2\u00cf\u00d0\3\2\2\2\u00d0%\3\2\2\2\u00d1\u00cf\3\2\2\2\u00d2"+
		"\u00d4\5,\27\2\u00d3\u00d5\7\r\2\2\u00d4\u00d3\3\2\2\2\u00d4\u00d5\3\2"+
		"\2\2\u00d5\u00d7\3\2\2\2\u00d6\u00d2\3\2\2\2\u00d6\u00d7\3\2\2\2\u00d7"+
		"\u00da\3\2\2\2\u00d8\u00db\5(\25\2\u00d9\u00db\5\b\5\2\u00da\u00d8\3\2"+
		"\2\2\u00da\u00d9\3\2\2\2\u00db\u00de\3\2\2\2\u00dc\u00dd\7\16\2\2\u00dd"+
		"\u00df\5,\27\2\u00de\u00dc\3\2\2\2\u00de\u00df\3\2\2\2\u00df\u00e2\3\2"+
		"\2\2\u00e0\u00e2\5,\27\2\u00e1\u00d6\3\2\2\2\u00e1\u00e0\3\2\2\2\u00e2"+
		"\'\3\2\2\2\u00e3\u00e4\5\6\4\2\u00e4\u00e7\7\31\2\2\u00e5\u00e8\5*\26"+
		"\2\u00e6\u00e8\5\f\7\2\u00e7\u00e5\3\2\2\2\u00e7\u00e6\3\2\2\2\u00e8\u00f0"+
		"\3\2\2\2\u00e9\u00ec\7\24\2\2\u00ea\u00ed\5*\26\2\u00eb\u00ed\5\f\7\2"+
		"\u00ec\u00ea\3\2\2\2\u00ec\u00eb\3\2\2\2\u00ed\u00ef\3\2\2\2\u00ee\u00e9"+
		"\3\2\2\2\u00ef\u00f2\3\2\2\2\u00f0\u00ee\3\2\2\2\u00f0\u00f1\3\2\2\2\u00f1"+
		"\u00f3\3\2\2\2\u00f2\u00f0\3\2\2\2\u00f3\u00f4\7\32\2\2\u00f4)\3\2\2\2"+
		"\u00f5\u00f6\7\13\2\2\u00f6\u00f7\7\21\2\2\u00f7+\3\2\2\2\u00f8\u00f9"+
		"\b\27\1\2\u00f9\u010a\5L\'\2\u00fa\u00fb\7\30\2\2\u00fb\u00fc\5\16\b\2"+
		"\u00fc\u00fd\7\30\2\2\u00fd\u010a\3\2\2\2\u00fe\u00ff\5J&\2\u00ff\u0100"+
		"\7\35\2\2\u0100\u0101\5,\27\2\u0101\u0102\7\24\2\2\u0102\u0103\5,\27\2"+
		"\u0103\u0104\7\36\2\2\u0104\u010a\3\2\2\2\u0105\u0106\7\31\2\2\u0106\u0107"+
		"\5,\27\2\u0107\u0108\7\32\2\2\u0108\u010a\3\2\2\2\u0109\u00f8\3\2\2\2"+
		"\u0109\u00fa\3\2\2\2\u0109\u00fe\3\2\2\2\u0109\u0105\3\2\2\2\u010a\u0111"+
		"\3\2\2\2\u010b\u010c\f\5\2\2\u010c\u010d\5F$\2\u010d\u010e\5,\27\6\u010e"+
		"\u0110\3\2\2\2\u010f\u010b\3\2\2\2\u0110\u0113\3\2\2\2\u0111\u010f\3\2"+
		"\2\2\u0111\u0112\3\2\2\2\u0112-\3\2\2\2\u0113\u0111\3\2\2\2\u0114\u0115"+
		"\7\33\2\2\u0115\u0116\5\16\b\2\u0116\u0117\7\25\2\2\u0117\u0118\5\60\31"+
		"\2\u0118\u0119\7\34\2\2\u0119/\3\2\2\2\u011a\u011b\b\31\1\2\u011b\u0121"+
		"\5\n\6\2\u011c\u011d\7\31\2\2\u011d\u011e\5\60\31\2\u011e\u011f\7\32\2"+
		"\2\u011f\u0121\3\2\2\2\u0120\u011a\3\2\2\2\u0120\u011c\3\2\2\2\u0121\u012c"+
		"\3\2\2\2\u0122\u0123\f\4\2\2\u0123\u0124\58\35\2\u0124\u0125\5\60\31\5"+
		"\u0125\u012b\3\2\2\2\u0126\u0127\f\3\2\2\u0127\u0128\5\66\34\2\u0128\u0129"+
		"\5\60\31\4\u0129\u012b\3\2\2\2\u012a\u0122\3\2\2\2\u012a\u0126\3\2\2\2"+
		"\u012b\u012e\3\2\2\2\u012c\u012a\3\2\2\2\u012c\u012d\3\2\2\2\u012d\61"+
		"\3\2\2\2\u012e\u012c\3\2\2\2\u012f\u0130\7\22\2\2\u0130\u0131\7\25\2\2"+
		"\u0131\63\3\2\2\2\u0132\u0133\7\26\2\2\u0133\65\3\2\2\2\u0134\u0138\7"+
		"\27\2\2\u0135\u0136\7\27\2\2\u0136\u0138\7\27\2\2\u0137\u0134\3\2\2\2"+
		"\u0137\u0135\3\2\2\2\u0138\67\3\2\2\2\u0139\u013d\7\30\2\2\u013a\u013b"+
		"\7\30\2\2\u013b\u013d\7\30\2\2\u013c\u0139\3\2\2\2\u013c\u013a\3\2\2\2"+
		"\u013d9\3\2\2\2\u013e\u013f\t\2\2\2\u013f;\3\2\2\2\u0140\u0141\t\3\2\2"+
		"\u0141=\3\2\2\2\u0142\u0145\5@!\2\u0143\u0145\5B\"\2\u0144\u0142\3\2\2"+
		"\2\u0144\u0143\3\2\2\2\u0145?\3\2\2\2\u0146\u0147\7\n\2\2\u0147\u0148"+
		"\7\n\2\2\u0148A\3\2\2\2\u0149\u014a\7\26\2\2\u014a\u014b\7\n\2\2\u014b"+
		"C\3\2\2\2\u014c\u014d\t\4\2\2\u014dE\3\2\2\2\u014e\u014f\t\5\2\2\u014f"+
		"G\3\2\2\2\u0150\u0151\t\6\2\2\u0151I\3\2\2\2\u0152\u0153\t\7\2\2\u0153"+
		"K\3\2\2\2\u0154\u0156\7\f\2\2\u0155\u0154\3\2\2\2\u0155\u0156\3\2\2\2"+
		"\u0156\u0157\3\2\2\2\u0157\u0158\7\22\2\2\u0158M\3\2\2\2%QU`ios}\u0081"+
		"\u0086\u0094\u009c\u00a5\u00aa\u00af\u00b4\u00bc\u00c5\u00cf\u00d4\u00d6"+
		"\u00da\u00de\u00e1\u00e7\u00ec\u00f0\u0109\u0111\u0120\u012a\u012c\u0137"+
		"\u013c\u0144\u0155";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
