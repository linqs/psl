// Generated from PSL.g4 by ANTLR 4.5
package edu.umd.cs.psl.cli.modelloader;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class PSLLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.5", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		EXPONENT_EXPRESSION=1, NOT=2, AND=3, OR=4, THEN=5, IMPLIED_BY=6, TERM_OPERATOR=7, 
		TERM_EQUAL=8, NOT_EQUAL=9, ARITHMETIC_RULE_OPERATOR=10, LESS_THAN_EQUAL=11, 
		GREATER_THAN_EQUAL=12, EQUAL=13, ARITHMETIC_OPERATOR=14, LINEAR_OPERATOR=15, 
		PLUS=16, MINUS=17, MULT=18, DIV=19, COEFF_OPERATOR=20, MAX=21, MIN=22, 
		IDENTIFIER=23, NONNEGATIVE_NUMBER=24, PERIOD=25, CARET=26, COMMA=27, COLON=28, 
		PIPE=29, LPAREN=30, RPAREN=31, LBRACE=32, RBRACE=33, LBRACKET=34, RBRACKET=35, 
		SINGLE_QUOTE=36, DOUBLE_QUOTE=37, WS=38, COMMENT=39, LINE_COMMENT=40, 
		PYTHON_COMMENT=41;
	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"EXPONENT_EXPRESSION", "NOT", "AND", "OR", "THEN", "IMPLIED_BY", "TERM_OPERATOR", 
		"TERM_EQUAL", "NOT_EQUAL", "ARITHMETIC_RULE_OPERATOR", "LESS_THAN_EQUAL", 
		"GREATER_THAN_EQUAL", "EQUAL", "ARITHMETIC_OPERATOR", "LINEAR_OPERATOR", 
		"PLUS", "MINUS", "MULT", "DIV", "COEFF_OPERATOR", "MAX", "MIN", "IDENTIFIER", 
		"NONNEGATIVE_NUMBER", "LETTER", "DIGIT", "PERIOD", "CARET", "COMMA", "COLON", 
		"PIPE", "LPAREN", "RPAREN", "LBRACE", "RBRACE", "LBRACKET", "RBRACKET", 
		"SINGLE_QUOTE", "DOUBLE_QUOTE", "WS", "COMMENT", "LINE_COMMENT", "PYTHON_COMMENT"
	};

	private static final String[] _LITERAL_NAMES = {
		null, null, null, null, null, null, null, null, "'=='", "'!='", null, 
		"'<='", "'>='", "'='", null, null, "'+'", "'-'", "'*'", "'/'", null, "'@Max'", 
		"'@Min'", null, null, "'.'", "'^'", "','", "':'", "'|'", "'('", "')'", 
		"'{'", "'}'", "'['", "']'", "'''", "'\"'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, "EXPONENT_EXPRESSION", "NOT", "AND", "OR", "THEN", "IMPLIED_BY", 
		"TERM_OPERATOR", "TERM_EQUAL", "NOT_EQUAL", "ARITHMETIC_RULE_OPERATOR", 
		"LESS_THAN_EQUAL", "GREATER_THAN_EQUAL", "EQUAL", "ARITHMETIC_OPERATOR", 
		"LINEAR_OPERATOR", "PLUS", "MINUS", "MULT", "DIV", "COEFF_OPERATOR", "MAX", 
		"MIN", "IDENTIFIER", "NONNEGATIVE_NUMBER", "PERIOD", "CARET", "COMMA", 
		"COLON", "PIPE", "LPAREN", "RPAREN", "LBRACE", "RBRACE", "LBRACKET", "RBRACKET", 
		"SINGLE_QUOTE", "DOUBLE_QUOTE", "WS", "COMMENT", "LINE_COMMENT", "PYTHON_COMMENT"
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


	public PSLLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "PSL.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2+\u0113\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\3\2\3\2\3\2\3\3\3\3\3\4\3\4\3\4\5\4b\n\4\3\5\3\5\3\5\5\5g\n\5\3\6"+
		"\3\6\3\6\3\6\5\6m\n\6\3\7\3\7\3\7\3\7\5\7s\n\7\3\b\3\b\5\bw\n\b\3\t\3"+
		"\t\3\t\3\n\3\n\3\n\3\13\3\13\3\13\5\13\u0082\n\13\3\f\3\f\3\f\3\r\3\r"+
		"\3\r\3\16\3\16\3\17\3\17\3\17\3\17\5\17\u0090\n\17\3\20\3\20\5\20\u0094"+
		"\n\20\3\21\3\21\3\22\3\22\3\23\3\23\3\24\3\24\3\25\3\25\5\25\u00a0\n\25"+
		"\3\26\3\26\3\26\3\26\3\26\3\27\3\27\3\27\3\27\3\27\3\30\3\30\3\30\7\30"+
		"\u00af\n\30\f\30\16\30\u00b2\13\30\3\31\6\31\u00b5\n\31\r\31\16\31\u00b6"+
		"\3\31\5\31\u00ba\n\31\3\31\7\31\u00bd\n\31\f\31\16\31\u00c0\13\31\3\31"+
		"\3\31\5\31\u00c4\n\31\3\31\6\31\u00c7\n\31\r\31\16\31\u00c8\5\31\u00cb"+
		"\n\31\3\32\3\32\3\33\3\33\3\34\3\34\3\35\3\35\3\36\3\36\3\37\3\37\3 \3"+
		" \3!\3!\3\"\3\"\3#\3#\3$\3$\3%\3%\3&\3&\3\'\3\'\3(\3(\3)\6)\u00ec\n)\r"+
		")\16)\u00ed\3)\3)\3*\3*\3*\3*\7*\u00f6\n*\f*\16*\u00f9\13*\3*\3*\3*\3"+
		"*\3*\3+\3+\3+\3+\7+\u0104\n+\f+\16+\u0107\13+\3+\3+\3,\3,\7,\u010d\n,"+
		"\f,\16,\u0110\13,\3,\3,\3\u00f7\2-\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n"+
		"\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22#\23%\24\'\25)\26+\27-\30"+
		"/\31\61\32\63\2\65\2\67\339\34;\35=\36?\37A C!E\"G#I$K%M&O\'Q(S)U*W+\3"+
		"\2\t\3\2\63\64\4\2##\u0080\u0080\4\2GGgg\6\2&&C\\aac|\3\2\62;\5\2\13\f"+
		"\16\17\"\"\4\2\f\f\17\17\u0128\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t"+
		"\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2"+
		"\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2"+
		"\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2"+
		"+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2"+
		";\3\2\2\2\2=\3\2\2\2\2?\3\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2E\3\2\2\2\2G\3"+
		"\2\2\2\2I\3\2\2\2\2K\3\2\2\2\2M\3\2\2\2\2O\3\2\2\2\2Q\3\2\2\2\2S\3\2\2"+
		"\2\2U\3\2\2\2\2W\3\2\2\2\3Y\3\2\2\2\5\\\3\2\2\2\7a\3\2\2\2\tf\3\2\2\2"+
		"\13l\3\2\2\2\rr\3\2\2\2\17v\3\2\2\2\21x\3\2\2\2\23{\3\2\2\2\25\u0081\3"+
		"\2\2\2\27\u0083\3\2\2\2\31\u0086\3\2\2\2\33\u0089\3\2\2\2\35\u008f\3\2"+
		"\2\2\37\u0093\3\2\2\2!\u0095\3\2\2\2#\u0097\3\2\2\2%\u0099\3\2\2\2\'\u009b"+
		"\3\2\2\2)\u009f\3\2\2\2+\u00a1\3\2\2\2-\u00a6\3\2\2\2/\u00ab\3\2\2\2\61"+
		"\u00b4\3\2\2\2\63\u00cc\3\2\2\2\65\u00ce\3\2\2\2\67\u00d0\3\2\2\29\u00d2"+
		"\3\2\2\2;\u00d4\3\2\2\2=\u00d6\3\2\2\2?\u00d8\3\2\2\2A\u00da\3\2\2\2C"+
		"\u00dc\3\2\2\2E\u00de\3\2\2\2G\u00e0\3\2\2\2I\u00e2\3\2\2\2K\u00e4\3\2"+
		"\2\2M\u00e6\3\2\2\2O\u00e8\3\2\2\2Q\u00eb\3\2\2\2S\u00f1\3\2\2\2U\u00ff"+
		"\3\2\2\2W\u010a\3\2\2\2YZ\59\35\2Z[\t\2\2\2[\4\3\2\2\2\\]\t\3\2\2]\6\3"+
		"\2\2\2^b\7(\2\2_`\7(\2\2`b\7(\2\2a^\3\2\2\2a_\3\2\2\2b\b\3\2\2\2cg\7~"+
		"\2\2de\7~\2\2eg\7~\2\2fc\3\2\2\2fd\3\2\2\2g\n\3\2\2\2hi\7@\2\2im\7@\2"+
		"\2jk\7/\2\2km\7@\2\2lh\3\2\2\2lj\3\2\2\2m\f\3\2\2\2no\7>\2\2os\7>\2\2"+
		"pq\7>\2\2qs\7/\2\2rn\3\2\2\2rp\3\2\2\2s\16\3\2\2\2tw\5\21\t\2uw\5\23\n"+
		"\2vt\3\2\2\2vu\3\2\2\2w\20\3\2\2\2xy\7?\2\2yz\7?\2\2z\22\3\2\2\2{|\7#"+
		"\2\2|}\7?\2\2}\24\3\2\2\2~\u0082\5\27\f\2\177\u0082\5\31\r\2\u0080\u0082"+
		"\5\33\16\2\u0081~\3\2\2\2\u0081\177\3\2\2\2\u0081\u0080\3\2\2\2\u0082"+
		"\26\3\2\2\2\u0083\u0084\7>\2\2\u0084\u0085\7?\2\2\u0085\30\3\2\2\2\u0086"+
		"\u0087\7@\2\2\u0087\u0088\7?\2\2\u0088\32\3\2\2\2\u0089\u008a\7?\2\2\u008a"+
		"\34\3\2\2\2\u008b\u0090\5!\21\2\u008c\u0090\5#\22\2\u008d\u0090\5%\23"+
		"\2\u008e\u0090\5\'\24\2\u008f\u008b\3\2\2\2\u008f\u008c\3\2\2\2\u008f"+
		"\u008d\3\2\2\2\u008f\u008e\3\2\2\2\u0090\36\3\2\2\2\u0091\u0094\5!\21"+
		"\2\u0092\u0094\5#\22\2\u0093\u0091\3\2\2\2\u0093\u0092\3\2\2\2\u0094 "+
		"\3\2\2\2\u0095\u0096\7-\2\2\u0096\"\3\2\2\2\u0097\u0098\7/\2\2\u0098$"+
		"\3\2\2\2\u0099\u009a\7,\2\2\u009a&\3\2\2\2\u009b\u009c\7\61\2\2\u009c"+
		"(\3\2\2\2\u009d\u00a0\5+\26\2\u009e\u00a0\5-\27\2\u009f\u009d\3\2\2\2"+
		"\u009f\u009e\3\2\2\2\u00a0*\3\2\2\2\u00a1\u00a2\7B\2\2\u00a2\u00a3\7O"+
		"\2\2\u00a3\u00a4\7c\2\2\u00a4\u00a5\7z\2\2\u00a5,\3\2\2\2\u00a6\u00a7"+
		"\7B\2\2\u00a7\u00a8\7O\2\2\u00a8\u00a9\7k\2\2\u00a9\u00aa\7p\2\2\u00aa"+
		".\3\2\2\2\u00ab\u00b0\5\63\32\2\u00ac\u00af\5\63\32\2\u00ad\u00af\5\65"+
		"\33\2\u00ae\u00ac\3\2\2\2\u00ae\u00ad\3\2\2\2\u00af\u00b2\3\2\2\2\u00b0"+
		"\u00ae\3\2\2\2\u00b0\u00b1\3\2\2\2\u00b1\60\3\2\2\2\u00b2\u00b0\3\2\2"+
		"\2\u00b3\u00b5\5\65\33\2\u00b4\u00b3\3\2\2\2\u00b5\u00b6\3\2\2\2\u00b6"+
		"\u00b4\3\2\2\2\u00b6\u00b7\3\2\2\2\u00b7\u00b9\3\2\2\2\u00b8\u00ba\5\67"+
		"\34\2\u00b9\u00b8\3\2\2\2\u00b9\u00ba\3\2\2\2\u00ba\u00be\3\2\2\2\u00bb"+
		"\u00bd\5\65\33\2\u00bc\u00bb\3\2\2\2\u00bd\u00c0\3\2\2\2\u00be\u00bc\3"+
		"\2\2\2\u00be\u00bf\3\2\2\2\u00bf\u00ca\3\2\2\2\u00c0\u00be\3\2\2\2\u00c1"+
		"\u00c3\t\4\2\2\u00c2\u00c4\5#\22\2\u00c3\u00c2\3\2\2\2\u00c3\u00c4\3\2"+
		"\2\2\u00c4\u00c6\3\2\2\2\u00c5\u00c7\5\65\33\2\u00c6\u00c5\3\2\2\2\u00c7"+
		"\u00c8\3\2\2\2\u00c8\u00c6\3\2\2\2\u00c8\u00c9\3\2\2\2\u00c9\u00cb\3\2"+
		"\2\2\u00ca\u00c1\3\2\2\2\u00ca\u00cb\3\2\2\2\u00cb\62\3\2\2\2\u00cc\u00cd"+
		"\t\5\2\2\u00cd\64\3\2\2\2\u00ce\u00cf\t\6\2\2\u00cf\66\3\2\2\2\u00d0\u00d1"+
		"\7\60\2\2\u00d18\3\2\2\2\u00d2\u00d3\7`\2\2\u00d3:\3\2\2\2\u00d4\u00d5"+
		"\7.\2\2\u00d5<\3\2\2\2\u00d6\u00d7\7<\2\2\u00d7>\3\2\2\2\u00d8\u00d9\7"+
		"~\2\2\u00d9@\3\2\2\2\u00da\u00db\7*\2\2\u00dbB\3\2\2\2\u00dc\u00dd\7+"+
		"\2\2\u00ddD\3\2\2\2\u00de\u00df\7}\2\2\u00dfF\3\2\2\2\u00e0\u00e1\7\177"+
		"\2\2\u00e1H\3\2\2\2\u00e2\u00e3\7]\2\2\u00e3J\3\2\2\2\u00e4\u00e5\7_\2"+
		"\2\u00e5L\3\2\2\2\u00e6\u00e7\7)\2\2\u00e7N\3\2\2\2\u00e8\u00e9\7$\2\2"+
		"\u00e9P\3\2\2\2\u00ea\u00ec\t\7\2\2\u00eb\u00ea\3\2\2\2\u00ec\u00ed\3"+
		"\2\2\2\u00ed\u00eb\3\2\2\2\u00ed\u00ee\3\2\2\2\u00ee\u00ef\3\2\2\2\u00ef"+
		"\u00f0\b)\2\2\u00f0R\3\2\2\2\u00f1\u00f2\7\61\2\2\u00f2\u00f3\7,\2\2\u00f3"+
		"\u00f7\3\2\2\2\u00f4\u00f6\13\2\2\2\u00f5\u00f4\3\2\2\2\u00f6\u00f9\3"+
		"\2\2\2\u00f7\u00f8\3\2\2\2\u00f7\u00f5\3\2\2\2\u00f8\u00fa\3\2\2\2\u00f9"+
		"\u00f7\3\2\2\2\u00fa\u00fb\7,\2\2\u00fb\u00fc\7\61\2\2\u00fc\u00fd\3\2"+
		"\2\2\u00fd\u00fe\b*\2\2\u00feT\3\2\2\2\u00ff\u0100\7\61\2\2\u0100\u0101"+
		"\7\61\2\2\u0101\u0105\3\2\2\2\u0102\u0104\n\b\2\2\u0103\u0102\3\2\2\2"+
		"\u0104\u0107\3\2\2\2\u0105\u0103\3\2\2\2\u0105\u0106\3\2\2\2\u0106\u0108"+
		"\3\2\2\2\u0107\u0105\3\2\2\2\u0108\u0109\b+\2\2\u0109V\3\2\2\2\u010a\u010e"+
		"\7%\2\2\u010b\u010d\n\b\2\2\u010c\u010b\3\2\2\2\u010d\u0110\3\2\2\2\u010e"+
		"\u010c\3\2\2\2\u010e\u010f\3\2\2\2\u010f\u0111\3\2\2\2\u0110\u010e\3\2"+
		"\2\2\u0111\u0112\b,\2\2\u0112X\3\2\2\2\30\2aflrv\u0081\u008f\u0093\u009f"+
		"\u00ae\u00b0\u00b6\u00b9\u00be\u00c3\u00c8\u00ca\u00ed\u00f7\u0105\u010e"+
		"\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}