// Generated from PSL.g4 by ANTLR 4.5.3
package edu.umd.cs.psl.parser.antlr;
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
	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"T__0", "T__1", "T__2", "T__3", "EXPONENT_EXPRESSION", "LESS_THAN_EQUAL", 
		"GREATER_THAN_EQUAL", "EQUAL", "PLUS", "MINUS", "MULT", "DIV", "MAX", 
		"MIN", "IDENTIFIER", "NONNEGATIVE_NUMBER", "LETTER", "DIGIT", "PERIOD", 
		"COMMA", "COLON", "NEGATION", "AMPERSAND", "PIPE", "LPAREN", "RPAREN", 
		"LBRACE", "RBRACE", "LBRACKET", "RBRACKET", "SINGLE_QUOTE", "DOUBLE_QUOTE", 
		"WS", "COMMENT", "LINE_COMMENT", "PYTHON_COMMENT"
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
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2$\u00dd\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\3\2\3\2\3\2\3\3\3\3\3\3\3\4\3\4\3\4\3\5"+
		"\3\5\3\5\3\6\3\6\3\6\3\7\3\7\3\7\3\b\3\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13"+
		"\3\f\3\f\3\r\3\r\3\16\3\16\3\16\3\16\3\16\3\17\3\17\3\17\3\17\3\17\3\20"+
		"\3\20\3\20\7\20x\n\20\f\20\16\20{\13\20\3\21\6\21~\n\21\r\21\16\21\177"+
		"\3\21\3\21\6\21\u0084\n\21\r\21\16\21\u0085\5\21\u0088\n\21\3\21\3\21"+
		"\5\21\u008c\n\21\3\21\6\21\u008f\n\21\r\21\16\21\u0090\5\21\u0093\n\21"+
		"\3\22\3\22\3\23\3\23\3\24\3\24\3\25\3\25\3\26\3\26\3\27\3\27\3\30\3\30"+
		"\3\31\3\31\3\32\3\32\3\33\3\33\3\34\3\34\3\35\3\35\3\36\3\36\3\37\3\37"+
		"\3 \3 \3!\3!\3\"\6\"\u00b6\n\"\r\"\16\"\u00b7\3\"\3\"\3#\3#\3#\3#\7#\u00c0"+
		"\n#\f#\16#\u00c3\13#\3#\3#\3#\3#\3#\3$\3$\3$\3$\7$\u00ce\n$\f$\16$\u00d1"+
		"\13$\3$\3$\3%\3%\7%\u00d7\n%\f%\16%\u00da\13%\3%\3%\3\u00c1\2&\3\3\5\4"+
		"\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22"+
		"#\2%\2\'\23)\24+\25-\26/\27\61\30\63\31\65\32\67\339\34;\35=\36?\37A "+
		"C!E\"G#I$\3\2\t\3\2\63\64\4\2GGgg\6\2&&C\\aac|\3\2\62;\4\2##\u0080\u0080"+
		"\5\2\13\f\16\17\"\"\4\2\f\f\17\17\u00e6\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3"+
		"\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2"+
		"\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35"+
		"\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-"+
		"\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2"+
		"\2\29\3\2\2\2\2;\3\2\2\2\2=\3\2\2\2\2?\3\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2"+
		"E\3\2\2\2\2G\3\2\2\2\2I\3\2\2\2\3K\3\2\2\2\5N\3\2\2\2\7Q\3\2\2\2\tT\3"+
		"\2\2\2\13W\3\2\2\2\rZ\3\2\2\2\17]\3\2\2\2\21`\3\2\2\2\23b\3\2\2\2\25d"+
		"\3\2\2\2\27f\3\2\2\2\31h\3\2\2\2\33j\3\2\2\2\35o\3\2\2\2\37t\3\2\2\2!"+
		"}\3\2\2\2#\u0094\3\2\2\2%\u0096\3\2\2\2\'\u0098\3\2\2\2)\u009a\3\2\2\2"+
		"+\u009c\3\2\2\2-\u009e\3\2\2\2/\u00a0\3\2\2\2\61\u00a2\3\2\2\2\63\u00a4"+
		"\3\2\2\2\65\u00a6\3\2\2\2\67\u00a8\3\2\2\29\u00aa\3\2\2\2;\u00ac\3\2\2"+
		"\2=\u00ae\3\2\2\2?\u00b0\3\2\2\2A\u00b2\3\2\2\2C\u00b5\3\2\2\2E\u00bb"+
		"\3\2\2\2G\u00c9\3\2\2\2I\u00d4\3\2\2\2KL\7@\2\2LM\7@\2\2M\4\3\2\2\2NO"+
		"\7/\2\2OP\7@\2\2P\6\3\2\2\2QR\7>\2\2RS\7>\2\2S\b\3\2\2\2TU\7>\2\2UV\7"+
		"/\2\2V\n\3\2\2\2WX\7`\2\2XY\t\2\2\2Y\f\3\2\2\2Z[\7>\2\2[\\\7?\2\2\\\16"+
		"\3\2\2\2]^\7@\2\2^_\7?\2\2_\20\3\2\2\2`a\7?\2\2a\22\3\2\2\2bc\7-\2\2c"+
		"\24\3\2\2\2de\7/\2\2e\26\3\2\2\2fg\7,\2\2g\30\3\2\2\2hi\7\61\2\2i\32\3"+
		"\2\2\2jk\7B\2\2kl\7O\2\2lm\7c\2\2mn\7z\2\2n\34\3\2\2\2op\7B\2\2pq\7O\2"+
		"\2qr\7k\2\2rs\7p\2\2s\36\3\2\2\2ty\5#\22\2ux\5#\22\2vx\5%\23\2wu\3\2\2"+
		"\2wv\3\2\2\2x{\3\2\2\2yw\3\2\2\2yz\3\2\2\2z \3\2\2\2{y\3\2\2\2|~\5%\23"+
		"\2}|\3\2\2\2~\177\3\2\2\2\177}\3\2\2\2\177\u0080\3\2\2\2\u0080\u0087\3"+
		"\2\2\2\u0081\u0083\5\'\24\2\u0082\u0084\5%\23\2\u0083\u0082\3\2\2\2\u0084"+
		"\u0085\3\2\2\2\u0085\u0083\3\2\2\2\u0085\u0086\3\2\2\2\u0086\u0088\3\2"+
		"\2\2\u0087\u0081\3\2\2\2\u0087\u0088\3\2\2\2\u0088\u0092\3\2\2\2\u0089"+
		"\u008b\t\3\2\2\u008a\u008c\5\25\13\2\u008b\u008a\3\2\2\2\u008b\u008c\3"+
		"\2\2\2\u008c\u008e\3\2\2\2\u008d\u008f\5%\23\2\u008e\u008d\3\2\2\2\u008f"+
		"\u0090\3\2\2\2\u0090\u008e\3\2\2\2\u0090\u0091\3\2\2\2\u0091\u0093\3\2"+
		"\2\2\u0092\u0089\3\2\2\2\u0092\u0093\3\2\2\2\u0093\"\3\2\2\2\u0094\u0095"+
		"\t\4\2\2\u0095$\3\2\2\2\u0096\u0097\t\5\2\2\u0097&\3\2\2\2\u0098\u0099"+
		"\7\60\2\2\u0099(\3\2\2\2\u009a\u009b\7.\2\2\u009b*\3\2\2\2\u009c\u009d"+
		"\7<\2\2\u009d,\3\2\2\2\u009e\u009f\t\6\2\2\u009f.\3\2\2\2\u00a0\u00a1"+
		"\7(\2\2\u00a1\60\3\2\2\2\u00a2\u00a3\7~\2\2\u00a3\62\3\2\2\2\u00a4\u00a5"+
		"\7*\2\2\u00a5\64\3\2\2\2\u00a6\u00a7\7+\2\2\u00a7\66\3\2\2\2\u00a8\u00a9"+
		"\7}\2\2\u00a98\3\2\2\2\u00aa\u00ab\7\177\2\2\u00ab:\3\2\2\2\u00ac\u00ad"+
		"\7]\2\2\u00ad<\3\2\2\2\u00ae\u00af\7_\2\2\u00af>\3\2\2\2\u00b0\u00b1\7"+
		")\2\2\u00b1@\3\2\2\2\u00b2\u00b3\7$\2\2\u00b3B\3\2\2\2\u00b4\u00b6\t\7"+
		"\2\2\u00b5\u00b4\3\2\2\2\u00b6\u00b7\3\2\2\2\u00b7\u00b5\3\2\2\2\u00b7"+
		"\u00b8\3\2\2\2\u00b8\u00b9\3\2\2\2\u00b9\u00ba\b\"\2\2\u00baD\3\2\2\2"+
		"\u00bb\u00bc\7\61\2\2\u00bc\u00bd\7,\2\2\u00bd\u00c1\3\2\2\2\u00be\u00c0"+
		"\13\2\2\2\u00bf\u00be\3\2\2\2\u00c0\u00c3\3\2\2\2\u00c1\u00c2\3\2\2\2"+
		"\u00c1\u00bf\3\2\2\2\u00c2\u00c4\3\2\2\2\u00c3\u00c1\3\2\2\2\u00c4\u00c5"+
		"\7,\2\2\u00c5\u00c6\7\61\2\2\u00c6\u00c7\3\2\2\2\u00c7\u00c8\b#\2\2\u00c8"+
		"F\3\2\2\2\u00c9\u00ca\7\61\2\2\u00ca\u00cb\7\61\2\2\u00cb\u00cf\3\2\2"+
		"\2\u00cc\u00ce\n\b\2\2\u00cd\u00cc\3\2\2\2\u00ce\u00d1\3\2\2\2\u00cf\u00cd"+
		"\3\2\2\2\u00cf\u00d0\3\2\2\2\u00d0\u00d2\3\2\2\2\u00d1\u00cf\3\2\2\2\u00d2"+
		"\u00d3\b$\2\2\u00d3H\3\2\2\2\u00d4\u00d8\7%\2\2\u00d5\u00d7\n\b\2\2\u00d6"+
		"\u00d5\3\2\2\2\u00d7\u00da\3\2\2\2\u00d8\u00d6\3\2\2\2\u00d8\u00d9\3\2"+
		"\2\2\u00d9\u00db\3\2\2\2\u00da\u00d8\3\2\2\2\u00db\u00dc\b%\2\2\u00dc"+
		"J\3\2\2\2\17\2wy\177\u0085\u0087\u008b\u0090\u0092\u00b7\u00c1\u00cf\u00d8"+
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
