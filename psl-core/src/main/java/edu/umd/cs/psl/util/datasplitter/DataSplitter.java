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
package edu.umd.cs.psl.util.datasplitter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.util.datasplitter.builddbstep.BuildDBStep;
import edu.umd.cs.psl.util.datasplitter.builddbstep.DBDefinition;
import edu.umd.cs.psl.util.datasplitter.closurestep.ClosureStep;
import edu.umd.cs.psl.util.datasplitter.splitstep.SplitStep;

/**
 * Utility for splitting data sets.
 * <p>
 * Can be parameterized with strategies.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class DataSplitter {
	private final Random rand;
	private SplitStep splitStep;
	private List<ClosureStep> closureSteps;
	private BuildDBStep buildDBStep;
	private DataSplitter subsplitter;
	
	public DataSplitter(long seed) {
		rand = new Random(seed);
		splitStep = null;
		closureSteps = new ArrayList<ClosureStep>();
		buildDBStep = null;
		subsplitter = null;
	}
	
	public void setSplitStep(SplitStep splitStep) {
		this.splitStep = splitStep;
	}
	
	public void addClosureStep(ClosureStep closureStep) {
		closureSteps.add(closureStep);
	}
	
	public void clearClosureSteps() {
		closureSteps.clear();
	}
	
	public void setBuildDBStep(BuildDBStep buildDBStep) {
		this.buildDBStep = buildDBStep;
	}
	
	public void setSubsplitter(DataSplitter subsplitter) {
		this.subsplitter = subsplitter;
	}
	
	public ExperimentTree split(Database db) {
		if (splitStep == null)
			throw new IllegalStateException("No SplitStep has been set.");
		if (closureSteps.size() == 0)
			throw new IllegalStateException("No ClosureStep has been set.");
		if (buildDBStep == null)
			throw new IllegalStateException("No BuildDBStep has been set.");
		
		List<Collection<Partition>> partitionGroups = splitStep.getSplits(db, rand);
		for (ClosureStep closureStep : closureSteps)
			closureStep.doClosure(db, partitionGroups);
		List<DBDefinition> dbDefs = buildDBStep.getDatabaseDefinitions(db, partitionGroups);
		
		ExperimentTree tree = new ExperimentTree();
		
		if (subsplitter != null) {
			DataStore ds = db.getDataStore();
			for (DBDefinition dbDef : dbDefs) {
				Database subDB = ds.getDatabase(dbDef.write, dbDef.toClose, dbDef.read);
				tree.addChild(subsplitter.split(subDB));
				subDB.close();
			}
		}
		else {
			for (DBDefinition dbDef : dbDefs)
				tree.addChild(new ExperimentTree(dbDef));
		}
		
		return tree;
	}
}
