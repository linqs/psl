
grammar PSL;

program
	:	psl_rule+
	;

psl_rule
	:	logical_rule
	|	arithmetic_rule
	;

//
// Atoms and literals
//

predicate
	:	IDENTIFIER
	;

atom
	:	predicate LPAREN term (COMMA term)* RPAREN
	|	term TERM_OPERATOR term
	;

literal
	:	atom
	|	NOT literal
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

logical_rule
	:	weighted_logical_rule
	|	unweighted_logical_rule
	;

weighted_logical_rule
	:	weight_expression logical_rule_expression EXPONENT_EXPRESSION?
	;

unweighted_logical_rule
	:	logical_rule_expression PERIOD
	;

logical_rule_expression
	:	disjunctive_clause
	|	disjunctive_clause IMPLIED_BY conjunctive_clause
	|	conjunctive_clause THEN disjunctive_clause
	;

disjunctive_clause
	:	literal (OR literal)*
	;

conjunctive_clause
	:	literal (AND literal)*
	;

//
// Arithmetic rules
//

arithmetic_rule
	:	weighted_arithmetic_rule
	|	unweighted_arithmetic_rule
	;

weighted_arithmetic_rule
	:	weight_expression arithmetic_rule_expression EXPONENT_EXPRESSION? select_statement*
	;

unweighted_arithmetic_rule
	:	arithmetic_rule_expression PERIOD select_statement*
	;

arithmetic_rule_expression
	:	arithmetic_rule_operand (LINEAR_OPERATOR arithmetic_rule_operand)* ARITHMETIC_RULE_OPERATOR arithmetic_rule_operand (LINEAR_OPERATOR arithmetic_rule_operand)*
	;

arithmetic_rule_operand
	:	(coefficient MULT?)? sum_augmented_atom (DIV coefficient)?
	|	coefficient
	;

sum_augmented_atom
	:	predicate LPAREN PLUS? variable (COMMA PLUS? variable)* RPAREN
	;

coefficient
	:	number
	|	PIPE variable PIPE
	|	coefficient ARITHMETIC_OPERATOR coefficient
	|	COEFF_OPERATOR LBRACKET coefficient (COMMA coefficient)+ RBRACKET
	|	LPAREN coefficient RPAREN
	;

select_statement
	:	LBRACE variable COLON bool_expression RBRACE
	;

bool_expression
	:	literal
	|	LPAREN bool_expression RPAREN
	|	bool_expression OR bool_expression
	|	bool_expression AND bool_expression 
	;

//
// Common expressions
//

weight_expression
	:	NONNEGATIVE_NUMBER COLON
	;

EXPONENT_EXPRESSION
	:	CARET [12]
	;

//
// Logical operators
//

NOT
	: '~'
	| '!'
	;

AND
	: '&'
	| '&&'
	;

OR
	: '|'
	| '||'
	;

THEN
	:	'>>'
	|	'->'
	;

IMPLIED_BY
	:	'<<'
	|	'<-'
	;

//
// Term operators
//

TERM_OPERATOR
	:	TERM_EQUAL
	|	NOT_EQUAL
	;

TERM_EQUAL
	:	'=='
	;

NOT_EQUAL
	:	'!='
	;

//
// Arithmetic rule operators
//

ARITHMETIC_RULE_OPERATOR
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

ARITHMETIC_OPERATOR
	:	PLUS
	|	MINUS
	|	MULT
	|	DIV
	;

LINEAR_OPERATOR
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

COEFF_OPERATOR
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

CARET
	:	'^'
	;

COMMA
	:	','
	;

COLON
	:	':'
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
