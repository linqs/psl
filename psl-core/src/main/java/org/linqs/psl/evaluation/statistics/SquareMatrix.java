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

import java.util.Collection;
import java.util.Iterator;

import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;

/**
 * Square matrix data structure.
 * Basically, a wrapper for ParallelColt DenseDoubleMatrix2D, with some specialized methods.
 * 
 * @author blondon
 *
 */
public class SquareMatrix {
	
	/**
	 * NOTE: Due to some weird bug in Parallel Colt and/or Groovy,
	 * 		 you can't instantiate a (subclass of a) DenseDoubleMatrix2D
	 * 		 in Groovy. Due to this, I have implemented this class as a 
	 * 		 wrapper instead of a subclass. 
	 */
	private DenseDoubleMatrix2D mat;
	
	/**
	 * Initializes an empty square matrix of size length x length.
	 * 
	 * @param length
	 */
	public SquareMatrix(int length) {
		mat = new DenseDoubleMatrix2D(length, length);
	}
	
	/**
	 * Copy constructor.
	 * 
	 * @param sm
	 */
	public SquareMatrix(final SquareMatrix sm) {
		mat = new DenseDoubleMatrix2D(sm.length(), sm.length());
		for (int i = 0; i < sm.length(); i++)
			for (int j = 0; j < sm.length(); j++)
				set(i,j,sm.get(i,j));
	}
	
	/**
	 * Returns the number of classes (labels).
	 * 
	 * @return
	 */
	public int length() {
		return mat.rows();
	}
	
	/**
	 * Get entry (i,j).
	 * 
	 * @param i
	 * @param j
	 * @return
	 */
	public double get(int i, int j) {
		return mat.get(i, j);
	}
	
	/**
	 * Set entry (i,j).
	 * 
	 * @param i
	 * @param j
	 * @param value
	 */
	public void set(int i, int j, double value) {
		mat.set(i, j, value);
	}

	/**
	 * Accumulates the scores from another square matrix.
	 * 
	 * @param sm
	 */
	public void accumulate(SquareMatrix sm) {
		for (int i = 0; i < length(); i++) {
			for (int j = 0; j < length(); j++) {
				double val = get(i,j) + sm.get(i,j);
				set(i,j,val);
			}
		}
	}
	
	/**
	 * Accumulates the scores from a collection of square matrices.
	 * 
	 * @param sms
	 */
	public void accumulate(Collection<SquareMatrix> sms) {
		if (sms.size() == 0)
			throw new IllegalArgumentException("Cannot accumulate empty collection.");
		Iterator<SquareMatrix> iter = sms.iterator();
		while (iter.hasNext())
			accumulate(iter.next());
	}
	
	/**
	 * Aggregates the scores of a collection of square matrices.
	 * 
	 * @param sms
	 * @return
	 */
	public static SquareMatrix aggregate(Collection<SquareMatrix> sms) {
		if (sms.size() == 0)
			throw new IllegalArgumentException("Cannot aggregate empty collection.");
		Iterator<SquareMatrix> iter = sms.iterator();
		SquareMatrix sm = iter.next().clone();
		while (iter.hasNext())
			sm.accumulate(iter.next());
		return sm;
	}

	/**
	 * Averages the scores of an array of square matrices.
	 * 
	 * @param sms
	 * @return
	 */
	public static SquareMatrix average(Collection<SquareMatrix> sms) {
		SquareMatrix sm = aggregate(sms);
		for (int i = 0; i < sm.length(); i++) {
			for (int j = 0; j < sm.length(); j++) {
				sm.set(i,j,sm.get(i,j)/(double)sms.size());
			}
		}
		return sm;		
	}

	/**
	 * Returns ParallelColt's string representation of the matrix.
	 */
	@Override
	public String toString() {
		return mat.toString();
	}
	
	/**
	 * Returns a Matlab string representation of the matrix.
	 * 
	 * @return
	 */
	public String toMatlabString(int numDigits) {
		/* Zero-pad decimals by numDigits digits. */ 
		String format = "%." + numDigits + "f";

		/* Build output string. */
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length(); i++) {
			if (i == 0)
				sb.append("[");
			else
				sb.append(" ");
			for (int j = 0; j < length(); j++) {
				sb.append(" ");
				/* Right-pad decimals by 4. */
				sb.append(String.format(format, get(i,j)));
			}
			if (i == length()-1)
				sb.append(" ]");
			else
				sb.append(" ;\n");
		}
		
		return sb.toString();
	}

	/**
	 * Returns a deep copy of the matrix.
	 */
	@Override
	public SquareMatrix clone() {
		return new SquareMatrix(this);
	}
	
}
