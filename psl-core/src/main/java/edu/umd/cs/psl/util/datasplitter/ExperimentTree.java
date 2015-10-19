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
package edu.umd.cs.psl.util.datasplitter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.umd.cs.psl.util.datasplitter.builddbstep.DBDefinition;

/**
 * Tree of experimental setups, such as folds and train/test splits.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ExperimentTree implements Iterable<ExperimentTree> {
	private final DBDefinition dbDef;
	private final List<ExperimentTree> children;
	
	public ExperimentTree() {
		dbDef = null;
		children = new ArrayList<ExperimentTree>();
	}
	
	public ExperimentTree(DBDefinition dbDef) {
		this.dbDef = dbDef;
		children = null;
	}
	
	public void addChild(ExperimentTree expTree) {
		if (children != null)
			children.add(expTree);
		else
			throw new UnsupportedOperationException("Node is a leaf.");
	}
	
	public DBDefinition getDBDefinition() {
		if (dbDef == null)
			throw new UnsupportedOperationException("Node is interior.");
		
		return dbDef;
	}

	@Override
	public Iterator<ExperimentTree> iterator() {
		if (children == null)
			return new Iterator<ExperimentTree>() {
				
				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
				
				@Override
				public ExperimentTree next() {
					throw new NoSuchElementException();
				}
				
				@Override
				public boolean hasNext() {
					return false;
				}
			};
		else
			return children.iterator();
	}
}
