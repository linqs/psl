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

import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.groovy.PredicateConstraint;
import edu.umd.cs.psl.groovy.SetComparison;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;

/* 
 * The first thing we need to do is initialize a ConfigBundle and a DataStore
 */

/*
 * A ConfigBundle is a set of key-value pairs containing configuration options.
 * One place these can be defined is in ${artifactId}/src/main/resources/psl.properties
 */
ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("basic-example")

/* Uses H2 as a DataStore and stores it in a temp. directory by default */
def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "basic-example")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)

/*
 * Now we can initialize a PSLModel, which is the core component of PSL.
 * The first constructor argument is the context in which the PSLModel is defined.
 * The second argument is the DataStore we will be using.
 */
PSLModel m = new PSLModel(this, data)

/*
 * In this example program, the task is to align two social networks, by
 * identifying which pairs of users are the same across networks.
 */

/* 
 * We create four predicates in the model, giving their names and list of argument types
 */
m.add predicate: "Network",    types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "Name",       types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "Knows",      types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "SamePerson", types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

/*
 * Now, we define a string similarity function bound to a predicate.
 * Note that we can use any implementation of ExternalFunction that acts on two strings!
 */
m.add function: "SameName" , implementation: new LevenshteinSimilarity()
/* Also, try: new MyStringSimilarity(), see end of file */

/* 
 * Having added all the predicates we need to represent our problem, we finally
 * add some rules into the model. Rules are defined using a logical syntax.
 * 
 * Uppercase letters are variables and the predicates used in the rules below
 * are those defined above. The character '&' denotes a conjunction where '>>'
 * denotes an implication.
 * 
 * Each rule is given a weight that is either the weight used for inference or
 * an initial guess for the starting point of weight learning.
 */

/*
 * We also create constants to refer to each social network.
 */
GroundTerm snA = data.getUniqueID(1);
GroundTerm snB = data.getUniqueID(2);

/*
 * Our first rule says that users with similar names are likely the same person
 */
m.add rule : ( Network(A, snA) & Network(B, snB) & Name(A,X) & Name(B,Y)
	& SameName(X,Y) ) >> SamePerson(A,B),  weight : 5

/* 
 * In this rule, we use the social network to propagate SamePerson information.
 */
m.add rule : ( Network(A, snA) & Network(B, snB) & SamePerson(A,B) & Knows(A, Friend1)
	& Knows(B, Friend2) ) >> SamePerson(Friend1, Friend2) , weight : 3.2

/* 
 * Next, we define some constraints for our model. In this case, we restrict that
 * each person can be aligned to at most one other person in the other social network.
 * To do so, we define two partial functional constraints where the latter is on
 * the inverse. We also say that samePerson must be symmetric,
 * i.e., samePerson(p1, p2) == samePerson(p2, p1).
 */
m.add PredicateConstraint.PartialFunctional, on : SamePerson
m.add PredicateConstraint.PartialInverseFunctional, on : SamePerson
m.add PredicateConstraint.Symmetric, on : SamePerson

/*
 * Finally, we define a prior on the inference predicate samePerson. It says that
 * we should assume two people are not the samePerson with some weight. This can
 * be overridden with evidence as defined in the previous rules.
 */
m.add rule: ~SamePerson(A,B), weight: 1

/*
 * Let's see what our model looks like.
 */
println m;

/* 
 * We now insert data into our DataStore. All data is stored in a partition.
 * We put all the observations into their own partition.
 * 
 * We can use insertion helpers for a specified predicate. Here we show how one
 * can manually insert data or use the insertion helpers to easily implement
 * custom data loaders.
 */
def evidencePartition = new Partition(0);
def insert = data.getInserter(name, evidencePartition);

/* Social Network A */
insert.insert(1, "John Braker");
insert.insert(2, "Mr. Jack Ressing");
insert.insert(3, "Peter Larry Smith");
insert.insert(4, "Tim Barosso");
insert.insert(5, "Jessica Pannillo");
insert.insert(6, "Peter Smithsonian");
insert.insert(7, "Miranda Parker");

/* Social Network B */
insert.insert(11, "Johny Braker");
insert.insert(12, "Jack Ressing");
insert.insert(13, "PL S.");
insert.insert(14, "Tim Barosso");
insert.insert(15, "J. Panelo");
insert.insert(16, "Gustav Heinrich Gans");
insert.insert(17, "Otto v. Lautern");

/*
 * Of course, we can also load data directly from tab delimited data files.
 */
def dir = 'data'+java.io.File.separator+'sn'+java.io.File.separator;

insert = data.getInserter(Network, evidencePartition)
InserterUtils.loadDelimitedData(insert, dir+"sn_network.txt");

insert = data.getInserter(Knows, evidencePartition)
InserterUtils.loadDelimitedData(insert, dir+"sn_knows.txt");

/*
 * After having loaded the data, we are ready to run some inference and see what
 * kind of alignment our model produces. Note that for now, we are using the
 * predefined weights.
 * 
 * We first create a second partition and open it as the write partition of
 * a Database from the DataStore. We also include the evidence partition as a
 * read partition.
 * 
 * We close the predicates Name and Knows since we want to treat those atoms as
 * observed, and leave the predicate
 * SamePerson open to infer its atoms' values.
 */
def targetPartition = new Partition(1);
Database db = data.getDatabase(targetPartition, [Network, Name, Knows] as Set, evidencePartition);

/*
 * Before running inference, we have to add the target atoms to the database.
 * If inference (or learning) attempts to access an atom that is not in the database,
 * it will throw an exception.
 * 
 * The below code builds a set of all users, then uses a utility class
 * (DatabasePopulator) to create all possible SamePerson atoms between users of
 * each network.
 */
Set<GroundTerm> usersA = new HashSet<GroundTerm>();
Set<GroundTerm> usersB = new HashSet<GroundTerm>();
for (int i = 1; i < 8; i++)
	usersA.add(data.getUniqueID(i));
for (int i = 11; i < 18; i++)
	usersB.add(data.getUniqueID(i));

Map<Variable, Set<GroundTerm>> popMap = new HashMap<Variable, Set<GroundTerm>>();
popMap.put(new Variable("UserA"), usersA)
popMap.put(new Variable("UserB"), usersB)

DatabasePopulator dbPop = new DatabasePopulator(db);
dbPop.populate((SamePerson(UserA, UserB)).getFormula(), popMap);
dbPop.populate((SamePerson(UserB, UserA)).getFormula(), popMap);

/*
 * Now we can run inference
 */
MPEInference inferenceApp = new MPEInference(m, db, config);
inferenceApp.mpeInference();
inferenceApp.close();

/*
 * Let's see the results
 */
println "Inference results with hand-defined weights:"
DecimalFormat formatter = new DecimalFormat("${symbol_pound}.${symbol_pound}${symbol_pound}");
for (GroundAtom atom : Queries.getAllAtoms(db, SamePerson))
	println atom.toString() + "${symbol_escape}t" + formatter.format(atom.getValue());

/* 
 * Next, we want to learn the weights from data. For that, we need to have some
 * evidence data from which we can learn. In our example, that means we need to
 * specify the 'true' alignment, which we now load into another partition.
 */
Partition trueDataPartition = new Partition(2);
insert = data.getInserter(SamePerson, trueDataPartition)
InserterUtils.loadDelimitedDataTruth(insert, dir + "sn_align.txt");

/* 
 * Now, we can learn the weights.
 * 
 * We first open a database which contains all the target atoms as observations.
 * We then combine this database with the original database to learn.
 */
Database trueDataDB = data.getDatabase(trueDataPartition, [samePerson] as Set);
MaxLikelihoodMPE weightLearning = new MaxLikelihoodMPE(m, db, trueDataDB, config);
weightLearning.learn();
weightLearning.close();

/*
 * Let's have a look at the newly learned weights.
 */
println ""
println "Learned model:"
println m

/*
 * Now, we apply the learned model to a different social network alignment data set.
 * We load the data set as before (into new partitions) and run inference.
 * Finally, we print the results.
 */

/*
 * Loads evidence
 */
Partition evidencePartition2 = new Partition(3);

insert = data.getInserter(Network, evidencePartition2)
InserterUtils.loadDelimitedData(insert, dir+"sn2_network.txt");

insert = data.getInserter(Name, evidencePartition2);
InserterUtils.loadDelimitedData(insert, dir+"sn2_names.txt");

insert = data.getInserter(Knows, evidencePartition2);
InserterUtils.loadDelimitedData(insert, dir+"sn2_knows.txt");

/*
 * Populates targets
 */
def targetPartition2 = new Partition(4);
Database db2 = data.getDatabase(targetPartition2, [Network, Name, Knows] as Set, evidencePartition2);

usersA.clear();
for (int i = 21; i < 28; i++)
	usersA.add(data.getUniqueID(i));
usersB.clear();
for (int i = 31; i < 38; i++)
	usersB.add(data.getUniqueID(i));

dbPop = new DatabasePopulator(db2);
dbPop.populate((SamePerson(UserA, UserB)).getFormula(), popMap);
dbPop.populate((SamePerson(UserB, UserA)).getFormula(), popMap);

/*
 * Performs inference
 */
inferenceApp = new MPEInference(m, db2, config);
result = inferenceApp.mpeInference();
inferenceApp.close();

println "Inference results on second social network with learned weights:"
for (GroundAtom atom : Queries.getAllAtoms(db2, SamePerson))
	println atom.toString() + "${symbol_escape}t" + formatter.format(atom.getValue());
	
/*
 * We close the Databases to flush writes
 */
db.close();
trueDataDB.close();
db2.close();

/**
 * This class implements the ExternalFunction interface so that it can be used
 * as an attribute similarity function within PSL.
 *
 * This simple implementation checks whether two strings are identical, in which case it returns 1.0
 * or different (returning 0.0).
 *
 * The package edu.umd.cs.psl.ui.functions.textsimilarity contains additional and
 * more sophisticated string similarity functions.
 */
class MyStringSimilarity implements ExternalFunction {
	
	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ArgumentType[] getArgumentTypes() {
		return [ArgumentType.String, ArgumentType.String].toArray();
	}
	
	@Override
	public double getValue(ReadOnlyDatabase db, GroundTerm... args) {
		return args[0].toString().equals(args[1].toString()) ? 1.0 : 0.0;
	}
	
}
