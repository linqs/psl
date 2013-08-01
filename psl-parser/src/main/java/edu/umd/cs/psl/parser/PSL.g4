
grammar PSL;

program
    :   (predicateDefinition | kernel | constraint)+
    ;

predicateDefinition
	:	predicate '(' argumentType (',' argumentType)* ')'
	;

// RULES

kernel
    :   weight expression SQUARED?
    ;

expression
    :   NOT expression
    |   '(' expression ')'
    |   argument NOTEQUAL argument
    |   argument SYMMETRIC argument
    |   expression AND expression
    |   expression OR expression
    |   expression THEN expression
    |   expression IMPLIEDBY expression
    |   atom
    ;

atom
    :   predicate '(' argument (',' argument)* ')'
    ;

argument
    :   variable
    |   constant
    ;

variable
    :   IDENTIFIER
    ;

constant
    :   intConstant
    |   strConstant
    ;

intConstant
    :   NUMBER
    ;

strConstant
    :   STRING
    ;

weight
    : OPENBRACE NUMBER CLOSEBRACE
    | OPENBRACE CONSTRAINT CLOSEBRACE
    ;

predicate
    :   IDENTIFIER
    ;

// CONSTRAINT

constraint
    :   '{' CONSTRAINT '}' constraintType 'on' predicate '(' argumentType ( ',' argumentType)* ')'
    ;

constraintType
    :   'Functional'
    |   'PartialFunctional'
    ;
        
argumentType
    :   'UniqueID'
    |   'String'
    |   'Integer'
    |   'Double'
    ;

ID_ARG : 'UniqueID' ;
STR_ARG : 'String' ;
INT_ARG : 'Integer' ;
DBL_ARG : 'Double' ;

ON: 'on' ;

FUNCTIONAL_CONSTRAINT : 'Functional';
PARTIAL_FUNCTIONAL_CONSTRAINT : 'PartialFunctional';
INVERSE_FUNCTIONAL_CONSTRAINT : 'InverseFunctional';
INVERSE_PARTIAL_FUNCTIONAL_CONSTRAINT : 'InversePartialFunctional';

COMMA: ',' ;

OPENBRACE: '{' ;
CLOSEBRACE: '}';

CONSTRAINT
    : 'constraint'
    ;

SQUARED
    : '{squared}'
    ;

OPENPAR
    : '('
    ;

CLOSEPAR
    :   ')'
    ;

NOT
    : '~'
    | '!'
    ;

AND
    : '&'
    ;

OR
    : '|'
    ;

THEN
    : '>>'
    | '->'
    ;

IMPLIEDBY
    : '<<'
    | '<-'
    ;

NOTEQUAL
    :   '-'
    |   '!='
    ;

SYMMETRIC
    : '^'
    ;

IDENTIFIER
	:	Letter LetterOrDigit*
	;


fragment
Letter
	:	[a-zA-Z$_] // these are the "java letters" below 0xFF
	;

fragment
LetterOrDigit
	:	[a-zA-Z0-9$_] // these are the "java letters or digits" below 0xFF
	;

NUMBER
    :   '-'? DIGIT+ '.'? DIGIT* (('e'|'E') '-' DIGIT+)?
    ;

STRING
    :	'\"' .*? '\"'
    |   '\'' .*? '\''
    ;

fragment
DOUBLE_QUOTE: '\"' ;

fragment
SINGLE_QUOTE: '\'' ;


fragment
DIGIT 
    : [0-9]
    ;

//
// Whitespace and comments
// (from https://github.com/antlr/grammars-v4)
//

WS  :  [ \t\r\n\u000C]+ -> skip
    ;

COMMENT
    :   '/*' .*? '*/' -> skip
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> skip
    ;

PYTHON_COMMENT
    :   '#' ~[\r\n]* -> skip
    ;
    
MODEL_HEADER
	: 'Model:' -> skip
	;


