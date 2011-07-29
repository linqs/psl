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
package edu.umd.cs.psl.groovy;


import java.util.List;
import java.util.Map;

import edu.umd.cs.psl.database.RDBMS.RDBMSPredicateHandle;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;

import edu.umd.cs.psl.database.RDBMS.*;
import edu.umd.cs.psl.database.DataFormat;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.PredicateDBType;
import edu.umd.cs.psl.database.partition.PartitionID;
import edu.umd.cs.psl.groovy.util.*;
import edu.umd.cs.psl.groovy.syntax.*;
import edu.umd.cs.psl.model.predicate.*;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.ui.loading.InserterUtils;

class RelationalDataStore extends RDBMSDataStore
implements DataStore {
	
	private static final int foldIDUpperBound = 1000;
	
	private static int writeIDCounter = foldIDUpperBound;
	private final boolean hasStringEntities = false;
	
	RelationalDataStore(args) {
		super();
		
		if (args['valuecol']!=null) {
			if (args['valuecol'] instanceof String) valueColumnSuffix = args['valuecol'];
			else throw new IllegalArgumentException("Expected STRING argument for value column name, but got: " + args['valuecol']);
		}
		if (args['confcol']!=null) {
			if (args['confcol'] instanceof String) confidenceColumnSuffix = args['confcol'];
			else throw new IllegalArgumentException("Expected STRING argument for confidence column name, but got: " + args['confcol']);
		}
		if (args['pslcol']!=null) {
			if (args['pslcol'] instanceof String) pslColumnName = args['pslcol'];
			else throw new IllegalArgumentException("Expected STRING argument for psl column name, but got: " + args['pslcol']);
		}
		if (args['partitioncol']!=null) {
			if (args['partitioncol'] instanceof String) partitionColumnName = args['partitioncol'];
			else throw new IllegalArgumentException("Expected STRING argument for partition column name, but got: " + args['partitioncol']);
		}
		if (args['entityid']!=null && args['entityid']=='string') {
			hasStringEntities=true;
		}
		

		Inserter.metaClass.loadFromFile = { String filename ->
			InserterUtils.loadDelimitedData(delegate, filename);
		}
		Inserter.metaClass.loadFromFile = { String filename, String delim ->
			InserterUtils.loadDelimitedData(delegate, filename, delim);
		}
		Inserter.metaClass.loadFromFileWithTruth = { String filename ->
			InserterUtils.loadDelimitedDataTruth(delegate, filename);
		}
		Inserter.metaClass.loadFromFileWithTruth = { String filename, String delim ->
			InserterUtils.loadDelimitedDataTruth(delegate, filename, delim);
		}
		Inserter.metaClass.loadFactIntersectionTable = { String filename ->
			InserterUtils.loadFactIntersectionTable(delegate, filename);
		}
		Inserter.metaClass.loadFactIntersectionTable = { String filename, String delim ->
			InserterUtils.loadFactIntersectionTable(delegate, filename, delim);
		}
		Inserter.metaClass.loadFactEntityIntersectionTable = { String filename ->
			InserterUtils.loadFactEntityIntersectionTable(delegate, filename);
		}
		Inserter.metaClass.loadFactEntityIntersectionTable = { String filename, String delim ->
			InserterUtils.loadFactEntityIntersectionTable(delegate, filename, delim);
		}
		
	}

	RelationalDataStore() {
		this([:]);
	}
	
	RelationalDataStore(args, PSLModel model) {
		this(args);
		registerModel(model);
	}

	RelationalDataStore(PSLModel model) {
		this([:],model);
	}

	public void registerModel(PSLModel model) {
		model.registerPredicates(this);
	}
	
	public void registerPredicate(Predicate predicate, List<String> argnames, PredicateDBType type) {
		super.registerPredicate(predicate,argnames,type,DataFormat.getDefaultFormat(predicate, hasStringEntities));
	}
	
	public void loadFactTable(PredicateFactory pf, String file, String delimeter) {
		InserterUtils.loadFactTable(pf, this, file, getDefaultPartition(), delimeter);
	}

	
	private void preprocessConnectionArgs(Map args) {
		if (args['db']==null) throw new IllegalArgumentException("Need to specify a database driver to connect with using the [db] label.");
		if (args['type']==null) args['type']='';
		else args['type']=args['type'].toString().toLowerCase();
		switch(args['type']) {
			case '': 
				args['type']=DatabaseDriver.Type.Disk;
				break;
			case 'disk': 
				args['type']=DatabaseDriver.Type.Disk;
				break;
			case 'memory': 
				args['type']=DatabaseDriver.Type.Memory;
				break;
			default: throw new IllegalArgumentException("Unrecognized database type: " + args['type']);
		}

		if (args['name']==null) args['name'] = 'psldb';
		if (args['folder']==null) args['folder'] = '';
	}
	
	def connect(Map args) {
		preprocessConnectionArgs(args);
		super.connect(args['db'],args['type'],args['name'],args['folder']);
	}
	
	def setup(Map args) {
		preprocessConnectionArgs(args);
		super.setup(args['db'],args['type'],args['name'],args['folder']);
	}
	
	@Override
	def Database getDatabase() {
		return getDatabase([:]);
	}
	
	@Override
	def Database getDatabase(Map args) {
		Partition writeID;
		PartitionConverter pconv = new PartitionConverter(this);
		if (args['write']!=null) {
			writeID=pconv.get(args['write']);
		} else writeID = getNextPartition();
		Partition[] parts = ArgumentParser.getArgumentPartitionArray(args, 'read', new PartitionConverter(this));
		if (parts==null) {
			parts = new Partition[1];
			parts[0] = getDefaultPartition();
		}
		
		Set<Predicate> toclose = new HashSet<Predicate>();
		if (args['close']!=null) {
			if (args['close'] instanceof List) {
				args['close'].each {
					if (it instanceof Predicate) toclose.add it;
					//else if (it instanceof String) toclose.add getPredicate(it);
					else throw new IllegalArgumentException("Expected a list of strings or predicates to close, but was given: "+it);
				}
			} else if (args['close'] instanceof Predicate) toclose.add args['close'];
			//else if (args['close'] instanceof String) toclose.add getPredicate(args['close']);
			else  throw new IllegalArgumentException("Expected a list of strings or predicates to close, but was given: "+args['close']);
		}
		
		return getDatabase(writeID, toclose, parts);
	}
	
	def Partition getNextPartition() {
		return getPartition(writeIDCounter);
	}
	
	def Partition getDefaultPartition() {
		return getPartition(DataStore.defaultPartitionID);
	}
	
	def Partition getPartition(int pID) {
		if (pID>=writeIDCounter) writeIDCounter = pID+1;
		return new PartitionID(pID);
	}
	
	def Inserter getInserter(Predicate predicate, int partitionID) {
		return this.getInserter(predicate,getPartition(partitionID));
	}
	
	def Inserter getInserter(Predicate predicate) {
		return getInserter(predicate,getDefaultPartition());
	}
	
	
}
