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
