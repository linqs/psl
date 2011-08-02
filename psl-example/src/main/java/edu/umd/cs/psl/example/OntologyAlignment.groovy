/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.example;

import edu.umd.cs.psl.groovy.*;
import edu.umd.cs.psl.database.RDBMS.DatabaseDriver;
import edu.umd.cs.psl.ui.functions.textsimilarity.*;
import edu.umd.cs.psl.model.function.AttributeSimilarityFunction;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.argument.type.*;
import edu.umd.cs.psl.model.predicate.type.*;
import edu.umd.cs.psl.config.*;

PSLModel m = new PSLModel(this);

////////////////////////// predicate declaration ////////////////////////
println "\t\tDECLARING PREDICATES...\n";

m.add predicate: "name"        , person    : Entity,  string   : Text   ,  type: PredicateTypes.BooleanTruth;
m.add predicate: "subclass"    , class1    : Entity,  class2   : Entity ,  type: PredicateTypes.BooleanTruth;
m.add predicate: "fromOntology", entity    : Entity,  ontology : Entity ,  type: PredicateTypes.BooleanTruth;
m.add predicate: "domainOf"    , property1 : Entity,  class1   : Entity ,  type: PredicateTypes.BooleanTruth;
m.add predicate: "rangeOf"     , property1 : Entity,  class1   : Entity ,  type: PredicateTypes.BooleanTruth;
m.add predicate: "hasType"     , entity    : Entity,  type1    : Text   ,  type: PredicateTypes.BooleanTruth;

//target predicate
m.add predicate: "similar"     , entity1   : Entity,  entity2  : Entity ,  open: true, type: PredicateTypes.SoftTruth;

m.add function: "similarName" , name1: Text, name2: Text, implementation: new LevensteinStringSimilarity();


///////////////////////////// rules ////////////////////////////////////
println "\t\tREADING RULES...\n";

m.add rule : ( name(A,X) & name(B,Y) & (A ^ B) & similarName(X,Y) & hasType(A,T) & hasType(B,T) 
             & fromOntology(A,O) & fromOntology(B,Q) & (O ^ Q) ) >> similar(A,B),  weight : 8;

m.add rule : ( similar(A,B) & name(A,X) & name(B,Y) ) >> similarName(X,Y),  weight : 1;

m.add rule : (domainOf(R,A) & domainOf(T,B) & similar(A,B) & (R ^ T)) >> similar(R,T) , weight : 2;
m.add rule : (rangeOf(R,A)  & rangeOf(T,B)  & similar(A,B) & (R ^ T)) >> similar(R,T) , weight : 2;
m.add rule : (domainOf(R,A) & domainOf(T,B) & similar(R,T) & (A ^ B)) >> similar(A,B) , weight : 2;
m.add rule : (rangeOf(R,A)  & rangeOf(T,B)  & similar(R,T) & (A ^ B)) >> similar(A,B) , weight : 2;

//define set comparison
m.add setcomparison: "similarChildren" , using: SetComparison.Equality, on : similar;
m.add rule :  (similar(A,B) & hasType(A,"class") & hasType(B,"class") & (A ^ B )) >> 
              similarChildren( {A.subclass } , {B.subclass } ) , weight : 3;

//constraints
m.add PredicateConstraint.PartialFunctional , on : similar;
m.add PredicateConstraint.PartialInverseFunctional , on : similar;


//prior
m.add Prior.Simple, on : similar, weight: 1;

///////////////////////////// database /////////////////////////////////
println "\t\tCREATING DATABASE...\n";

DataStore data = new RelationalDataStore(m, entityid : 'string')
data.setup db : DatabaseDriver.H2
println "\t\t\tDONE NEWING DATABASE...\n";



//load data
def dir = 'data'+java.io.File.separator+'ontology'+java.io.File.separator;
def trainDir = dir+'train'+java.io.File.separator;
for (Predicate p : [domainOf,fromOntology,name,hasType,rangeOf,subclass])
{
        println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p)
	insert.loadFromFile(trainDir+p.getName()+".txt");
}

println "\t\t\tREADING target predicate file ...\n";
insert = data.getInserter(similar,2)
insert.loadFromFileWithTruth(trainDir+"similar.txt","\t");

ConfigManager cm = ConfigManager.getManager();
ConfigBundle exampleBundle = cm.getBundle("example");

//////////////////////////// weight learning ///////////////////////////
println "\t\tLEARNING WEIGHTS...";

WeightLearningConfiguration config = new WeightLearningConfiguration();
config.setLearningType(WeightLearningConfiguration.Type.Perceptron);
config.setInitialParameter(0.0);
m.learn data, evidence : 1, infered: 2, close : similar, config: config
println m

println "\t\tLEARNING WEIGHTS DONE";



/////////////////////////// inference //////////////////////////////////
println "\t\tINFERRING...";

def testDir = dir+'test'+java.io.File.separator;
for (Predicate p : [domainOf,fromOntology,name,hasType,rangeOf,subclass]) 
{
	insert = data.getInserter(p,5)
	insert.loadFromFile(testDir+p.getName()+".txt");
}

result = m.mapInference(data.getDatabase(write: 1004, read : 5), new PSLCoreConfiguration(), exampleBundle);

println "\t\tINFERENCE DONE";



println "\t\tRESULTS"
result.printAtoms(similar,true);
