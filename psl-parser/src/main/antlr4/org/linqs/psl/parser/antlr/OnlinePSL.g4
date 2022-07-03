/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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

GET_ATOM
    :   G E T A T O M
    ;

OBSERVE_ATOM
    :   O B S E R V E A T O M
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

fragment A : [aA]; // match either an 'a' or 'A'
fragment B : [bB];
fragment C : [cC];
fragment D : [dD];
fragment E : [eE];
fragment F : [fF];
fragment G : [gG];
fragment H : [hH];
fragment I : [iI];
fragment J : [jJ];
fragment K : [kK];
fragment L : [lL];
fragment M : [mM];
fragment N : [nN];
fragment O : [oO];
fragment P : [pP];
fragment Q : [qQ];
fragment R : [rR];
fragment S : [sS];
fragment T : [tT];
fragment U : [uU];
fragment V : [vV];
fragment W : [wW];
fragment X : [xX];
fragment Y : [yY];
fragment Z : [zZ];
