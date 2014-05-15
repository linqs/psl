#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package ${package};

import edu.umd.cs.psl.groovy.*;
import edu.umd.cs.psl.ui.functions.textsimilarity.*;
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.argument.type.*;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.predicate.type.*;
import edu.umd.cs.psl.application.inference.LazyMPEInference;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.LazyMaxLikelihoodMPE;
import edu.umd.cs.psl.config.*;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type;

////////////////////////// initial setup ////////////////////////
ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("ontology-alignment")

def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "ontology-alignment")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)
PSLModel m = new PSLModel(this, data);

////////////////////////// predicate declaration ////////////////////////
println "\t\tDECLARING PREDICATES";

m.add predicate: "name"        , types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "subclass"    , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "fromOntology", types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "domainOf"    , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "rangeOf"     , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "hasType"     , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

//target predicate
m.add predicate: "similar"     , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

m.add function: "similarName" , implementation: new LevenshteinSimilarity();


///////////////////////////// rules ////////////////////////////////////
println "${symbol_escape}t${symbol_escape}tDECLARING RULES";

m.add rule : ( name(A,X) & name(B,Y) & (A ^ B) & similarName(X,Y) & hasType(A,T) & hasType(B,T) 
             & fromOntology(A,O) & fromOntology(B,Q) & (O ^ Q) ) >> similar(A,B),  weight : 8;

m.add rule : ( similar(A,B) & name(A,X) & name(B,Y) ) >> similarName(X,Y),  weight : 1;

m.add rule : (domainOf(R,A) & domainOf(T,B) & similar(A,B) & (R ^ T)) >> similar(R,T) , weight : 2;
m.add rule : (rangeOf(R,A)  & rangeOf(T,B)  & similar(A,B) & (R ^ T)) >> similar(R,T) , weight : 2;
m.add rule : (domainOf(R,A) & domainOf(T,B) & similar(R,T) & (A ^ B)) >> similar(A,B) , weight : 2;
m.add rule : (rangeOf(R,A)  & rangeOf(T,B)  & similar(R,T) & (A ^ B)) >> similar(A,B) , weight : 2;

// define set comparison
m.add setcomparison: "similarChildren" , using: SetComparison.Equality, on : similar;
def classID = data.getUniqueID("class");
m.add rule :  (similar(A,B) & hasType(A, classID) & hasType(B, classID) & (A ^ B )) >> 
              similarChildren( {A.subclass } , {B.subclass } ) , weight : 3;

// constraints
m.add PredicateConstraint.PartialFunctional , on : similar;
m.add PredicateConstraint.PartialInverseFunctional , on : similar;
m.add PredicateConstraint.Symmetric , on : similar;

// prior
m.add rule : ~similar(A,B), weight: 1;

// load data
def dir = 'data'+java.io.File.separator+'ontology'+java.io.File.separator;
def trainDir = dir+'train'+java.io.File.separator;

Partition trainPart = new Partition(0);
Partition truthPart = new Partition(1);

for (Predicate p : [domainOf,fromOntology,name,hasType,rangeOf,subclass])
{
        println "${symbol_escape}t${symbol_escape}t${symbol_escape}tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, trainPart)
	InserterUtils.loadDelimitedData(insert, trainDir+p.getName().toLowerCase()+".txt");
}

println "${symbol_escape}t${symbol_escape}t${symbol_escape}tREADING SIMILAR ...";
insert = data.getInserter(similar, truthPart)
InserterUtils.loadDelimitedDataTruth(insert, trainDir+"similar.txt");

//////////////////////////// weight learning ///////////////////////////
println "${symbol_escape}t${symbol_escape}tLEARNING WEIGHTS...";

Database trainDB = data.getDatabase(trainPart, [name, subclass, fromOntology, domainOf, rangeOf, hasType] as Set);
Database truthDB = data.getDatabase(truthPart, [similar] as Set);

LazyMaxLikelihoodMPE weightLearning = new LazyMaxLikelihoodMPE(m, trainDB, truthDB, config);
weightLearning.learn();
weightLearning.close();

println "${symbol_escape}t${symbol_escape}tLEARNING WEIGHTS DONE";

println m

/////////////////////////// test inference //////////////////////////////////
println "${symbol_escape}t${symbol_escape}tINFERRING...";

def testDir = dir+'test'+java.io.File.separator;
Partition testPart = new Partition(2);
for (Predicate p : [domainOf,fromOntology,name,hasType,rangeOf,subclass]) 
{
	insert = data.getInserter(p, testPart);
	InserterUtils.loadDelimitedData(insert, testDir+p.getName().toLowerCase()+".txt");
}

Database testDB = data.getDatabase(testPart, [name, subclass, fromOntology, domainOf, rangeOf, hasType] as Set);
LazyMPEInference inference = new LazyMPEInference(m, testDB, config);
inference.mpeInference();
inference.close();

println "${symbol_escape}t${symbol_escape}tINFERENCE DONE";

for (GroundAtom atom : Queries.getAllAtoms(testDB, similar))
	println atom.toString() + "${symbol_escape}t" + atom.getValue();
