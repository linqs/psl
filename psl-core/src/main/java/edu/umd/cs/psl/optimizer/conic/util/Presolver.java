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
package edu.umd.cs.psl.optimizer.conic.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleQRDecomposition;

import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;

/**
 * Utility methods for modifying a {@link ConicProgram}.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class Presolver {
	
	/**
	 * Deletes any redundant constraints from a {@link ConicProgram}.
	 * 
	 * @param program  the program to be modified
	 */
	public static void removeRedundantConstraints(ConicProgram program) {
		final double tolerance = 10e-10;
		
		program.checkOutMatrices();
		
		Map<Integer, LinearConstraint> rowMap = invertLCMap(program.getLcMap());
		List<LinearConstraint> toRemove = new LinkedList<LinearConstraint>();
		
		SparseDoubleQRDecomposition qr = new SparseDoubleQRDecomposition(program.getA(), 1);
		int[] q = qr.getSymbolicAnalysis().q; /* Column permutations (since A is transposed) */
		DoubleMatrix2D R = qr.getR();
		
		for (int i = 0; i < program.getNumLinearConstraints(); i++) {
			if (Math.abs(R.get(i, i)) < tolerance)
				toRemove.add(rowMap.get(q[i]));
		}
		
		program.checkInMatrices();
		
		for (LinearConstraint lc : toRemove)
			lc.delete();
	}
	
	private static Map<Integer, LinearConstraint> invertLCMap(Map<LinearConstraint, Integer> map) {
		Map<Integer, LinearConstraint> invertedMap = new HashMap<Integer, LinearConstraint>(map.size());
		
		for (Map.Entry<LinearConstraint, Integer> e : map.entrySet())
			invertedMap.put(e.getValue(), e.getKey());
		
		if (map.size() != invertedMap.size())
			throw new IllegalArgumentException("Map is not one-to-one.");
		
		return invertedMap;
	}
}
