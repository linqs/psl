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
package org.linqs.psl.evaluation.statistics;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;

import cern.colt.matrix.tint.impl.DenseIntMatrix2D;

/**
 * Confusion matrix data structure.
 * Basically, a wrapper for ParallelColt DenseIntMatrix2D, with some specialized methods.
 * 
 * @author blondon
 *
 */
public class ConfusionMatrix extends DenseIntMatrix2D implements Serializable {

	/**
	 * NOTE: I just picked a number; hope it doesn't clash with something else...
	 */
	private static final long serialVersionUID = 42L;

	/**
	 * Initializes an empty confusion matrix of size numClasses.
	 * 
	 * @param numClasses
	 */
	public ConfusionMatrix(int numClasses) {
		super(numClasses, numClasses);
	}

	/**
	 * Initializes a confusion matrix from a 2D array of ints.
	 * @param data
	 */
	public ConfusionMatrix(final int[][] data) {
		super(data);
	}
	
	/**
	 * Copy constructor.
	 * 
	 * @param cm
	 */
	public ConfusionMatrix(final ConfusionMatrix cm) {
		super(cm.getNumClasses(), cm.getNumClasses());
		for (int i = 0; i < cm.getNumClasses(); i++)
			for (int j = 0; j < cm.getNumClasses(); j++)
				set(i,j,cm.get(i,j));
	}
	
	/**
	 * Returns the number of classes (labels).
	 * 
	 * @return
	 */
	public int getNumClasses() {
		return rows;
	}

	/**
	 * Accumulates the scores from another confusion matrix.
	 * 
	 * @param cm
	 */
	public void accumulate(ConfusionMatrix cm) {
		for (int i = 0; i < getNumClasses(); i++) {
			for (int j = 0; j < getNumClasses(); j++) {
				int val = get(i,j) + cm.get(i,j);
				set(i,j,val);
			}
		}
	}
	
	/**
	 * Accumulates the scores from a collection of confusion matrices.
	 * 
	 * @param cms
	 */
	public void accumulate(Collection<ConfusionMatrix> cms) {
		if (cms.size() == 0)
			throw new IllegalArgumentException("Cannot accumulate empty collection.");
		Iterator<ConfusionMatrix> iter = cms.iterator();
		while (iter.hasNext())
			accumulate(iter.next());
	}
	
	/**
	 * Aggregates the scores of a collection of confusion matrices.
	 * 
	 * @param cms
	 * @return
	 */
	public static ConfusionMatrix aggregate(Collection<ConfusionMatrix> cms) {
		if (cms.size() == 0)
			throw new IllegalArgumentException("Cannot aggregate empty collection.");
		Iterator<ConfusionMatrix> iter = cms.iterator();
		ConfusionMatrix cm = iter.next().clone();
		while (iter.hasNext())
			cm.accumulate(iter.next());
		return cm;
	}
	
	/**
	 * Returns the precision matrix, computed by normlizing each entry (i,j) the sum of column j.
	 * 
	 * @return
	 */
	public SquareMatrix getPrecisionMatrix() {
		/* Normalize entries of matrix by column sums. */
		SquareMatrix normVals = new SquareMatrix(getNumClasses());
		for (int j = 0; j < getNumClasses(); j++) {
			int colSum = 0;
			for (int i = 0; i < getNumClasses(); i++)
				colSum += get(i,j);
			for (int i = 0; i < getNumClasses(); i++) {
				double val = colSum == 0 ? 0.0 : get(i,j) / (double)colSum;
				normVals.set(i,j,val);
			}
		}
		return normVals;
	}
	
	/**
	 * Returns the recall matrix, computed by normlizing each entry (i,j) the sum of row i.
	 * 
	 * @return
	 */
	public SquareMatrix getRecallMatrix() {
		SquareMatrix normVals = new SquareMatrix(getNumClasses());
		for (int i = 0; i < getNumClasses(); i++) {
			int rowSum = 0;
			for (int j = 0; j < getNumClasses(); j++)
				rowSum += get(i,j);
			for (int j = 0; j < getNumClasses(); j++) {
				double val = rowSum == 0 ? 0.0 : get(i,j) / (double)rowSum;
				normVals.set(i,j,val);
			}
		}
		return new SquareMatrix(normVals);
	}
	
	/**
	 * Returns a Matlab string representation of the confusion matrix.
	 * 
	 * @return
	 */
	public String toMatlabString() {
		/* Determine max value in CM. */
		int mx = this.getMaxLocation()[0];
		
		/* Determine character width of max value. */
		int maxCharWidth = (int)Math.log10((double)mx) + 1;
		
		/* Left-pad numbers by maxCharWidth. */
		String format = "%" + maxCharWidth + "d";

		/* Build output string. */
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < getNumClasses(); i++) {
			if (i == 0)
				sb.append("[");
			else
				sb.append(" ");
			for (int j = 0; j < getNumClasses(); j++) {
				sb.append(" ");
				sb.append(String.format(format, get(i,j)));
			}
			if (i == getNumClasses()-1)
				sb.append(" ]");
			else
				sb.append(" ;\n");
		}

		return sb.toString();
	}

	/**
	 * Returns a deep copy of the confusion matrix.
	 */
	@Override
	public ConfusionMatrix clone() {
		return new ConfusionMatrix(this);
	}

}
