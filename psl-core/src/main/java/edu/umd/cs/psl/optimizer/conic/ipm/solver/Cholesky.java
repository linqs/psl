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
package edu.umd.cs.psl.optimizer.conic.ipm.solver;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;

/**
 * Solves normal systems using a Cholesky factorization.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class Cholesky implements NormalSystemSolver {
	
	private SparseDoubleCholeskyDecomposition decomposition;

	@Override
	public void setConicProgram(ConicProgram program) {
		/* Intentionally blank */
	}

	@Override
	public void setA(SparseCCDoubleMatrix2D A) {
		decomposition = new SparseDoubleCholeskyDecomposition(A, 1);
	}

	@Override
	public void solve(DoubleMatrix1D b) {
		decomposition.solve(b);
	}

}
