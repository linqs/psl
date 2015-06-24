// Generated from PSL.g4 by ANTLR 4.5
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
	static { RuntimeMetaData.checkVersion("4.5", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		EXPONENT=1, NOT=2, AND=3, OR=4, THEN=5, IMPLIED_BY=6, TERM_OPERATOR=7, 
		TERM_EQUAL=8, NOT_EQUAL=9, SYMMETRIC=10, ARITHMETIC_RULE_OPERATOR=11, 
		LESS_THAN_EQUAL=12, GREATER_THAN_EQUAL=13, EQUAL=14, ARITHMETIC_OPERATOR=15, 
		LINEAR_OPERATOR=16, PLUS=17, MINUS=18, MULT=19, DIV=20, COEFF_OPERATOR=21, 
		MAX=22, MIN=23, IDENTIFIER=24, NONNEGATIVE_NUMBER=25, NUMBER=26, PERIOD=27, 
		COMMA=28, COLON=29, PIPE=30, LPAREN=31, RPAREN=32, LBRACE=33, RBRACE=34, 
		LBRACKET=35, RBRACKET=36, SINGLE_QUOTE=37, DOUBLE_QUOTE=38, WS=39, COMMENT=40, 
		LINE_COMMENT=41, PYTHON_COMMENT=42;
	public static final int
		RULE_program = 0, RULE_psl_rule = 1, RULE_predicate = 2, RULE_atom = 3, 
		RULE_literal = 4, RULE_term = 5, RULE_variable = 6, RULE_constant = 7, 
		RULE_logical_rule = 8, RULE_weighted_logical_rule = 9, RULE_unweighted_logical_rule = 10, 
		RULE_logical_rule_expression = 11, RULE_disjunctive_clause = 12, RULE_conjunctive_clause = 13, 
		RULE_arithmetic_rule = 14, RULE_weighted_arithmetic_rule = 15, RULE_unweighted_arithmetic_rule = 16, 
		RULE_arithmetic_rule_expression = 17, RULE_arithmetic_rule_operand = 18, 
		RULE_sum_augmented_atom = 19, RULE_coefficient = 20, RULE_select_statement = 21, 
		RULE_bool_expression = 22, RULE_weight_expression = 23, RULE_exponent_expression = 24;
	public static final String[] ruleNames = {
		"program", "psl_rule", "predicate", "atom", "literal", "term", "variable", 
		"constant", "logical_rule", "weighted_logical_rule", "unweighted_logical_rule", 
		"logical_rule_expression", "disjunctive_clause", "conjunctive_clause", 
		"arithmetic_rule", "weighted_arithmetic_rule", "unweighted_arithmetic_rule", 
		"arithmetic_rule_expression", "arithmetic_rule_operand", "sum_augmented_atom", 
		"coefficient", "select_statement", "bool_expression", "weight_expression", 
		"exponent_expression"
	};

	private static final String[] _LITERAL_NAMES = {
		null, null, null, null, null, null, null, null, "'=='", "'!='", "'^'", 
		null, "'<='", "'>='", "'='", null, null, "'+'", "'-'", "'*'", "'/'", null, 
		"'Max'", "'Min'", null, null, null, "'.'", "','", "':'", "'|'", "'('", 
		"')'", "'{'", "'}'", "'['", "']'", "'''", "'\"'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, "EXPONENT", "NOT", "AND", "OR", "THEN", "IMPLIED_BY", "TERM_OPERATOR", 
		"TERM_EQUAL", "NOT_EQUAL", "SYMMETRIC", "ARITHMETIC_RULE_OPERATOR", "LESS_THAN_EQUAL", 
		"GREATER_THAN_EQUAL", "EQUAL", "ARITHMETIC_OPERATOR", "LINEAR_OPERATOR", 
		"PLUS", "MINUS", "MULT", "DIV", "COEFF_OPERATOR", "MAX", "MIN", "IDENTIFIER", 
		"NONNEGATIVE_NUMBER", "NUMBER", "PERIOD", "COMMA", "COLON", "PIPE", "LPAREN", 
		"RPAREN", "LBRACE", "RBRACE", "LBRACKET", "RBRACKET", "SINGLE_QUOTE", 
		"DOUBLE_QUOTE", "WS", "COMMENT", "LINE_COMMENT", "PYTHON_COMMENT"
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
		public List<Psl_ruleContext> psl_rule() {
			return getRuleContexts(Psl_ruleContext.class);
		}
		public Psl_ruleContext psl_rule(int i) {
			return getRuleContext(Psl_ruleContext.class,i);
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
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(51); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(50);
				psl_rule();
				}
				}
				setState(53); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NOT) | (1L << COEFF_OPERATOR) | (1L << IDENTIFIER) | (1L << NONNEGATIVE_NUMBER) | (1L << NUMBER) | (1L << PIPE) | (1L << LPAREN) | (1L << SINGLE_QUOTE) | (1L << DOUBLE_QUOTE))) != 0) );
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

	public static class Psl_ruleContext extends ParserRuleContext {
		public Logical_ruleContext logical_rule() {
			return getRuleContext(Logical_ruleContext.class,0);
		}
		public Arithmetic_ruleContext arithmetic_rule() {
			return getRuleContext(Arithmetic_ruleContext.class,0);
		}
		public Psl_ruleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_psl_rule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterPsl_rule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitPsl_rule(this);
		}
	}

	public final Psl_ruleContext psl_rule() throws RecognitionException {
		Psl_ruleContext _localctx = new Psl_ruleContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_psl_rule);
		try {
			setState(57);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(55);
				logical_rule();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(56);
				arithmetic_rule();
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
	}

	public final PredicateContext predicate() throws RecognitionException {
		PredicateContext _localctx = new PredicateContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_predicate);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(59);
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
		public TerminalNode TERM_OPERATOR() { return getToken(PSLParser.TERM_OPERATOR, 0); }
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
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_atom);
		int _la;
		try {
			setState(77);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(61);
				predicate();
				setState(62);
				match(LPAREN);
				setState(63);
				term();
				setState(68);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(64);
					match(COMMA);
					setState(65);
					term();
					}
					}
					setState(70);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(71);
				match(RPAREN);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(73);
				term();
				setState(74);
				match(TERM_OPERATOR);
				setState(75);
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
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_literal);
		try {
			setState(82);
			switch (_input.LA(1)) {
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 1);
				{
				setState(79);
				atom();
				}
				break;
			case NOT:
				enterOuterAlt(_localctx, 2);
				{
				setState(80);
				match(NOT);
				setState(81);
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
	}

	public final TermContext term() throws RecognitionException {
		TermContext _localctx = new TermContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_term);
		try {
			setState(86);
			switch (_input.LA(1)) {
			case IDENTIFIER:
				enterOuterAlt(_localctx, 1);
				{
				setState(84);
				variable();
				}
				break;
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(85);
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
	}

	public final VariableContext variable() throws RecognitionException {
		VariableContext _localctx = new VariableContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_variable);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(88);
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
	}

	public final ConstantContext constant() throws RecognitionException {
		ConstantContext _localctx = new ConstantContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_constant);
		try {
			setState(96);
			switch (_input.LA(1)) {
			case SINGLE_QUOTE:
				enterOuterAlt(_localctx, 1);
				{
				setState(90);
				match(SINGLE_QUOTE);
				setState(91);
				match(IDENTIFIER);
				setState(92);
				match(SINGLE_QUOTE);
				}
				break;
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(93);
				match(DOUBLE_QUOTE);
				setState(94);
				match(IDENTIFIER);
				setState(95);
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

	public static class Logical_ruleContext extends ParserRuleContext {
		public Weighted_logical_ruleContext weighted_logical_rule() {
			return getRuleContext(Weighted_logical_ruleContext.class,0);
		}
		public Unweighted_logical_ruleContext unweighted_logical_rule() {
			return getRuleContext(Unweighted_logical_ruleContext.class,0);
		}
		public Logical_ruleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logical_rule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterLogical_rule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitLogical_rule(this);
		}
	}

	public final Logical_ruleContext logical_rule() throws RecognitionException {
		Logical_ruleContext _localctx = new Logical_ruleContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_logical_rule);
		try {
			setState(100);
			switch (_input.LA(1)) {
			case NONNEGATIVE_NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(98);
				weighted_logical_rule();
				}
				break;
			case NOT:
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				enterOuterAlt(_localctx, 2);
				{
				setState(99);
				unweighted_logical_rule();
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

	public static class Weighted_logical_ruleContext extends ParserRuleContext {
		public Weight_expressionContext weight_expression() {
			return getRuleContext(Weight_expressionContext.class,0);
		}
		public Logical_rule_expressionContext logical_rule_expression() {
			return getRuleContext(Logical_rule_expressionContext.class,0);
		}
		public Exponent_expressionContext exponent_expression() {
			return getRuleContext(Exponent_expressionContext.class,0);
		}
		public Weighted_logical_ruleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_weighted_logical_rule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterWeighted_logical_rule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitWeighted_logical_rule(this);
		}
	}

	public final Weighted_logical_ruleContext weighted_logical_rule() throws RecognitionException {
		Weighted_logical_ruleContext _localctx = new Weighted_logical_ruleContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_weighted_logical_rule);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(102);
			weight_expression();
			setState(103);
			logical_rule_expression();
			setState(105);
			_la = _input.LA(1);
			if (_la==SYMMETRIC) {
				{
				setState(104);
				exponent_expression();
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

	public static class Unweighted_logical_ruleContext extends ParserRuleContext {
		public Logical_rule_expressionContext logical_rule_expression() {
			return getRuleContext(Logical_rule_expressionContext.class,0);
		}
		public TerminalNode PERIOD() { return getToken(PSLParser.PERIOD, 0); }
		public Unweighted_logical_ruleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unweighted_logical_rule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterUnweighted_logical_rule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitUnweighted_logical_rule(this);
		}
	}

	public final Unweighted_logical_ruleContext unweighted_logical_rule() throws RecognitionException {
		Unweighted_logical_ruleContext _localctx = new Unweighted_logical_ruleContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_unweighted_logical_rule);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(107);
			logical_rule_expression();
			setState(108);
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

	public static class Logical_rule_expressionContext extends ParserRuleContext {
		public Disjunctive_clauseContext disjunctive_clause() {
			return getRuleContext(Disjunctive_clauseContext.class,0);
		}
		public TerminalNode IMPLIED_BY() { return getToken(PSLParser.IMPLIED_BY, 0); }
		public Conjunctive_clauseContext conjunctive_clause() {
			return getRuleContext(Conjunctive_clauseContext.class,0);
		}
		public TerminalNode THEN() { return getToken(PSLParser.THEN, 0); }
		public Logical_rule_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logical_rule_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterLogical_rule_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitLogical_rule_expression(this);
		}
	}

	public final Logical_rule_expressionContext logical_rule_expression() throws RecognitionException {
		Logical_rule_expressionContext _localctx = new Logical_rule_expressionContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_logical_rule_expression);
		try {
			setState(119);
			switch ( getInterpreter().adaptivePredict(_input,9,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(110);
				disjunctive_clause();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(111);
				disjunctive_clause();
				setState(112);
				match(IMPLIED_BY);
				setState(113);
				conjunctive_clause();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(115);
				conjunctive_clause();
				setState(116);
				match(THEN);
				setState(117);
				disjunctive_clause();
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

	public static class Disjunctive_clauseContext extends ParserRuleContext {
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
		public Disjunctive_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_disjunctive_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterDisjunctive_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitDisjunctive_clause(this);
		}
	}

	public final Disjunctive_clauseContext disjunctive_clause() throws RecognitionException {
		Disjunctive_clauseContext _localctx = new Disjunctive_clauseContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_disjunctive_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(121);
			literal();
			setState(126);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(122);
				match(OR);
				setState(123);
				literal();
				}
				}
				setState(128);
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

	public static class Conjunctive_clauseContext extends ParserRuleContext {
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
		public Conjunctive_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_conjunctive_clause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterConjunctive_clause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitConjunctive_clause(this);
		}
	}

	public final Conjunctive_clauseContext conjunctive_clause() throws RecognitionException {
		Conjunctive_clauseContext _localctx = new Conjunctive_clauseContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_conjunctive_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(129);
			literal();
			setState(134);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(130);
				match(AND);
				setState(131);
				literal();
				}
				}
				setState(136);
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

	public static class Arithmetic_ruleContext extends ParserRuleContext {
		public Weighted_arithmetic_ruleContext weighted_arithmetic_rule() {
			return getRuleContext(Weighted_arithmetic_ruleContext.class,0);
		}
		public Unweighted_arithmetic_ruleContext unweighted_arithmetic_rule() {
			return getRuleContext(Unweighted_arithmetic_ruleContext.class,0);
		}
		public Arithmetic_ruleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmetic_rule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArithmetic_rule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArithmetic_rule(this);
		}
	}

	public final Arithmetic_ruleContext arithmetic_rule() throws RecognitionException {
		Arithmetic_ruleContext _localctx = new Arithmetic_ruleContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_arithmetic_rule);
		try {
			setState(139);
			switch (_input.LA(1)) {
			case NONNEGATIVE_NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(137);
				weighted_arithmetic_rule();
				}
				break;
			case COEFF_OPERATOR:
			case IDENTIFIER:
			case NUMBER:
			case PIPE:
			case LPAREN:
				enterOuterAlt(_localctx, 2);
				{
				setState(138);
				unweighted_arithmetic_rule();
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

	public static class Weighted_arithmetic_ruleContext extends ParserRuleContext {
		public Weight_expressionContext weight_expression() {
			return getRuleContext(Weight_expressionContext.class,0);
		}
		public Arithmetic_rule_expressionContext arithmetic_rule_expression() {
			return getRuleContext(Arithmetic_rule_expressionContext.class,0);
		}
		public Exponent_expressionContext exponent_expression() {
			return getRuleContext(Exponent_expressionContext.class,0);
		}
		public List<Select_statementContext> select_statement() {
			return getRuleContexts(Select_statementContext.class);
		}
		public Select_statementContext select_statement(int i) {
			return getRuleContext(Select_statementContext.class,i);
		}
		public Weighted_arithmetic_ruleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_weighted_arithmetic_rule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterWeighted_arithmetic_rule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitWeighted_arithmetic_rule(this);
		}
	}

	public final Weighted_arithmetic_ruleContext weighted_arithmetic_rule() throws RecognitionException {
		Weighted_arithmetic_ruleContext _localctx = new Weighted_arithmetic_ruleContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_weighted_arithmetic_rule);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(141);
			weight_expression();
			setState(142);
			arithmetic_rule_expression();
			setState(144);
			_la = _input.LA(1);
			if (_la==SYMMETRIC) {
				{
				setState(143);
				exponent_expression();
				}
			}

			setState(149);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LBRACE) {
				{
				{
				setState(146);
				select_statement();
				}
				}
				setState(151);
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

	public static class Unweighted_arithmetic_ruleContext extends ParserRuleContext {
		public Arithmetic_rule_expressionContext arithmetic_rule_expression() {
			return getRuleContext(Arithmetic_rule_expressionContext.class,0);
		}
		public TerminalNode PERIOD() { return getToken(PSLParser.PERIOD, 0); }
		public List<Select_statementContext> select_statement() {
			return getRuleContexts(Select_statementContext.class);
		}
		public Select_statementContext select_statement(int i) {
			return getRuleContext(Select_statementContext.class,i);
		}
		public Unweighted_arithmetic_ruleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unweighted_arithmetic_rule; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterUnweighted_arithmetic_rule(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitUnweighted_arithmetic_rule(this);
		}
	}

	public final Unweighted_arithmetic_ruleContext unweighted_arithmetic_rule() throws RecognitionException {
		Unweighted_arithmetic_ruleContext _localctx = new Unweighted_arithmetic_ruleContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_unweighted_arithmetic_rule);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(152);
			arithmetic_rule_expression();
			setState(153);
			match(PERIOD);
			setState(157);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LBRACE) {
				{
				{
				setState(154);
				select_statement();
				}
				}
				setState(159);
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

	public static class Arithmetic_rule_expressionContext extends ParserRuleContext {
		public List<Arithmetic_rule_operandContext> arithmetic_rule_operand() {
			return getRuleContexts(Arithmetic_rule_operandContext.class);
		}
		public Arithmetic_rule_operandContext arithmetic_rule_operand(int i) {
			return getRuleContext(Arithmetic_rule_operandContext.class,i);
		}
		public TerminalNode ARITHMETIC_RULE_OPERATOR() { return getToken(PSLParser.ARITHMETIC_RULE_OPERATOR, 0); }
		public List<TerminalNode> LINEAR_OPERATOR() { return getTokens(PSLParser.LINEAR_OPERATOR); }
		public TerminalNode LINEAR_OPERATOR(int i) {
			return getToken(PSLParser.LINEAR_OPERATOR, i);
		}
		public Arithmetic_rule_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmetic_rule_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArithmetic_rule_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArithmetic_rule_expression(this);
		}
	}

	public final Arithmetic_rule_expressionContext arithmetic_rule_expression() throws RecognitionException {
		Arithmetic_rule_expressionContext _localctx = new Arithmetic_rule_expressionContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_arithmetic_rule_expression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(160);
			arithmetic_rule_operand();
			setState(165);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LINEAR_OPERATOR) {
				{
				{
				setState(161);
				match(LINEAR_OPERATOR);
				setState(162);
				arithmetic_rule_operand();
				}
				}
				setState(167);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(168);
			match(ARITHMETIC_RULE_OPERATOR);
			setState(169);
			arithmetic_rule_operand();
			setState(174);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LINEAR_OPERATOR) {
				{
				{
				setState(170);
				match(LINEAR_OPERATOR);
				setState(171);
				arithmetic_rule_operand();
				}
				}
				setState(176);
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

	public static class Arithmetic_rule_operandContext extends ParserRuleContext {
		public Sum_augmented_atomContext sum_augmented_atom() {
			return getRuleContext(Sum_augmented_atomContext.class,0);
		}
		public List<CoefficientContext> coefficient() {
			return getRuleContexts(CoefficientContext.class);
		}
		public CoefficientContext coefficient(int i) {
			return getRuleContext(CoefficientContext.class,i);
		}
		public TerminalNode DIV() { return getToken(PSLParser.DIV, 0); }
		public TerminalNode MULT() { return getToken(PSLParser.MULT, 0); }
		public Arithmetic_rule_operandContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arithmetic_rule_operand; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArithmetic_rule_operand(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArithmetic_rule_operand(this);
		}
	}

	public final Arithmetic_rule_operandContext arithmetic_rule_operand() throws RecognitionException {
		Arithmetic_rule_operandContext _localctx = new Arithmetic_rule_operandContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_arithmetic_rule_operand);
		int _la;
		try {
			setState(189);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(181);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << COEFF_OPERATOR) | (1L << NUMBER) | (1L << PIPE) | (1L << LPAREN))) != 0)) {
					{
					setState(177);
					coefficient(0);
					setState(179);
					_la = _input.LA(1);
					if (_la==MULT) {
						{
						setState(178);
						match(MULT);
						}
					}

					}
				}

				setState(183);
				sum_augmented_atom();
				setState(186);
				_la = _input.LA(1);
				if (_la==DIV) {
					{
					setState(184);
					match(DIV);
					setState(185);
					coefficient(0);
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(188);
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

	public static class Sum_augmented_atomContext extends ParserRuleContext {
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(PSLParser.LPAREN, 0); }
		public List<VariableContext> variable() {
			return getRuleContexts(VariableContext.class);
		}
		public VariableContext variable(int i) {
			return getRuleContext(VariableContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(PSLParser.RPAREN, 0); }
		public List<TerminalNode> PLUS() { return getTokens(PSLParser.PLUS); }
		public TerminalNode PLUS(int i) {
			return getToken(PSLParser.PLUS, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(PSLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(PSLParser.COMMA, i);
		}
		public Sum_augmented_atomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sum_augmented_atom; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterSum_augmented_atom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitSum_augmented_atom(this);
		}
	}

	public final Sum_augmented_atomContext sum_augmented_atom() throws RecognitionException {
		Sum_augmented_atomContext _localctx = new Sum_augmented_atomContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_sum_augmented_atom);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(191);
			predicate();
			setState(192);
			match(LPAREN);
			setState(194);
			_la = _input.LA(1);
			if (_la==PLUS) {
				{
				setState(193);
				match(PLUS);
				}
			}

			setState(196);
			variable();
			setState(204);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(197);
				match(COMMA);
				setState(199);
				_la = _input.LA(1);
				if (_la==PLUS) {
					{
					setState(198);
					match(PLUS);
					}
				}

				setState(201);
				variable();
				}
				}
				setState(206);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(207);
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

	public static class CoefficientContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(PSLParser.NUMBER, 0); }
		public List<TerminalNode> PIPE() { return getTokens(PSLParser.PIPE); }
		public TerminalNode PIPE(int i) {
			return getToken(PSLParser.PIPE, i);
		}
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public TerminalNode COEFF_OPERATOR() { return getToken(PSLParser.COEFF_OPERATOR, 0); }
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
		public TerminalNode ARITHMETIC_OPERATOR() { return getToken(PSLParser.ARITHMETIC_OPERATOR, 0); }
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
	}

	public final CoefficientContext coefficient() throws RecognitionException {
		return coefficient(0);
	}

	private CoefficientContext coefficient(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		CoefficientContext _localctx = new CoefficientContext(_ctx, _parentState);
		CoefficientContext _prevctx = _localctx;
		int _startState = 40;
		enterRecursionRule(_localctx, 40, RULE_coefficient, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(230);
			switch (_input.LA(1)) {
			case NUMBER:
				{
				setState(210);
				match(NUMBER);
				}
				break;
			case PIPE:
				{
				setState(211);
				match(PIPE);
				setState(212);
				variable();
				setState(213);
				match(PIPE);
				}
				break;
			case COEFF_OPERATOR:
				{
				setState(215);
				match(COEFF_OPERATOR);
				setState(216);
				match(LBRACKET);
				setState(217);
				coefficient(0);
				setState(220); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(218);
					match(COMMA);
					setState(219);
					coefficient(0);
					}
					}
					setState(222); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==COMMA );
				setState(224);
				match(RBRACKET);
				}
				break;
			case LPAREN:
				{
				setState(226);
				match(LPAREN);
				setState(227);
				coefficient(0);
				setState(228);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(237);
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
					setState(232);
					if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
					setState(233);
					match(ARITHMETIC_OPERATOR);
					setState(234);
					coefficient(4);
					}
					} 
				}
				setState(239);
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

	public static class Select_statementContext extends ParserRuleContext {
		public TerminalNode LBRACE() { return getToken(PSLParser.LBRACE, 0); }
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public TerminalNode COLON() { return getToken(PSLParser.COLON, 0); }
		public Bool_expressionContext bool_expression() {
			return getRuleContext(Bool_expressionContext.class,0);
		}
		public TerminalNode RBRACE() { return getToken(PSLParser.RBRACE, 0); }
		public Select_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_select_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterSelect_statement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitSelect_statement(this);
		}
	}

	public final Select_statementContext select_statement() throws RecognitionException {
		Select_statementContext _localctx = new Select_statementContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_select_statement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(240);
			match(LBRACE);
			setState(241);
			variable();
			setState(242);
			match(COLON);
			setState(243);
			bool_expression(0);
			setState(244);
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

	public static class Bool_expressionContext extends ParserRuleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(PSLParser.LPAREN, 0); }
		public List<Bool_expressionContext> bool_expression() {
			return getRuleContexts(Bool_expressionContext.class);
		}
		public Bool_expressionContext bool_expression(int i) {
			return getRuleContext(Bool_expressionContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(PSLParser.RPAREN, 0); }
		public TerminalNode OR() { return getToken(PSLParser.OR, 0); }
		public TerminalNode AND() { return getToken(PSLParser.AND, 0); }
		public Bool_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_bool_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterBool_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitBool_expression(this);
		}
	}

	public final Bool_expressionContext bool_expression() throws RecognitionException {
		return bool_expression(0);
	}

	private Bool_expressionContext bool_expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		Bool_expressionContext _localctx = new Bool_expressionContext(_ctx, _parentState);
		Bool_expressionContext _prevctx = _localctx;
		int _startState = 44;
		enterRecursionRule(_localctx, 44, RULE_bool_expression, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(252);
			switch (_input.LA(1)) {
			case NOT:
			case IDENTIFIER:
			case SINGLE_QUOTE:
			case DOUBLE_QUOTE:
				{
				setState(247);
				literal();
				}
				break;
			case LPAREN:
				{
				setState(248);
				match(LPAREN);
				setState(249);
				bool_expression(0);
				setState(250);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(262);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,30,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(260);
					switch ( getInterpreter().adaptivePredict(_input,29,_ctx) ) {
					case 1:
						{
						_localctx = new Bool_expressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_bool_expression);
						setState(254);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(255);
						match(OR);
						setState(256);
						bool_expression(3);
						}
						break;
					case 2:
						{
						_localctx = new Bool_expressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_bool_expression);
						setState(257);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(258);
						match(AND);
						setState(259);
						bool_expression(2);
						}
						break;
					}
					} 
				}
				setState(264);
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

	public static class Weight_expressionContext extends ParserRuleContext {
		public TerminalNode NONNEGATIVE_NUMBER() { return getToken(PSLParser.NONNEGATIVE_NUMBER, 0); }
		public TerminalNode COLON() { return getToken(PSLParser.COLON, 0); }
		public Weight_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_weight_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterWeight_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitWeight_expression(this);
		}
	}

	public final Weight_expressionContext weight_expression() throws RecognitionException {
		Weight_expressionContext _localctx = new Weight_expressionContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_weight_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(265);
			match(NONNEGATIVE_NUMBER);
			setState(266);
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

	public static class Exponent_expressionContext extends ParserRuleContext {
		public TerminalNode EXPONENT() { return getToken(PSLParser.EXPONENT, 0); }
		public Exponent_expressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exponent_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterExponent_expression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitExponent_expression(this);
		}
	}

	public final Exponent_expressionContext exponent_expression() throws RecognitionException {
		Exponent_expressionContext _localctx = new Exponent_expressionContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_exponent_expression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(268);
			match(SYMMETRIC);
			setState(269);
			match(EXPONENT);
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
		case 20:
			return coefficient_sempred((CoefficientContext)_localctx, predIndex);
		case 22:
			return bool_expression_sempred((Bool_expressionContext)_localctx, predIndex);
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
	private boolean bool_expression_sempred(Bool_expressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return precpred(_ctx, 2);
		case 2:
			return precpred(_ctx, 1);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\3,\u0112\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\3\2\6\2\66\n\2\r\2\16\2\67\3\3\3\3\5\3<\n\3\3\4\3\4\3\5\3\5"+
		"\3\5\3\5\3\5\7\5E\n\5\f\5\16\5H\13\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5P\n\5"+
		"\3\6\3\6\3\6\5\6U\n\6\3\7\3\7\5\7Y\n\7\3\b\3\b\3\t\3\t\3\t\3\t\3\t\3\t"+
		"\5\tc\n\t\3\n\3\n\5\ng\n\n\3\13\3\13\3\13\5\13l\n\13\3\f\3\f\3\f\3\r\3"+
		"\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\5\rz\n\r\3\16\3\16\3\16\7\16\177\n\16\f"+
		"\16\16\16\u0082\13\16\3\17\3\17\3\17\7\17\u0087\n\17\f\17\16\17\u008a"+
		"\13\17\3\20\3\20\5\20\u008e\n\20\3\21\3\21\3\21\5\21\u0093\n\21\3\21\7"+
		"\21\u0096\n\21\f\21\16\21\u0099\13\21\3\22\3\22\3\22\7\22\u009e\n\22\f"+
		"\22\16\22\u00a1\13\22\3\23\3\23\3\23\7\23\u00a6\n\23\f\23\16\23\u00a9"+
		"\13\23\3\23\3\23\3\23\3\23\7\23\u00af\n\23\f\23\16\23\u00b2\13\23\3\24"+
		"\3\24\5\24\u00b6\n\24\5\24\u00b8\n\24\3\24\3\24\3\24\5\24\u00bd\n\24\3"+
		"\24\5\24\u00c0\n\24\3\25\3\25\3\25\5\25\u00c5\n\25\3\25\3\25\3\25\5\25"+
		"\u00ca\n\25\3\25\7\25\u00cd\n\25\f\25\16\25\u00d0\13\25\3\25\3\25\3\26"+
		"\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\6\26\u00df\n\26\r\26"+
		"\16\26\u00e0\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u00e9\n\26\3\26\3\26\3"+
		"\26\7\26\u00ee\n\26\f\26\16\26\u00f1\13\26\3\27\3\27\3\27\3\27\3\27\3"+
		"\27\3\30\3\30\3\30\3\30\3\30\3\30\5\30\u00ff\n\30\3\30\3\30\3\30\3\30"+
		"\3\30\3\30\7\30\u0107\n\30\f\30\16\30\u010a\13\30\3\31\3\31\3\31\3\32"+
		"\3\32\3\32\3\32\2\4*.\33\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&("+
		"*,.\60\62\2\2\u011a\2\65\3\2\2\2\4;\3\2\2\2\6=\3\2\2\2\bO\3\2\2\2\nT\3"+
		"\2\2\2\fX\3\2\2\2\16Z\3\2\2\2\20b\3\2\2\2\22f\3\2\2\2\24h\3\2\2\2\26m"+
		"\3\2\2\2\30y\3\2\2\2\32{\3\2\2\2\34\u0083\3\2\2\2\36\u008d\3\2\2\2 \u008f"+
		"\3\2\2\2\"\u009a\3\2\2\2$\u00a2\3\2\2\2&\u00bf\3\2\2\2(\u00c1\3\2\2\2"+
		"*\u00e8\3\2\2\2,\u00f2\3\2\2\2.\u00fe\3\2\2\2\60\u010b\3\2\2\2\62\u010e"+
		"\3\2\2\2\64\66\5\4\3\2\65\64\3\2\2\2\66\67\3\2\2\2\67\65\3\2\2\2\678\3"+
		"\2\2\28\3\3\2\2\29<\5\22\n\2:<\5\36\20\2;9\3\2\2\2;:\3\2\2\2<\5\3\2\2"+
		"\2=>\7\32\2\2>\7\3\2\2\2?@\5\6\4\2@A\7!\2\2AF\5\f\7\2BC\7\36\2\2CE\5\f"+
		"\7\2DB\3\2\2\2EH\3\2\2\2FD\3\2\2\2FG\3\2\2\2GI\3\2\2\2HF\3\2\2\2IJ\7\""+
		"\2\2JP\3\2\2\2KL\5\f\7\2LM\7\t\2\2MN\5\f\7\2NP\3\2\2\2O?\3\2\2\2OK\3\2"+
		"\2\2P\t\3\2\2\2QU\5\b\5\2RS\7\4\2\2SU\5\n\6\2TQ\3\2\2\2TR\3\2\2\2U\13"+
		"\3\2\2\2VY\5\16\b\2WY\5\20\t\2XV\3\2\2\2XW\3\2\2\2Y\r\3\2\2\2Z[\7\32\2"+
		"\2[\17\3\2\2\2\\]\7\'\2\2]^\7\32\2\2^c\7\'\2\2_`\7(\2\2`a\7\32\2\2ac\7"+
		"(\2\2b\\\3\2\2\2b_\3\2\2\2c\21\3\2\2\2dg\5\24\13\2eg\5\26\f\2fd\3\2\2"+
		"\2fe\3\2\2\2g\23\3\2\2\2hi\5\60\31\2ik\5\30\r\2jl\5\62\32\2kj\3\2\2\2"+
		"kl\3\2\2\2l\25\3\2\2\2mn\5\30\r\2no\7\35\2\2o\27\3\2\2\2pz\5\32\16\2q"+
		"r\5\32\16\2rs\7\b\2\2st\5\34\17\2tz\3\2\2\2uv\5\34\17\2vw\7\7\2\2wx\5"+
		"\32\16\2xz\3\2\2\2yp\3\2\2\2yq\3\2\2\2yu\3\2\2\2z\31\3\2\2\2{\u0080\5"+
		"\n\6\2|}\7\6\2\2}\177\5\n\6\2~|\3\2\2\2\177\u0082\3\2\2\2\u0080~\3\2\2"+
		"\2\u0080\u0081\3\2\2\2\u0081\33\3\2\2\2\u0082\u0080\3\2\2\2\u0083\u0088"+
		"\5\n\6\2\u0084\u0085\7\5\2\2\u0085\u0087\5\n\6\2\u0086\u0084\3\2\2\2\u0087"+
		"\u008a\3\2\2\2\u0088\u0086\3\2\2\2\u0088\u0089\3\2\2\2\u0089\35\3\2\2"+
		"\2\u008a\u0088\3\2\2\2\u008b\u008e\5 \21\2\u008c\u008e\5\"\22\2\u008d"+
		"\u008b\3\2\2\2\u008d\u008c\3\2\2\2\u008e\37\3\2\2\2\u008f\u0090\5\60\31"+
		"\2\u0090\u0092\5$\23\2\u0091\u0093\5\62\32\2\u0092\u0091\3\2\2\2\u0092"+
		"\u0093\3\2\2\2\u0093\u0097\3\2\2\2\u0094\u0096\5,\27\2\u0095\u0094\3\2"+
		"\2\2\u0096\u0099\3\2\2\2\u0097\u0095\3\2\2\2\u0097\u0098\3\2\2\2\u0098"+
		"!\3\2\2\2\u0099\u0097\3\2\2\2\u009a\u009b\5$\23\2\u009b\u009f\7\35\2\2"+
		"\u009c\u009e\5,\27\2\u009d\u009c\3\2\2\2\u009e\u00a1\3\2\2\2\u009f\u009d"+
		"\3\2\2\2\u009f\u00a0\3\2\2\2\u00a0#\3\2\2\2\u00a1\u009f\3\2\2\2\u00a2"+
		"\u00a7\5&\24\2\u00a3\u00a4\7\22\2\2\u00a4\u00a6\5&\24\2\u00a5\u00a3\3"+
		"\2\2\2\u00a6\u00a9\3\2\2\2\u00a7\u00a5\3\2\2\2\u00a7\u00a8\3\2\2\2\u00a8"+
		"\u00aa\3\2\2\2\u00a9\u00a7\3\2\2\2\u00aa\u00ab\7\r\2\2\u00ab\u00b0\5&"+
		"\24\2\u00ac\u00ad\7\22\2\2\u00ad\u00af\5&\24\2\u00ae\u00ac\3\2\2\2\u00af"+
		"\u00b2\3\2\2\2\u00b0\u00ae\3\2\2\2\u00b0\u00b1\3\2\2\2\u00b1%\3\2\2\2"+
		"\u00b2\u00b0\3\2\2\2\u00b3\u00b5\5*\26\2\u00b4\u00b6\7\25\2\2\u00b5\u00b4"+
		"\3\2\2\2\u00b5\u00b6\3\2\2\2\u00b6\u00b8\3\2\2\2\u00b7\u00b3\3\2\2\2\u00b7"+
		"\u00b8\3\2\2\2\u00b8\u00b9\3\2\2\2\u00b9\u00bc\5(\25\2\u00ba\u00bb\7\26"+
		"\2\2\u00bb\u00bd\5*\26\2\u00bc\u00ba\3\2\2\2\u00bc\u00bd\3\2\2\2\u00bd"+
		"\u00c0\3\2\2\2\u00be\u00c0\5*\26\2\u00bf\u00b7\3\2\2\2\u00bf\u00be\3\2"+
		"\2\2\u00c0\'\3\2\2\2\u00c1\u00c2\5\6\4\2\u00c2\u00c4\7!\2\2\u00c3\u00c5"+
		"\7\23\2\2\u00c4\u00c3\3\2\2\2\u00c4\u00c5\3\2\2\2\u00c5\u00c6\3\2\2\2"+
		"\u00c6\u00ce\5\16\b\2\u00c7\u00c9\7\36\2\2\u00c8\u00ca\7\23\2\2\u00c9"+
		"\u00c8\3\2\2\2\u00c9\u00ca\3\2\2\2\u00ca\u00cb\3\2\2\2\u00cb\u00cd\5\16"+
		"\b\2\u00cc\u00c7\3\2\2\2\u00cd\u00d0\3\2\2\2\u00ce\u00cc\3\2\2\2\u00ce"+
		"\u00cf\3\2\2\2\u00cf\u00d1\3\2\2\2\u00d0\u00ce\3\2\2\2\u00d1\u00d2\7\""+
		"\2\2\u00d2)\3\2\2\2\u00d3\u00d4\b\26\1\2\u00d4\u00e9\7\34\2\2\u00d5\u00d6"+
		"\7 \2\2\u00d6\u00d7\5\16\b\2\u00d7\u00d8\7 \2\2\u00d8\u00e9\3\2\2\2\u00d9"+
		"\u00da\7\27\2\2\u00da\u00db\7%\2\2\u00db\u00de\5*\26\2\u00dc\u00dd\7\36"+
		"\2\2\u00dd\u00df\5*\26\2\u00de\u00dc\3\2\2\2\u00df\u00e0\3\2\2\2\u00e0"+
		"\u00de\3\2\2\2\u00e0\u00e1\3\2\2\2\u00e1\u00e2\3\2\2\2\u00e2\u00e3\7&"+
		"\2\2\u00e3\u00e9\3\2\2\2\u00e4\u00e5\7!\2\2\u00e5\u00e6\5*\26\2\u00e6"+
		"\u00e7\7\"\2\2\u00e7\u00e9\3\2\2\2\u00e8\u00d3\3\2\2\2\u00e8\u00d5\3\2"+
		"\2\2\u00e8\u00d9\3\2\2\2\u00e8\u00e4\3\2\2\2\u00e9\u00ef\3\2\2\2\u00ea"+
		"\u00eb\f\5\2\2\u00eb\u00ec\7\21\2\2\u00ec\u00ee\5*\26\6\u00ed\u00ea\3"+
		"\2\2\2\u00ee\u00f1\3\2\2\2\u00ef\u00ed\3\2\2\2\u00ef\u00f0\3\2\2\2\u00f0"+
		"+\3\2\2\2\u00f1\u00ef\3\2\2\2\u00f2\u00f3\7#\2\2\u00f3\u00f4\5\16\b\2"+
		"\u00f4\u00f5\7\37\2\2\u00f5\u00f6\5.\30\2\u00f6\u00f7\7$\2\2\u00f7-\3"+
		"\2\2\2\u00f8\u00f9\b\30\1\2\u00f9\u00ff\5\n\6\2\u00fa\u00fb\7!\2\2\u00fb"+
		"\u00fc\5.\30\2\u00fc\u00fd\7\"\2\2\u00fd\u00ff\3\2\2\2\u00fe\u00f8\3\2"+
		"\2\2\u00fe\u00fa\3\2\2\2\u00ff\u0108\3\2\2\2\u0100\u0101\f\4\2\2\u0101"+
		"\u0102\7\6\2\2\u0102\u0107\5.\30\5\u0103\u0104\f\3\2\2\u0104\u0105\7\5"+
		"\2\2\u0105\u0107\5.\30\4\u0106\u0100\3\2\2\2\u0106\u0103\3\2\2\2\u0107"+
		"\u010a\3\2\2\2\u0108\u0106\3\2\2\2\u0108\u0109\3\2\2\2\u0109/\3\2\2\2"+
		"\u010a\u0108\3\2\2\2\u010b\u010c\7\33\2\2\u010c\u010d\7\37\2\2\u010d\61"+
		"\3\2\2\2\u010e\u010f\7\f\2\2\u010f\u0110\7\3\2\2\u0110\63\3\2\2\2!\67"+
		";FOTXbfky\u0080\u0088\u008d\u0092\u0097\u009f\u00a7\u00b0\u00b5\u00b7"+
		"\u00bc\u00bf\u00c4\u00c9\u00ce\u00e0\u00e8\u00ef\u00fe\u0106\u0108";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}