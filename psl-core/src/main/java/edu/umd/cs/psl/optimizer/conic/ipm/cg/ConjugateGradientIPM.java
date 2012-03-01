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
package edu.umd.cs.psl.optimizer.conic.ipm.cg;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.algo.solver.DoubleCG;
import cern.colt.matrix.tdouble.algo.solver.IterativeSolverDoubleNotConvergedException;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.optimizer.conic.ipm.IPM;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;

public class ConjugateGradientIPM extends IPM {

	public ConjugateGradientIPM(ConfigBundle config) {
		super(config);
	}
	
	@Override
	protected void solveNormalSystem(SparseCCDoubleMatrix2D A, DoubleMatrix1D x, ConicProgram program) {
		DoubleCG cg = new DoubleCG(x);
		DoubleMatrix1D b = x.copy();
		x.assign(0);
		try {
			cg.solve(A, b, x);
		}
		catch (IterativeSolverDoubleNotConvergedException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
