/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

grammar OnlinePSL;

import PSL;

/*
 * Parser Rules
 */

onlineProgram
    :   action+ EOF
    ;

action
    :   addAtom
    |   deleteAtom
    |   observeAtom
    |   updateObservation
    |   getAtom
    |   addRule
    |   activateRule
    |   deleteRule
    |   deactivateRule
    |   exit
    |   stop
    |   sync
    |   writeInferredPredicates
    ;

addAtom
    :   ADD_ATOM PARTITION atom number?
    ;

addRule
    :   ADD_RULE pslRule
    ;

deleteRule
    :   DELETE_RULE pslRule
    ;

activateRule
    :   ACTIVATE_RULE pslRule
    ;

deactivateRule
    :   DEACTIVATE_RULE pslRule
    ;

deleteAtom
    :   DELETE_ATOM PARTITION atom
    ;

exit
    :   EXIT
    ;

observeAtom
    :   OBSERVE_ATOM atom number
    ;

getAtom
    :   GET_ATOM atom
    ;

stop
    :   STOP
    ;

sync
    :   SYNC
    ;

updateObservation
    :   UPDATE_OBSERVATION atom number
    ;

writeInferredPredicates
    :   WRITE_INFERRED_PREDICATES STRING_LITERAL
    ;

/*
 * Lexer Rules
 */

PARTITION
    :   READ_PARTITION
    |   WRITE_PARTITION
    ;

ADD_ATOM
    :   A D D A T O M
    ;

ADD_RULE
    :   A D D R U L E
    ;

DELETE_RULE
    :   D E L E T E R U L E
    ;

DEACTIVATE_RULE
    :   D E A C T I V A T E R U L E
    ;

ACTIVATE_RULE
    :   A C T I V A T E R U L E
    ;

DELETE_ATOM
    :   D E L E T E A T O M
    ;

EXIT
    :   E X I T
    ;

OBSERVE_ATOM
    :   O B S E R V E A T O M
    ;

GET_ATOM
    :   G E T A T O M
    ;

READ_PARTITION
    :   R E A D
    ;

STOP
    :   S T O P
    ;

SYNC
    :   S Y N C
    ;

UPDATE_OBSERVATION
    :   U P D A T E A T O M
    ;

WRITE_INFERRED_PREDICATES
    :   W R I T E I N F E R R E D P R E D I C A T E S
    ;

WRITE_PARTITION
    :   W R I T E
    ;

fragment A
    :   'A'
    |   'a'
    ;

fragment B
    :   'B'
    |   'b'
    ;

fragment C
    :   'C'
    |   'c'
    ;

fragment D
    :   'D'
    |   'd'
    ;

fragment E
    :   'E'
    |   'e'
    ;

fragment F
    :   'F'
    |   'f'
    ;

fragment G
    :   'G'
    |   'g'
    ;

fragment H
    :   'H'
    |   'h'
    ;

fragment I
    :   'I'
    |   'i'
    ;

fragment J
    :   'J'
    |   'j'
    ;

fragment K
    :   'K'
    |   'k'
    ;

fragment L
    :   'L'
    |   'l'
    ;

fragment M
    :   'M'
    |   'm'
    ;

fragment N
    :   'N'
    |   'n'
    ;

fragment O
    :   'O'
    |   'o'
    ;

fragment P
    :   'P'
    |   'p'
    ;

fragment Q
    :   'Q'
    |   'q'
    ;

fragment R
    :   'R'
    |   'r'
    ;

fragment S
    :   'S'
    |   's'
    ;

fragment T
    :   'T'
    |   't'
    ;

fragment U
    :   'U'
    |   'u'
    ;

fragment V
    :   'V'
    |   'v'
    ;

fragment W
    :   'W'
    |   'w'
    ;

fragment X
    :   'X'
    |   'x'
    ;

fragment Y
    :   'Y'
    |   'y'
    ;

fragment Z
    :   'Z'
    |   'z'
    ;
