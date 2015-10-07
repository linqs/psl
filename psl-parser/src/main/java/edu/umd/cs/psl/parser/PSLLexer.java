// Generated from PSL.g4 by ANTLR 4.1
package edu.umd.cs.psl.parser;
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
	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] tokenNames = {
		"<INVALID>",
		"'UniqueID'", "'String'", "'Integer'", "'Double'", "'on'", "'Functional'", 
		"'PartialFunctional'", "'InverseFunctional'", "'InversePartialFunctional'", 
		"','", "'{'", "'}'", "'constraint'", "'{squared}'", "'('", "')'", "NOT", 
		"'&'", "'|'", "THEN", "IMPLIEDBY", "NOTEQUAL", "'^'", "IDENTIFIER", "NUMBER", 
		"STRING", "WS", "COMMENT", "LINE_COMMENT", "PYTHON_COMMENT", "'Model:'"
	};
	public static final String[] ruleNames = {
		"ID_ARG", "STR_ARG", "INT_ARG", "DBL_ARG", "ON", "FUNCTIONAL_CONSTRAINT", 
		"PARTIAL_FUNCTIONAL_CONSTRAINT", "INVERSE_FUNCTIONAL_CONSTRAINT", "INVERSE_PARTIAL_FUNCTIONAL_CONSTRAINT", 
		"COMMA", "OPENBRACE", "CLOSEBRACE", "CONSTRAINT", "SQUARED", "OPENPAR", 
		"CLOSEPAR", "NOT", "AND", "OR", "THEN", "IMPLIEDBY", "NOTEQUAL", "SYMMETRIC", 
		"IDENTIFIER", "Letter", "LetterOrDigit", "NUMBER", "STRING", "DOUBLE_QUOTE", 
		"SINGLE_QUOTE", "DIGIT", "WS", "COMMENT", "LINE_COMMENT", "PYTHON_COMMENT", 
		"MODEL_HEADER"
	};


	public PSLLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "PSL.g4"; }

	@Override
	public String[] getTokenNames() { return tokenNames; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	@Override
	public void action(RuleContext _localctx, int ruleIndex, int actionIndex) {
		switch (ruleIndex) {
		case 31: WS_action((RuleContext)_localctx, actionIndex); break;

		case 32: COMMENT_action((RuleContext)_localctx, actionIndex); break;

		case 33: LINE_COMMENT_action((RuleContext)_localctx, actionIndex); break;

		case 34: PYTHON_COMMENT_action((RuleContext)_localctx, actionIndex); break;

		case 35: MODEL_HEADER_action((RuleContext)_localctx, actionIndex); break;
		}
	}
	private void MODEL_HEADER_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 4: skip();  break;
		}
	}
	private void WS_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 0: skip();  break;
		}
	}
	private void LINE_COMMENT_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 2: skip();  break;
		}
	}
	private void COMMENT_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 1: skip();  break;
		}
	}
	private void PYTHON_COMMENT_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 3: skip();  break;
		}
	}

	public static final String _serializedATN =
		"\3\uacf5\uee8c\u4f5d\u8b0d\u4a45\u78bd\u1b2f\u3378\2!\u015c\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\3\2\3\2\3\2\3\2\3\2\3\2\3\2\3\2\3\2\3\3"+
		"\3\3\3\3\3\3\3\3\3\3\3\3\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\4\3\5\3\5\3\5\3"+
		"\5\3\5\3\5\3\5\3\6\3\6\3\6\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7\3\7"+
		"\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3\b\3"+
		"\b\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t\3\t"+
		"\3\t\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3"+
		"\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\13\3\13\3\f\3\f\3\r\3\r\3\16\3\16"+
		"\3\16\3\16\3\16\3\16\3\16\3\16\3\16\3\16\3\16\3\17\3\17\3\17\3\17\3\17"+
		"\3\17\3\17\3\17\3\17\3\17\3\20\3\20\3\21\3\21\3\22\3\22\3\23\3\23\3\24"+
		"\3\24\3\25\3\25\3\25\3\25\5\25\u00df\n\25\3\26\3\26\3\26\3\26\5\26\u00e5"+
		"\n\26\3\27\3\27\3\27\5\27\u00ea\n\27\3\30\3\30\3\31\3\31\7\31\u00f0\n"+
		"\31\f\31\16\31\u00f3\13\31\3\32\3\32\3\33\3\33\3\34\5\34\u00fa\n\34\3"+
		"\34\6\34\u00fd\n\34\r\34\16\34\u00fe\3\34\5\34\u0102\n\34\3\34\7\34\u0105"+
		"\n\34\f\34\16\34\u0108\13\34\3\34\3\34\3\34\6\34\u010d\n\34\r\34\16\34"+
		"\u010e\5\34\u0111\n\34\3\35\3\35\7\35\u0115\n\35\f\35\16\35\u0118\13\35"+
		"\3\35\3\35\3\35\7\35\u011d\n\35\f\35\16\35\u0120\13\35\3\35\5\35\u0123"+
		"\n\35\3\36\3\36\3\37\3\37\3 \3 \3!\6!\u012c\n!\r!\16!\u012d\3!\3!\3\""+
		"\3\"\3\"\3\"\7\"\u0136\n\"\f\"\16\"\u0139\13\"\3\"\3\"\3\"\3\"\3\"\3#"+
		"\3#\3#\3#\7#\u0144\n#\f#\16#\u0147\13#\3#\3#\3$\3$\7$\u014d\n$\f$\16$"+
		"\u0150\13$\3$\3$\3%\3%\3%\3%\3%\3%\3%\3%\3%\5\u0116\u011e\u0137&\3\3\1"+
		"\5\4\1\7\5\1\t\6\1\13\7\1\r\b\1\17\t\1\21\n\1\23\13\1\25\f\1\27\r\1\31"+
		"\16\1\33\17\1\35\20\1\37\21\1!\22\1#\23\1%\24\1\'\25\1)\26\1+\27\1-\30"+
		"\1/\31\1\61\32\1\63\2\1\65\2\1\67\33\19\34\1;\2\1=\2\1?\2\1A\35\2C\36"+
		"\3E\37\4G \5I!\6\3\2\t\4\2##\u0080\u0080\6\2&&C\\aac|\7\2&&\62;C\\aac"+
		"|\4\2GGgg\3\2\62;\5\2\13\f\16\17\"\"\4\2\f\f\17\17\u0167\2\3\3\2\2\2\2"+
		"\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2"+
		"\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2"+
		"\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2"+
		"\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2"+
		"\2\67\3\2\2\2\29\3\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2E\3\2\2\2\2G\3\2\2\2\2"+
		"I\3\2\2\2\3K\3\2\2\2\5T\3\2\2\2\7[\3\2\2\2\tc\3\2\2\2\13j\3\2\2\2\rm\3"+
		"\2\2\2\17x\3\2\2\2\21\u008a\3\2\2\2\23\u009c\3\2\2\2\25\u00b5\3\2\2\2"+
		"\27\u00b7\3\2\2\2\31\u00b9\3\2\2\2\33\u00bb\3\2\2\2\35\u00c6\3\2\2\2\37"+
		"\u00d0\3\2\2\2!\u00d2\3\2\2\2#\u00d4\3\2\2\2%\u00d6\3\2\2\2\'\u00d8\3"+
		"\2\2\2)\u00de\3\2\2\2+\u00e4\3\2\2\2-\u00e9\3\2\2\2/\u00eb\3\2\2\2\61"+
		"\u00ed\3\2\2\2\63\u00f4\3\2\2\2\65\u00f6\3\2\2\2\67\u00f9\3\2\2\29\u0122"+
		"\3\2\2\2;\u0124\3\2\2\2=\u0126\3\2\2\2?\u0128\3\2\2\2A\u012b\3\2\2\2C"+
		"\u0131\3\2\2\2E\u013f\3\2\2\2G\u014a\3\2\2\2I\u0153\3\2\2\2KL\7W\2\2L"+
		"M\7p\2\2MN\7k\2\2NO\7s\2\2OP\7w\2\2PQ\7g\2\2QR\7K\2\2RS\7F\2\2S\4\3\2"+
		"\2\2TU\7U\2\2UV\7v\2\2VW\7t\2\2WX\7k\2\2XY\7p\2\2YZ\7i\2\2Z\6\3\2\2\2"+
		"[\\\7K\2\2\\]\7p\2\2]^\7v\2\2^_\7g\2\2_`\7i\2\2`a\7g\2\2ab\7t\2\2b\b\3"+
		"\2\2\2cd\7F\2\2de\7q\2\2ef\7w\2\2fg\7d\2\2gh\7n\2\2hi\7g\2\2i\n\3\2\2"+
		"\2jk\7q\2\2kl\7p\2\2l\f\3\2\2\2mn\7H\2\2no\7w\2\2op\7p\2\2pq\7e\2\2qr"+
		"\7v\2\2rs\7k\2\2st\7q\2\2tu\7p\2\2uv\7c\2\2vw\7n\2\2w\16\3\2\2\2xy\7R"+
		"\2\2yz\7c\2\2z{\7t\2\2{|\7v\2\2|}\7k\2\2}~\7c\2\2~\177\7n\2\2\177\u0080"+
		"\7H\2\2\u0080\u0081\7w\2\2\u0081\u0082\7p\2\2\u0082\u0083\7e\2\2\u0083"+
		"\u0084\7v\2\2\u0084\u0085\7k\2\2\u0085\u0086\7q\2\2\u0086\u0087\7p\2\2"+
		"\u0087\u0088\7c\2\2\u0088\u0089\7n\2\2\u0089\20\3\2\2\2\u008a\u008b\7"+
		"K\2\2\u008b\u008c\7p\2\2\u008c\u008d\7x\2\2\u008d\u008e\7g\2\2\u008e\u008f"+
		"\7t\2\2\u008f\u0090\7u\2\2\u0090\u0091\7g\2\2\u0091\u0092\7H\2\2\u0092"+
		"\u0093\7w\2\2\u0093\u0094\7p\2\2\u0094\u0095\7e\2\2\u0095\u0096\7v\2\2"+
		"\u0096\u0097\7k\2\2\u0097\u0098\7q\2\2\u0098\u0099\7p\2\2\u0099\u009a"+
		"\7c\2\2\u009a\u009b\7n\2\2\u009b\22\3\2\2\2\u009c\u009d\7K\2\2\u009d\u009e"+
		"\7p\2\2\u009e\u009f\7x\2\2\u009f\u00a0\7g\2\2\u00a0\u00a1\7t\2\2\u00a1"+
		"\u00a2\7u\2\2\u00a2\u00a3\7g\2\2\u00a3\u00a4\7R\2\2\u00a4\u00a5\7c\2\2"+
		"\u00a5\u00a6\7t\2\2\u00a6\u00a7\7v\2\2\u00a7\u00a8\7k\2\2\u00a8\u00a9"+
		"\7c\2\2\u00a9\u00aa\7n\2\2\u00aa\u00ab\7H\2\2\u00ab\u00ac\7w\2\2\u00ac"+
		"\u00ad\7p\2\2\u00ad\u00ae\7e\2\2\u00ae\u00af\7v\2\2\u00af\u00b0\7k\2\2"+
		"\u00b0\u00b1\7q\2\2\u00b1\u00b2\7p\2\2\u00b2\u00b3\7c\2\2\u00b3\u00b4"+
		"\7n\2\2\u00b4\24\3\2\2\2\u00b5\u00b6\7.\2\2\u00b6\26\3\2\2\2\u00b7\u00b8"+
		"\7}\2\2\u00b8\30\3\2\2\2\u00b9\u00ba\7\177\2\2\u00ba\32\3\2\2\2\u00bb"+
		"\u00bc\7e\2\2\u00bc\u00bd\7q\2\2\u00bd\u00be\7p\2\2\u00be\u00bf\7u\2\2"+
		"\u00bf\u00c0\7v\2\2\u00c0\u00c1\7t\2\2\u00c1\u00c2\7c\2\2\u00c2\u00c3"+
		"\7k\2\2\u00c3\u00c4\7p\2\2\u00c4\u00c5\7v\2\2\u00c5\34\3\2\2\2\u00c6\u00c7"+
		"\7}\2\2\u00c7\u00c8\7u\2\2\u00c8\u00c9\7s\2\2\u00c9\u00ca\7w\2\2\u00ca"+
		"\u00cb\7c\2\2\u00cb\u00cc\7t\2\2\u00cc\u00cd\7g\2\2\u00cd\u00ce\7f\2\2"+
		"\u00ce\u00cf\7\177\2\2\u00cf\36\3\2\2\2\u00d0\u00d1\7*\2\2\u00d1 \3\2"+
		"\2\2\u00d2\u00d3\7+\2\2\u00d3\"\3\2\2\2\u00d4\u00d5\t\2\2\2\u00d5$\3\2"+
		"\2\2\u00d6\u00d7\7(\2\2\u00d7&\3\2\2\2\u00d8\u00d9\7~\2\2\u00d9(\3\2\2"+
		"\2\u00da\u00db\7@\2\2\u00db\u00df\7@\2\2\u00dc\u00dd\7/\2\2\u00dd\u00df"+
		"\7@\2\2\u00de\u00da\3\2\2\2\u00de\u00dc\3\2\2\2\u00df*\3\2\2\2\u00e0\u00e1"+
		"\7>\2\2\u00e1\u00e5\7>\2\2\u00e2\u00e3\7>\2\2\u00e3\u00e5\7/\2\2\u00e4"+
		"\u00e0\3\2\2\2\u00e4\u00e2\3\2\2\2\u00e5,\3\2\2\2\u00e6\u00ea\7/\2\2\u00e7"+
		"\u00e8\7#\2\2\u00e8\u00ea\7?\2\2\u00e9\u00e6\3\2\2\2\u00e9\u00e7\3\2\2"+
		"\2\u00ea.\3\2\2\2\u00eb\u00ec\7`\2\2\u00ec\60\3\2\2\2\u00ed\u00f1\5\63"+
		"\32\2\u00ee\u00f0\5\65\33\2\u00ef\u00ee\3\2\2\2\u00f0\u00f3\3\2\2\2\u00f1"+
		"\u00ef\3\2\2\2\u00f1\u00f2\3\2\2\2\u00f2\62\3\2\2\2\u00f3\u00f1\3\2\2"+
		"\2\u00f4\u00f5\t\3\2\2\u00f5\64\3\2\2\2\u00f6\u00f7\t\4\2\2\u00f7\66\3"+
		"\2\2\2\u00f8\u00fa\7/\2\2\u00f9\u00f8\3\2\2\2\u00f9\u00fa\3\2\2\2\u00fa"+
		"\u00fc\3\2\2\2\u00fb\u00fd\5? \2\u00fc\u00fb\3\2\2\2\u00fd\u00fe\3\2\2"+
		"\2\u00fe\u00fc\3\2\2\2\u00fe\u00ff\3\2\2\2\u00ff\u0101\3\2\2\2\u0100\u0102"+
		"\7\60\2\2\u0101\u0100\3\2\2\2\u0101\u0102\3\2\2\2\u0102\u0106\3\2\2\2"+
		"\u0103\u0105\5? \2\u0104\u0103\3\2\2\2\u0105\u0108\3\2\2\2\u0106\u0104"+
		"\3\2\2\2\u0106\u0107\3\2\2\2\u0107\u0110\3\2\2\2\u0108\u0106\3\2\2\2\u0109"+
		"\u010a\t\5\2\2\u010a\u010c\7/\2\2\u010b\u010d\5? \2\u010c\u010b\3\2\2"+
		"\2\u010d\u010e\3\2\2\2\u010e\u010c\3\2\2\2\u010e\u010f\3\2\2\2\u010f\u0111"+
		"\3\2\2\2\u0110\u0109\3\2\2\2\u0110\u0111\3\2\2\2\u01118\3\2\2\2\u0112"+
		"\u0116\7$\2\2\u0113\u0115\13\2\2\2\u0114\u0113\3\2\2\2\u0115\u0118\3\2"+
		"\2\2\u0116\u0117\3\2\2\2\u0116\u0114\3\2\2\2\u0117\u0119\3\2\2\2\u0118"+
		"\u0116\3\2\2\2\u0119\u0123\7$\2\2\u011a\u011e\7)\2\2\u011b\u011d\13\2"+
		"\2\2\u011c\u011b\3\2\2\2\u011d\u0120\3\2\2\2\u011e\u011f\3\2\2\2\u011e"+
		"\u011c\3\2\2\2\u011f\u0121\3\2\2\2\u0120\u011e\3\2\2\2\u0121\u0123\7)"+
		"\2\2\u0122\u0112\3\2\2\2\u0122\u011a\3\2\2\2\u0123:\3\2\2\2\u0124\u0125"+
		"\7$\2\2\u0125<\3\2\2\2\u0126\u0127\7)\2\2\u0127>\3\2\2\2\u0128\u0129\t"+
		"\6\2\2\u0129@\3\2\2\2\u012a\u012c\t\7\2\2\u012b\u012a\3\2\2\2\u012c\u012d"+
		"\3\2\2\2\u012d\u012b\3\2\2\2\u012d\u012e\3\2\2\2\u012e\u012f\3\2\2\2\u012f"+
		"\u0130\b!\2\2\u0130B\3\2\2\2\u0131\u0132\7\61\2\2\u0132\u0133\7,\2\2\u0133"+
		"\u0137\3\2\2\2\u0134\u0136\13\2\2\2\u0135\u0134\3\2\2\2\u0136\u0139\3"+
		"\2\2\2\u0137\u0138\3\2\2\2\u0137\u0135\3\2\2\2\u0138\u013a\3\2\2\2\u0139"+
		"\u0137\3\2\2\2\u013a\u013b\7,\2\2\u013b\u013c\7\61\2\2\u013c\u013d\3\2"+
		"\2\2\u013d\u013e\b\"\3\2\u013eD\3\2\2\2\u013f\u0140\7\61\2\2\u0140\u0141"+
		"\7\61\2\2\u0141\u0145\3\2\2\2\u0142\u0144\n\b\2\2\u0143\u0142\3\2\2\2"+
		"\u0144\u0147\3\2\2\2\u0145\u0143\3\2\2\2\u0145\u0146\3\2\2\2\u0146\u0148"+
		"\3\2\2\2\u0147\u0145\3\2\2\2\u0148\u0149\b#\4\2\u0149F\3\2\2\2\u014a\u014e"+
		"\7%\2\2\u014b\u014d\n\b\2\2\u014c\u014b\3\2\2\2\u014d\u0150\3\2\2\2\u014e"+
		"\u014c\3\2\2\2\u014e\u014f\3\2\2\2\u014f\u0151\3\2\2\2\u0150\u014e\3\2"+
		"\2\2\u0151\u0152\b$\5\2\u0152H\3\2\2\2\u0153\u0154\7O\2\2\u0154\u0155"+
		"\7q\2\2\u0155\u0156\7f\2\2\u0156\u0157\7g\2\2\u0157\u0158\7n\2\2\u0158"+
		"\u0159\7<\2\2\u0159\u015a\3\2\2\2\u015a\u015b\b%\6\2\u015bJ\3\2\2\2\24"+
		"\2\u00de\u00e4\u00e9\u00f1\u00f9\u00fe\u0101\u0106\u010e\u0110\u0116\u011e"+
		"\u0122\u012d\u0137\u0145\u014e";
	public static final ATN _ATN =
		ATNSimulator.deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
