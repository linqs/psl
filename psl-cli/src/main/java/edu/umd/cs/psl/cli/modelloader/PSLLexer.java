// Generated from PSL.g4 by ANTLR 4.5.3
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
	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] ruleNames = {
		"EXPONENT_EXPRESSION", "NOT", "AND", "OR", "THEN", "IMPLIED_BY", "TERM_EQUAL", 
		"NOT_EQUAL", "LESS_THAN_EQUAL", "GREATER_THAN_EQUAL", "EQUAL", "PLUS", 
		"MINUS", "MULT", "DIV", "MAX", "MIN", "IDENTIFIER", "NONNEGATIVE_NUMBER", 
		"LETTER", "DIGIT", "PERIOD", "COMMA", "COLON", "PIPE", "LPAREN", "RPAREN", 
		"LBRACE", "RBRACE", "LBRACKET", "RBRACKET", "SINGLE_QUOTE", "DOUBLE_QUOTE", 
		"WS", "COMMENT", "LINE_COMMENT", "PYTHON_COMMENT"
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
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2%\u00ed\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\3\2\3\2\3\2\3\3\3\3\3\4\3\4\3\4\5"+
		"\4V\n\4\3\5\3\5\3\5\5\5[\n\5\3\6\3\6\3\6\3\6\5\6a\n\6\3\7\3\7\3\7\3\7"+
		"\5\7g\n\7\3\b\3\b\3\b\3\t\3\t\3\t\3\n\3\n\3\n\3\13\3\13\3\13\3\f\3\f\3"+
		"\r\3\r\3\16\3\16\3\17\3\17\3\20\3\20\3\21\3\21\3\21\3\21\3\21\3\22\3\22"+
		"\3\22\3\22\3\22\3\23\3\23\3\23\7\23\u008c\n\23\f\23\16\23\u008f\13\23"+
		"\3\24\6\24\u0092\n\24\r\24\16\24\u0093\3\24\3\24\6\24\u0098\n\24\r\24"+
		"\16\24\u0099\5\24\u009c\n\24\3\24\3\24\5\24\u00a0\n\24\3\24\6\24\u00a3"+
		"\n\24\r\24\16\24\u00a4\5\24\u00a7\n\24\3\25\3\25\3\26\3\26\3\27\3\27\3"+
		"\30\3\30\3\31\3\31\3\32\3\32\3\33\3\33\3\34\3\34\3\35\3\35\3\36\3\36\3"+
		"\37\3\37\3 \3 \3!\3!\3\"\3\"\3#\6#\u00c6\n#\r#\16#\u00c7\3#\3#\3$\3$\3"+
		"$\3$\7$\u00d0\n$\f$\16$\u00d3\13$\3$\3$\3$\3$\3$\3%\3%\3%\3%\7%\u00de"+
		"\n%\f%\16%\u00e1\13%\3%\3%\3&\3&\7&\u00e7\n&\f&\16&\u00ea\13&\3&\3&\3"+
		"\u00d1\2\'\3\3\5\4\7\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33"+
		"\17\35\20\37\21!\22#\23%\24\'\25)\2+\2-\26/\27\61\30\63\31\65\32\67\33"+
		"9\34;\35=\36?\37A C!E\"G#I$K%\3\2\t\3\2\63\64\4\2##\u0080\u0080\4\2GG"+
		"gg\6\2&&C\\aac|\3\2\62;\5\2\13\f\16\17\"\"\4\2\f\f\17\17\u00fa\2\3\3\2"+
		"\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17"+
		"\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2"+
		"\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3"+
		"\2\2\2\2\'\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3\2\2\2\2\65"+
		"\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2;\3\2\2\2\2=\3\2\2\2\2?\3\2\2\2\2A\3"+
		"\2\2\2\2C\3\2\2\2\2E\3\2\2\2\2G\3\2\2\2\2I\3\2\2\2\2K\3\2\2\2\3M\3\2\2"+
		"\2\5P\3\2\2\2\7U\3\2\2\2\tZ\3\2\2\2\13`\3\2\2\2\rf\3\2\2\2\17h\3\2\2\2"+
		"\21k\3\2\2\2\23n\3\2\2\2\25q\3\2\2\2\27t\3\2\2\2\31v\3\2\2\2\33x\3\2\2"+
		"\2\35z\3\2\2\2\37|\3\2\2\2!~\3\2\2\2#\u0083\3\2\2\2%\u0088\3\2\2\2\'\u0091"+
		"\3\2\2\2)\u00a8\3\2\2\2+\u00aa\3\2\2\2-\u00ac\3\2\2\2/\u00ae\3\2\2\2\61"+
		"\u00b0\3\2\2\2\63\u00b2\3\2\2\2\65\u00b4\3\2\2\2\67\u00b6\3\2\2\29\u00b8"+
		"\3\2\2\2;\u00ba\3\2\2\2=\u00bc\3\2\2\2?\u00be\3\2\2\2A\u00c0\3\2\2\2C"+
		"\u00c2\3\2\2\2E\u00c5\3\2\2\2G\u00cb\3\2\2\2I\u00d9\3\2\2\2K\u00e4\3\2"+
		"\2\2MN\7`\2\2NO\t\2\2\2O\4\3\2\2\2PQ\t\3\2\2Q\6\3\2\2\2RV\7(\2\2ST\7("+
		"\2\2TV\7(\2\2UR\3\2\2\2US\3\2\2\2V\b\3\2\2\2W[\7~\2\2XY\7~\2\2Y[\7~\2"+
		"\2ZW\3\2\2\2ZX\3\2\2\2[\n\3\2\2\2\\]\7@\2\2]a\7@\2\2^_\7/\2\2_a\7@\2\2"+
		"`\\\3\2\2\2`^\3\2\2\2a\f\3\2\2\2bc\7>\2\2cg\7>\2\2de\7>\2\2eg\7/\2\2f"+
		"b\3\2\2\2fd\3\2\2\2g\16\3\2\2\2hi\7?\2\2ij\7?\2\2j\20\3\2\2\2kl\7#\2\2"+
		"lm\7?\2\2m\22\3\2\2\2no\7>\2\2op\7?\2\2p\24\3\2\2\2qr\7@\2\2rs\7?\2\2"+
		"s\26\3\2\2\2tu\7?\2\2u\30\3\2\2\2vw\7-\2\2w\32\3\2\2\2xy\7/\2\2y\34\3"+
		"\2\2\2z{\7,\2\2{\36\3\2\2\2|}\7\61\2\2} \3\2\2\2~\177\7B\2\2\177\u0080"+
		"\7O\2\2\u0080\u0081\7c\2\2\u0081\u0082\7z\2\2\u0082\"\3\2\2\2\u0083\u0084"+
		"\7B\2\2\u0084\u0085\7O\2\2\u0085\u0086\7k\2\2\u0086\u0087\7p\2\2\u0087"+
		"$\3\2\2\2\u0088\u008d\5)\25\2\u0089\u008c\5)\25\2\u008a\u008c\5+\26\2"+
		"\u008b\u0089\3\2\2\2\u008b\u008a\3\2\2\2\u008c\u008f\3\2\2\2\u008d\u008b"+
		"\3\2\2\2\u008d\u008e\3\2\2\2\u008e&\3\2\2\2\u008f\u008d\3\2\2\2\u0090"+
		"\u0092\5+\26\2\u0091\u0090\3\2\2\2\u0092\u0093\3\2\2\2\u0093\u0091\3\2"+
		"\2\2\u0093\u0094\3\2\2\2\u0094\u009b\3\2\2\2\u0095\u0097\5-\27\2\u0096"+
		"\u0098\5+\26\2\u0097\u0096\3\2\2\2\u0098\u0099\3\2\2\2\u0099\u0097\3\2"+
		"\2\2\u0099\u009a\3\2\2\2\u009a\u009c\3\2\2\2\u009b\u0095\3\2\2\2\u009b"+
		"\u009c\3\2\2\2\u009c\u00a6\3\2\2\2\u009d\u009f\t\4\2\2\u009e\u00a0\5\33"+
		"\16\2\u009f\u009e\3\2\2\2\u009f\u00a0\3\2\2\2\u00a0\u00a2\3\2\2\2\u00a1"+
		"\u00a3\5+\26\2\u00a2\u00a1\3\2\2\2\u00a3\u00a4\3\2\2\2\u00a4\u00a2\3\2"+
		"\2\2\u00a4\u00a5\3\2\2\2\u00a5\u00a7\3\2\2\2\u00a6\u009d\3\2\2\2\u00a6"+
		"\u00a7\3\2\2\2\u00a7(\3\2\2\2\u00a8\u00a9\t\5\2\2\u00a9*\3\2\2\2\u00aa"+
		"\u00ab\t\6\2\2\u00ab,\3\2\2\2\u00ac\u00ad\7\60\2\2\u00ad.\3\2\2\2\u00ae"+
		"\u00af\7.\2\2\u00af\60\3\2\2\2\u00b0\u00b1\7<\2\2\u00b1\62\3\2\2\2\u00b2"+
		"\u00b3\7~\2\2\u00b3\64\3\2\2\2\u00b4\u00b5\7*\2\2\u00b5\66\3\2\2\2\u00b6"+
		"\u00b7\7+\2\2\u00b78\3\2\2\2\u00b8\u00b9\7}\2\2\u00b9:\3\2\2\2\u00ba\u00bb"+
		"\7\177\2\2\u00bb<\3\2\2\2\u00bc\u00bd\7]\2\2\u00bd>\3\2\2\2\u00be\u00bf"+
		"\7_\2\2\u00bf@\3\2\2\2\u00c0\u00c1\7)\2\2\u00c1B\3\2\2\2\u00c2\u00c3\7"+
		"$\2\2\u00c3D\3\2\2\2\u00c4\u00c6\t\7\2\2\u00c5\u00c4\3\2\2\2\u00c6\u00c7"+
		"\3\2\2\2\u00c7\u00c5\3\2\2\2\u00c7\u00c8\3\2\2\2\u00c8\u00c9\3\2\2\2\u00c9"+
		"\u00ca\b#\2\2\u00caF\3\2\2\2\u00cb\u00cc\7\61\2\2\u00cc\u00cd\7,\2\2\u00cd"+
		"\u00d1\3\2\2\2\u00ce\u00d0\13\2\2\2\u00cf\u00ce\3\2\2\2\u00d0\u00d3\3"+
		"\2\2\2\u00d1\u00d2\3\2\2\2\u00d1\u00cf\3\2\2\2\u00d2\u00d4\3\2\2\2\u00d3"+
		"\u00d1\3\2\2\2\u00d4\u00d5\7,\2\2\u00d5\u00d6\7\61\2\2\u00d6\u00d7\3\2"+
		"\2\2\u00d7\u00d8\b$\2\2\u00d8H\3\2\2\2\u00d9\u00da\7\61\2\2\u00da\u00db"+
		"\7\61\2\2\u00db\u00df\3\2\2\2\u00dc\u00de\n\b\2\2\u00dd\u00dc\3\2\2\2"+
		"\u00de\u00e1\3\2\2\2\u00df\u00dd\3\2\2\2\u00df\u00e0\3\2\2\2\u00e0\u00e2"+
		"\3\2\2\2\u00e1\u00df\3\2\2\2\u00e2\u00e3\b%\2\2\u00e3J\3\2\2\2\u00e4\u00e8"+
		"\7%\2\2\u00e5\u00e7\n\b\2\2\u00e6\u00e5\3\2\2\2\u00e7\u00ea\3\2\2\2\u00e8"+
		"\u00e6\3\2\2\2\u00e8\u00e9\3\2\2\2\u00e9\u00eb\3\2\2\2\u00ea\u00e8\3\2"+
		"\2\2\u00eb\u00ec\b&\2\2\u00ecL\3\2\2\2\23\2UZ`f\u008b\u008d\u0093\u0099"+
		"\u009b\u009f\u00a4\u00a6\u00c7\u00d1\u00df\u00e8\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}