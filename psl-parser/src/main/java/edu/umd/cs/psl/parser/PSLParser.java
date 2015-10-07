// Generated from PSL.g4 by ANTLR 4.1
package edu.umd.cs.psl.parser;
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
	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		ID_ARG=1, STR_ARG=2, INT_ARG=3, DBL_ARG=4, ON=5, FUNCTIONAL_CONSTRAINT=6, 
		PARTIAL_FUNCTIONAL_CONSTRAINT=7, INVERSE_FUNCTIONAL_CONSTRAINT=8, INVERSE_PARTIAL_FUNCTIONAL_CONSTRAINT=9, 
		COMMA=10, OPENBRACE=11, CLOSEBRACE=12, CONSTRAINT=13, SQUARED=14, OPENPAR=15, 
		CLOSEPAR=16, NOT=17, AND=18, OR=19, THEN=20, IMPLIEDBY=21, NOTEQUAL=22, 
		SYMMETRIC=23, IDENTIFIER=24, NUMBER=25, STRING=26, WS=27, COMMENT=28, 
		LINE_COMMENT=29, PYTHON_COMMENT=30, MODEL_HEADER=31;
	public static final String[] tokenNames = {
		"<INVALID>", "'UniqueID'", "'String'", "'Integer'", "'Double'", "'on'", 
		"'Functional'", "'PartialFunctional'", "'InverseFunctional'", "'InversePartialFunctional'", 
		"','", "'{'", "'}'", "'constraint'", "'{squared}'", "'('", "')'", "NOT", 
		"'&'", "'|'", "THEN", "IMPLIEDBY", "NOTEQUAL", "'^'", "IDENTIFIER", "NUMBER", 
		"STRING", "WS", "COMMENT", "LINE_COMMENT", "PYTHON_COMMENT", "'Model:'"
	};
	public static final int
		RULE_program = 0, RULE_predicateDefinition = 1, RULE_kernel = 2, RULE_expression = 3, 
		RULE_atom = 4, RULE_argument = 5, RULE_variable = 6, RULE_constant = 7, 
		RULE_intConstant = 8, RULE_strConstant = 9, RULE_weight = 10, RULE_predicate = 11, 
		RULE_constraint = 12, RULE_constraintType = 13, RULE_argumentType = 14;
	public static final String[] ruleNames = {
		"program", "predicateDefinition", "kernel", "expression", "atom", "argument", 
		"variable", "constant", "intConstant", "strConstant", "weight", "predicate", 
		"constraint", "constraintType", "argumentType"
	};

	@Override
	public String getGrammarFileName() { return "PSL.g4"; }

	@Override
	public String[] getTokenNames() { return tokenNames; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public PSLParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}
	public static class ProgramContext extends ParserRuleContext {
		public ConstraintContext constraint(int i) {
			return getRuleContext(ConstraintContext.class,i);
		}
		public List<ConstraintContext> constraint() {
			return getRuleContexts(ConstraintContext.class);
		}
		public KernelContext kernel(int i) {
			return getRuleContext(KernelContext.class,i);
		}
		public PredicateDefinitionContext predicateDefinition(int i) {
			return getRuleContext(PredicateDefinitionContext.class,i);
		}
		public List<PredicateDefinitionContext> predicateDefinition() {
			return getRuleContexts(PredicateDefinitionContext.class);
		}
		public List<KernelContext> kernel() {
			return getRuleContexts(KernelContext.class);
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
			setState(33); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				setState(33);
				switch ( getInterpreter().adaptivePredict(_input,0,_ctx) ) {
				case 1:
					{
					setState(30); predicateDefinition();
					}
					break;

				case 2:
					{
					setState(31); kernel();
					}
					break;

				case 3:
					{
					setState(32); constraint();
					}
					break;
				}
				}
				setState(35); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==OPENBRACE || _la==IDENTIFIER );
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

	public static class PredicateDefinitionContext extends ParserRuleContext {
		public List<ArgumentTypeContext> argumentType() {
			return getRuleContexts(ArgumentTypeContext.class);
		}
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public ArgumentTypeContext argumentType(int i) {
			return getRuleContext(ArgumentTypeContext.class,i);
		}
		public PredicateDefinitionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_predicateDefinition; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterPredicateDefinition(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitPredicateDefinition(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitPredicateDefinition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PredicateDefinitionContext predicateDefinition() throws RecognitionException {
		PredicateDefinitionContext _localctx = new PredicateDefinitionContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_predicateDefinition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(37); predicate();
			setState(38); match(OPENPAR);
			setState(39); argumentType();
			setState(44);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(40); match(COMMA);
				setState(41); argumentType();
				}
				}
				setState(46);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(47); match(CLOSEPAR);
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

	public static class KernelContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public WeightContext weight() {
			return getRuleContext(WeightContext.class,0);
		}
		public TerminalNode SQUARED() { return getToken(PSLParser.SQUARED, 0); }
		public KernelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_kernel; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterKernel(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitKernel(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitKernel(this);
			else return visitor.visitChildren(this);
		}
	}

	public final KernelContext kernel() throws RecognitionException {
		KernelContext _localctx = new KernelContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_kernel);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(49); weight();
			setState(50); expression(0);
			setState(52);
			_la = _input.LA(1);
			if (_la==SQUARED) {
				{
				setState(51); match(SQUARED);
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

	public static class ExpressionContext extends ParserRuleContext {
		public int _p;
		public List<ArgumentContext> argument() {
			return getRuleContexts(ArgumentContext.class);
		}
		public TerminalNode THEN() { return getToken(PSLParser.THEN, 0); }
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode SYMMETRIC() { return getToken(PSLParser.SYMMETRIC, 0); }
		public TerminalNode IMPLIEDBY() { return getToken(PSLParser.IMPLIEDBY, 0); }
		public ArgumentContext argument(int i) {
			return getRuleContext(ArgumentContext.class,i);
		}
		public TerminalNode AND() { return getToken(PSLParser.AND, 0); }
		public TerminalNode OR() { return getToken(PSLParser.OR, 0); }
		public TerminalNode NOT() { return getToken(PSLParser.NOT, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public TerminalNode NOTEQUAL() { return getToken(PSLParser.NOTEQUAL, 0); }
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public ExpressionContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public ExpressionContext(ParserRuleContext parent, int invokingState, int _p) {
			super(parent, invokingState);
			this._p = _p;
		}
		@Override public int getRuleIndex() { return RULE_expression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState, _p);
		ExpressionContext _prevctx = _localctx;
		int _startState = 6;
		enterRecursionRule(_localctx, RULE_expression);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(70);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				{
				setState(55); match(NOT);
				setState(56); expression(9);
				}
				break;

			case 2:
				{
				setState(57); match(OPENPAR);
				setState(58); expression(0);
				setState(59); match(CLOSEPAR);
				}
				break;

			case 3:
				{
				setState(61); argument();
				setState(62); match(NOTEQUAL);
				setState(63); argument();
				}
				break;

			case 4:
				{
				setState(65); argument();
				setState(66); match(SYMMETRIC);
				setState(67); argument();
				}
				break;

			case 5:
				{
				setState(69); atom();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(86);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
			while ( _alt!=2 && _alt!=-1 ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(84);
					switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
					case 1:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState, _p);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(72);
						if (!(5 >= _localctx._p)) throw new FailedPredicateException(this, "5 >= $_p");
						setState(73); match(AND);
						setState(74); expression(6);
						}
						break;

					case 2:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState, _p);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(75);
						if (!(4 >= _localctx._p)) throw new FailedPredicateException(this, "4 >= $_p");
						setState(76); match(OR);
						setState(77); expression(5);
						}
						break;

					case 3:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState, _p);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(78);
						if (!(3 >= _localctx._p)) throw new FailedPredicateException(this, "3 >= $_p");
						setState(79); match(THEN);
						setState(80); expression(4);
						}
						break;

					case 4:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState, _p);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(81);
						if (!(2 >= _localctx._p)) throw new FailedPredicateException(this, "2 >= $_p");
						setState(82); match(IMPLIEDBY);
						setState(83); expression(3);
						}
						break;
					}
					} 
				}
				setState(88);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
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

	public static class AtomContext extends ParserRuleContext {
		public List<ArgumentContext> argument() {
			return getRuleContexts(ArgumentContext.class);
		}
		public ArgumentContext argument(int i) {
			return getRuleContext(ArgumentContext.class,i);
		}
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
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
		enterRule(_localctx, 8, RULE_atom);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(89); predicate();
			setState(90); match(OPENPAR);
			setState(91); argument();
			setState(96);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(92); match(COMMA);
				setState(93); argument();
				}
				}
				setState(98);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(99); match(CLOSEPAR);
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

	public static class ArgumentContext extends ParserRuleContext {
		public VariableContext variable() {
			return getRuleContext(VariableContext.class,0);
		}
		public ConstantContext constant() {
			return getRuleContext(ConstantContext.class,0);
		}
		public ArgumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArgument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArgument(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitArgument(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgumentContext argument() throws RecognitionException {
		ArgumentContext _localctx = new ArgumentContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_argument);
		try {
			setState(103);
			switch (_input.LA(1)) {
			case IDENTIFIER:
				enterOuterAlt(_localctx, 1);
				{
				setState(101); variable();
				}
				break;
			case NUMBER:
			case STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(102); constant();
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
			setState(105); match(IDENTIFIER);
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
		public StrConstantContext strConstant() {
			return getRuleContext(StrConstantContext.class,0);
		}
		public IntConstantContext intConstant() {
			return getRuleContext(IntConstantContext.class,0);
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
			setState(109);
			switch (_input.LA(1)) {
			case NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(107); intConstant();
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(108); strConstant();
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

	public static class IntConstantContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(PSLParser.NUMBER, 0); }
		public IntConstantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_intConstant; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterIntConstant(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitIntConstant(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitIntConstant(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IntConstantContext intConstant() throws RecognitionException {
		IntConstantContext _localctx = new IntConstantContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_intConstant);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(111); match(NUMBER);
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

	public static class StrConstantContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(PSLParser.STRING, 0); }
		public StrConstantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_strConstant; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterStrConstant(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitStrConstant(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitStrConstant(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StrConstantContext strConstant() throws RecognitionException {
		StrConstantContext _localctx = new StrConstantContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_strConstant);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(113); match(STRING);
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

	public static class WeightContext extends ParserRuleContext {
		public TerminalNode CLOSEBRACE() { return getToken(PSLParser.CLOSEBRACE, 0); }
		public TerminalNode CONSTRAINT() { return getToken(PSLParser.CONSTRAINT, 0); }
		public TerminalNode NUMBER() { return getToken(PSLParser.NUMBER, 0); }
		public TerminalNode OPENBRACE() { return getToken(PSLParser.OPENBRACE, 0); }
		public WeightContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_weight; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterWeight(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitWeight(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitWeight(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WeightContext weight() throws RecognitionException {
		WeightContext _localctx = new WeightContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_weight);
		try {
			setState(121);
			switch ( getInterpreter().adaptivePredict(_input,10,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(115); match(OPENBRACE);
				setState(116); match(NUMBER);
				setState(117); match(CLOSEBRACE);
				}
				break;

			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(118); match(OPENBRACE);
				setState(119); match(CONSTRAINT);
				setState(120); match(CLOSEBRACE);
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
		enterRule(_localctx, 22, RULE_predicate);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(123); match(IDENTIFIER);
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

	public static class ConstraintContext extends ParserRuleContext {
		public List<ArgumentTypeContext> argumentType() {
			return getRuleContexts(ArgumentTypeContext.class);
		}
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public TerminalNode CONSTRAINT() { return getToken(PSLParser.CONSTRAINT, 0); }
		public ConstraintTypeContext constraintType() {
			return getRuleContext(ConstraintTypeContext.class,0);
		}
		public ArgumentTypeContext argumentType(int i) {
			return getRuleContext(ArgumentTypeContext.class,i);
		}
		public ConstraintContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constraint; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterConstraint(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitConstraint(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitConstraint(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstraintContext constraint() throws RecognitionException {
		ConstraintContext _localctx = new ConstraintContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_constraint);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(125); match(OPENBRACE);
			setState(126); match(CONSTRAINT);
			setState(127); match(CLOSEBRACE);
			setState(128); constraintType();
			setState(129); match(ON);
			setState(130); predicate();
			setState(131); match(OPENPAR);
			setState(132); argumentType();
			setState(137);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(133); match(COMMA);
				setState(134); argumentType();
				}
				}
				setState(139);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(140); match(CLOSEPAR);
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

	public static class ConstraintTypeContext extends ParserRuleContext {
		public ConstraintTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constraintType; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterConstraintType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitConstraintType(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitConstraintType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstraintTypeContext constraintType() throws RecognitionException {
		ConstraintTypeContext _localctx = new ConstraintTypeContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_constraintType);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(142);
			_la = _input.LA(1);
			if ( !(_la==FUNCTIONAL_CONSTRAINT || _la==PARTIAL_FUNCTIONAL_CONSTRAINT) ) {
			_errHandler.recoverInline(this);
			}
			consume();
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

	public static class ArgumentTypeContext extends ParserRuleContext {
		public ArgumentTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argumentType; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).enterArgumentType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof PSLListener ) ((PSLListener)listener).exitArgumentType(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof PSLVisitor ) return ((PSLVisitor<? extends T>)visitor).visitArgumentType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgumentTypeContext argumentType() throws RecognitionException {
		ArgumentTypeContext _localctx = new ArgumentTypeContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_argumentType);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(144);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ID_ARG) | (1L << STR_ARG) | (1L << INT_ARG) | (1L << DBL_ARG))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			consume();
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
		case 3: return expression_sempred((ExpressionContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0: return 5 >= _localctx._p;

		case 1: return 4 >= _localctx._p;

		case 2: return 3 >= _localctx._p;

		case 3: return 2 >= _localctx._p;
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\uacf5\uee8c\u4f5d\u8b0d\u4a45\u78bd\u1b2f\u3378\3!\u0095\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\3\2\3\2\3\2\6\2$\n\2"+
		"\r\2\16\2%\3\3\3\3\3\3\3\3\3\3\7\3-\n\3\f\3\16\3\60\13\3\3\3\3\3\3\4\3"+
		"\4\3\4\5\4\67\n\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5"+
		"\3\5\3\5\3\5\5\5I\n\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5"+
		"\7\5W\n\5\f\5\16\5Z\13\5\3\6\3\6\3\6\3\6\3\6\7\6a\n\6\f\6\16\6d\13\6\3"+
		"\6\3\6\3\7\3\7\5\7j\n\7\3\b\3\b\3\t\3\t\5\tp\n\t\3\n\3\n\3\13\3\13\3\f"+
		"\3\f\3\f\3\f\3\f\3\f\5\f|\n\f\3\r\3\r\3\16\3\16\3\16\3\16\3\16\3\16\3"+
		"\16\3\16\3\16\3\16\7\16\u008a\n\16\f\16\16\16\u008d\13\16\3\16\3\16\3"+
		"\17\3\17\3\20\3\20\3\20\2\21\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36\2"+
		"\4\3\2\b\t\3\2\3\6\u0097\2#\3\2\2\2\4\'\3\2\2\2\6\63\3\2\2\2\bH\3\2\2"+
		"\2\n[\3\2\2\2\fi\3\2\2\2\16k\3\2\2\2\20o\3\2\2\2\22q\3\2\2\2\24s\3\2\2"+
		"\2\26{\3\2\2\2\30}\3\2\2\2\32\177\3\2\2\2\34\u0090\3\2\2\2\36\u0092\3"+
		"\2\2\2 $\5\4\3\2!$\5\6\4\2\"$\5\32\16\2# \3\2\2\2#!\3\2\2\2#\"\3\2\2\2"+
		"$%\3\2\2\2%#\3\2\2\2%&\3\2\2\2&\3\3\2\2\2\'(\5\30\r\2()\7\21\2\2).\5\36"+
		"\20\2*+\7\f\2\2+-\5\36\20\2,*\3\2\2\2-\60\3\2\2\2.,\3\2\2\2./\3\2\2\2"+
		"/\61\3\2\2\2\60.\3\2\2\2\61\62\7\22\2\2\62\5\3\2\2\2\63\64\5\26\f\2\64"+
		"\66\5\b\5\2\65\67\7\20\2\2\66\65\3\2\2\2\66\67\3\2\2\2\67\7\3\2\2\289"+
		"\b\5\1\29:\7\23\2\2:I\5\b\5\2;<\7\21\2\2<=\5\b\5\2=>\7\22\2\2>I\3\2\2"+
		"\2?@\5\f\7\2@A\7\30\2\2AB\5\f\7\2BI\3\2\2\2CD\5\f\7\2DE\7\31\2\2EF\5\f"+
		"\7\2FI\3\2\2\2GI\5\n\6\2H8\3\2\2\2H;\3\2\2\2H?\3\2\2\2HC\3\2\2\2HG\3\2"+
		"\2\2IX\3\2\2\2JK\6\5\2\3KL\7\24\2\2LW\5\b\5\2MN\6\5\3\3NO\7\25\2\2OW\5"+
		"\b\5\2PQ\6\5\4\3QR\7\26\2\2RW\5\b\5\2ST\6\5\5\3TU\7\27\2\2UW\5\b\5\2V"+
		"J\3\2\2\2VM\3\2\2\2VP\3\2\2\2VS\3\2\2\2WZ\3\2\2\2XV\3\2\2\2XY\3\2\2\2"+
		"Y\t\3\2\2\2ZX\3\2\2\2[\\\5\30\r\2\\]\7\21\2\2]b\5\f\7\2^_\7\f\2\2_a\5"+
		"\f\7\2`^\3\2\2\2ad\3\2\2\2b`\3\2\2\2bc\3\2\2\2ce\3\2\2\2db\3\2\2\2ef\7"+
		"\22\2\2f\13\3\2\2\2gj\5\16\b\2hj\5\20\t\2ig\3\2\2\2ih\3\2\2\2j\r\3\2\2"+
		"\2kl\7\32\2\2l\17\3\2\2\2mp\5\22\n\2np\5\24\13\2om\3\2\2\2on\3\2\2\2p"+
		"\21\3\2\2\2qr\7\33\2\2r\23\3\2\2\2st\7\34\2\2t\25\3\2\2\2uv\7\r\2\2vw"+
		"\7\33\2\2w|\7\16\2\2xy\7\r\2\2yz\7\17\2\2z|\7\16\2\2{u\3\2\2\2{x\3\2\2"+
		"\2|\27\3\2\2\2}~\7\32\2\2~\31\3\2\2\2\177\u0080\7\r\2\2\u0080\u0081\7"+
		"\17\2\2\u0081\u0082\7\16\2\2\u0082\u0083\5\34\17\2\u0083\u0084\7\7\2\2"+
		"\u0084\u0085\5\30\r\2\u0085\u0086\7\21\2\2\u0086\u008b\5\36\20\2\u0087"+
		"\u0088\7\f\2\2\u0088\u008a\5\36\20\2\u0089\u0087\3\2\2\2\u008a\u008d\3"+
		"\2\2\2\u008b\u0089\3\2\2\2\u008b\u008c\3\2\2\2\u008c\u008e\3\2\2\2\u008d"+
		"\u008b\3\2\2\2\u008e\u008f\7\22\2\2\u008f\33\3\2\2\2\u0090\u0091\t\2\2"+
		"\2\u0091\35\3\2\2\2\u0092\u0093\t\3\2\2\u0093\37\3\2\2\2\16#%.\66HVXb"+
		"io{\u008b";
	public static final ATN _ATN =
		ATNSimulator.deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
