#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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

import java.text.DecimalFormat;

import edu.umd.cs.psl.groovy.*;
import edu.umd.cs.psl.ui.functions.textsimilarity.*;
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.argument.type.*;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.predicate.type.*;
import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
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

m.add predicate: "name"        , types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "subclass"    , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "fromOntology", types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "domainOf"    , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "rangeOf"     , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "hasType"     , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

//target predicate
m.add predicate: "similar"     , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

m.add function: "similarName"  , implementation: new LevenshteinSimilarity();


///////////////////////////// rules ////////////////////////////////////

/* (O1-O2) means that O1 and O2 are not equal */

m.add rule : ( name(A,X) & name(B,Y) & similarName(X,Y) & hasType(A,T) & hasType(B,T) 
	& fromOntology(A,O1) & fromOntology(B,O2) & (O1-O2)) >> similar(A,B), weight : 8;

m.add rule : ( similar(A,B) & name(A,X) & name(B,Y)
	& fromOntology(A,O1) & fromOntology(B,O2) & (O1-O2)) >> similarName(X,Y), weight : 1;

m.add rule : (domainOf(R,A) & domainOf(T,B) & similar(A,B)
	& fromOntology(A,O1) & fromOntology(B,O2) & (O1-O2)) >> similar(R,T), weight : 2;

m.add rule : (rangeOf(R,A)  & rangeOf(T,B)  & similar(A,B)
	& fromOntology(A,O1) & fromOntology(B,O2) & (O1-O2)) >> similar(R,T), weight : 2;

m.add rule : (domainOf(R,A) & domainOf(T,B) & similar(R,T)
	& fromOntology(A,O1) & fromOntology(B,O2) & (O1-O2)) >> similar(A,B), weight : 2;

m.add rule : (rangeOf(R,A)  & rangeOf(T,B)  & similar(R,T)
	& fromOntology(A,O1) & fromOntology(B,O2) & (O1-O2)) >> similar(A,B), weight : 2;

GroundTerm classID = data.getUniqueID("class");
m.add rule : (similar(A,B) & hasType(A, classID) & hasType(B, classID)
	& subclass(A, S1) & subclass(B, S2)
	& fromOntology(A,O1) & fromOntology(B,O2) & (O1-O2)) >> similar(S1, S2), weight: 3;

// constraints
m.add PredicateConstraint.PartialFunctional , on : similar;
m.add PredicateConstraint.PartialInverseFunctional , on : similar;
m.add PredicateConstraint.Symmetric , on : similar;

// prior
m.add rule : ~similar(A,B), weight: 1;

//////////////////////////// data setup ///////////////////////////

/* Loads data */
def dir = 'data'+java.io.File.separator+'ontology'+java.io.File.separator;
def trainDir = dir+'train'+java.io.File.separator;

Partition trainObservations = new Partition(0);
Partition trainPredictions = new Partition(1);
Partition truth = new Partition(2);

for (Predicate p : [domainOf,fromOntology,name,hasType,rangeOf,subclass])
{
    insert = data.getInserter(p, trainObservations)
	InserterUtils.loadDelimitedData(insert, trainDir+p.getName().toLowerCase()+".txt");
}

insert = data.getInserter(similar, truth)
InserterUtils.loadDelimitedDataTruth(insert, trainDir+"similar.txt");

Database trainDB = data.getDatabase(trainPredictions, [name, subclass, fromOntology, domainOf, rangeOf, hasType] as Set, trainObservations);
populateSimilar(trainDB);

Database truthDB = data.getDatabase(truth, [similar] as Set);

//////////////////////////// weight learning ///////////////////////////
println "LEARNING WEIGHTS...";

MaxLikelihoodMPE weightLearning = new MaxLikelihoodMPE(m, trainDB, truthDB, config);
weightLearning.learn();
weightLearning.close();

println "LEARNING WEIGHTS DONE";

println m

/////////////////////////// test setup //////////////////////////////////

def testDir = dir+'test'+java.io.File.separator;
Partition testObservations = new Partition(3);
Partition testPredictions = new Partition(4);
for (Predicate p : [domainOf,fromOntology,name,hasType,rangeOf,subclass]) 
{
	insert = data.getInserter(p, testObservations);
	InserterUtils.loadDelimitedData(insert, testDir+p.getName().toLowerCase()+".txt");
}

Database testDB = data.getDatabase(testPredictions, [name, subclass, fromOntology, domainOf, rangeOf, hasType] as Set, testObservations);
populateSimilar(testDB);

/////////////////////////// test inference //////////////////////////////////
println "INFERRING...";

MPEInference inference = new MPEInference(m, testDB, config);
inference.mpeInference();
inference.close();

println "INFERENCE DONE";

DecimalFormat formatter = new DecimalFormat("${symbol_pound}.${symbol_pound}${symbol_pound}");
for (GroundAtom atom : Queries.getAllAtoms(testDB, similar))
	println atom.toString() + ": " + formatter.format(atom.getValue());

/**
 * Populates all the similiar atoms between the concepts of two ontologies using
 * the fromOntology predicate.
 * 
 * @param db  The database to populate. It should contain the fromOntology atoms
 */
void populateSimilar(Database db) {
	/* Collects the ontology concepts */
	Set<GroundAtom> concepts = Queries.getAllAtoms(db, fromOntology);
	Set<GroundTerm> o1 = new HashSet<GroundTerm>();
	Set<GroundTerm> o2 = new HashSet<GroundTerm>();
	for (GroundAtom atom : concepts) {
		if (atom.getArguments()[1].toString().equals("o1"))
			o1.add(atom.getArguments()[0]);
		else
			o2.add(atom.getArguments()[0]);
	}
	
	/* Populates manually (as opposed to using DatabasePopulator) */
	for (GroundTerm o1Concept : o1) {
		for (GroundTerm o2Concept : o2) {
			((RandomVariableAtom) db.getAtom(similar, o1Concept, o2Concept)).commitToDB();
			((RandomVariableAtom) db.getAtom(similar, o2Concept, o1Concept)).commitToDB();
		}
	}
}

