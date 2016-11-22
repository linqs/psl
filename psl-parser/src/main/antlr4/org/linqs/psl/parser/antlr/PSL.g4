
grammar PSL;

program
	:	pslRule+ EOF
	;

pslRule
	:	logicalRule
	|	arithmeticRule
	;

// The inner part of a rule (logical or arithmetic).
// This cannot be reached from a default PSL program and must be asked for specifically.
pslRulePartial
	:	logicalRule EOF
	|	arithmeticRule EOF
	|	logicalRuleExpression EOF
	|	arithmeticRuleExpression selectStatement* EOF
	;

//
// Atoms and literals
//

predicate
	:	IDENTIFIER
	;

atom
	:	predicate LPAREN term (COMMA term)* RPAREN
	|	term termOperator term
	;

literal
	:	atom
	|	not literal
	;

term
	:	variable
	|	constant
	;

variable
	:	IDENTIFIER
	;

constant
	:	SINGLE_QUOTE IDENTIFIER SINGLE_QUOTE
	|	DOUBLE_QUOTE IDENTIFIER DOUBLE_QUOTE
	;

//
// Logical rules
//

logicalRule
	:	weightedLogicalRule
	|	unweightedLogicalRule
	;

weightedLogicalRule
	:	weightExpression logicalRuleExpression EXPONENT_EXPRESSION?
	;

unweightedLogicalRule
	:	logicalRuleExpression PERIOD
	;

logicalRuleExpression
	:	disjunctiveClause
	|	disjunctiveClause impliedBy conjunctiveClause
	|	conjunctiveClause then disjunctiveClause
	;

disjunctiveClause
	:	literal (or literal)*
	;

conjunctiveClause
	:	literal (and literal)*
	;

//
// Arithmetic rules
//

arithmeticRule
	:	weightedArithmeticRule
	|	unweightedArithmeticRule
	;

weightedArithmeticRule
	:	weightExpression arithmeticRuleExpression EXPONENT_EXPRESSION? selectStatement*
	;

unweightedArithmeticRule
	:	arithmeticRuleExpression PERIOD selectStatement*
	;

arithmeticRuleExpression
	:	arithmeticRuleOperand (linearOperator arithmeticRuleOperand)* arithmeticRuleRelation arithmeticRuleOperand (linearOperator arithmeticRuleOperand)*
	;

arithmeticRuleOperand
	:	(coefficient MULT?)? (summationAtom | atom) (DIV coefficient)?
	|	coefficient
	;

summationAtom
	:	predicate LPAREN (summationVariable | term) (COMMA (summationVariable | term))* RPAREN
	;

summationVariable
	:	PLUS IDENTIFIER
	;

coefficient
	:	number
	|	PIPE variable PIPE
	|	coefficient arithmeticOperator coefficient
	|	coeffOperator LBRACKET coefficient COMMA coefficient RBRACKET
	|	LPAREN coefficient RPAREN
	;

selectStatement
	:	LBRACE variable COLON boolExpression RBRACE
	;

boolExpression
	:	literal
	|	LPAREN boolExpression RPAREN
	|	boolExpression or boolExpression
	|	boolExpression and boolExpression
	;

//
// Common expressions
//

weightExpression
	:	NONNEGATIVE_NUMBER COLON
	;

EXPONENT_EXPRESSION
	:	'^' [12]
	;

//
// Logical operators
//

not
	:	NEGATION
	;

and
	:	AMPERSAND
	|	AMPERSAND AMPERSAND
	;

or
	:	PIPE
	|	PIPE PIPE
	;

then
	:	'>>'
	|	'->'
	;

impliedBy
	:	'<<'
	|	'<-'
	;

//
// Term operators
//

termOperator
	:	termEqual
	|	notEqual
	;

termEqual
	:	EQUAL EQUAL
	;

notEqual
	:	NEGATION EQUAL
	;

//
// Arithmetic rule relations
//

arithmeticRuleRelation
	:	LESS_THAN_EQUAL
	|	GREATER_THAN_EQUAL
	|	EQUAL
	;

LESS_THAN_EQUAL
	:	'<='
	;

GREATER_THAN_EQUAL
	:	'>='
	;

EQUAL
	:	'='
	;

//
// Arithmetic operators
//

arithmeticOperator
	:	PLUS
	|	MINUS
	|	MULT
	|	DIV
	;

linearOperator
	:	PLUS
	|	MINUS
	;

PLUS
	:	'+'
	;

MINUS
	:	'-'
	;

MULT
	:	'*'
	;

DIV
	:	'/'
	;

//
// Additional coefficient operators
//

coeffOperator
	:	MAX
	|	MIN
	;

MAX
	:	'@Max'
	;

MIN
	:	'@Min'
	;

//
// Identifiers and numbers
//

number
	:	MINUS? NONNEGATIVE_NUMBER
	;

IDENTIFIER
	:	LETTER (LETTER | DIGIT)*
	;

NONNEGATIVE_NUMBER
	:	DIGIT+ (PERIOD DIGIT+)? ([eE] MINUS? DIGIT+)?
	;

fragment
LETTER
	:	[a-zA-Z$_] // these are the "java letters" below 0xFF
	;

fragment
DIGIT
	:	[0-9]
	;

//
// Common tokens
//

PERIOD
	:	'.'
	;

COMMA
	:	','
	;

COLON
	:	':'
	;

NEGATION
	:	'~'
	|	'!'
	;

AMPERSAND
	:	'&'
	;

PIPE
	:	'|'
	;

LPAREN
	:	'('
	;

RPAREN
	:	')'
	;

LBRACE
	:	'{'
	;

RBRACE
	:	'}'
	;

LBRACKET
	:	'['
	;

RBRACKET
	:	']'
	;

SINGLE_QUOTE
	:	'\''
	;

DOUBLE_QUOTE
	:	'\"'
	;

//
// Whitespace and comments
// (from https://github.com/antlr/grammars-v4)
//

WS
	:	[ \t\r\n\u000C]+ -> skip
	;

COMMENT
	:	'/*' .*? '*/' -> skip
	;

LINE_COMMENT
	:	'//' ~[\r\n]* -> skip
	;

PYTHON_COMMENT
	:	'#' ~[\r\n]* -> skip
	;
