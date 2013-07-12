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
package edu.umd.cs.psl.optimizer.conic.ipm.solver.preconditioner;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.function.tdouble.IntIntDoubleFunction;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.algo.solver.preconditioner.DoublePreconditioner;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.optimizer.conic.partition.ConicProgramPartition;
import edu.umd.cs.psl.optimizer.conic.partition.ObjectiveCoefficientPartitioner;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;

public class BlockPreconditioner implements DoublePreconditioner {
	
	private static final Logger log = LoggerFactory.getLogger(BlockPreconditioner.class);
	
	protected final ConicProgram program;
	protected final ConicProgramPartition partition;
	protected SparseDoubleCholeskyDecomposition cholesky;
	
	public BlockPreconditioner(ConicProgram program) {
		this.program = program;
		ObjectiveCoefficientPartitioner partitioner = new ObjectiveCoefficientPartitioner(new EmptyBundle());
		partitioner.setConicProgram(program);
		partition = partitioner.getPartition();
	}

	@Override
	public DoubleMatrix1D apply(DoubleMatrix1D b, DoubleMatrix1D x) {
		x.assign(b);
		cholesky.solve(x);
		return x;
	}

	@Override
	public DoubleMatrix1D transApply(DoubleMatrix1D b, DoubleMatrix1D x) {
		return apply(b, x);
	}

	@Override
	public void setMatrix(DoubleMatrix2D A) {
		log.trace("Starting to set matrix.");
		DoubleMatrix2D localA = A.copy();
		Set<LinearConstraint> cutConstraints = partition.getCutConstraints();
		final Set<Integer> cutRows = new HashSet<Integer>();
		for (LinearConstraint con : cutConstraints) {
			cutRows.add(program.getIndex(con));
		}
		
		localA.forEachNonZero(new IntIntDoubleFunction() {
			
			@Override
			public double apply(int first, int second, double third) {
				boolean containsFirst = cutRows.contains(first);
				if (first == second && containsFirst)
					return 1;
				
				boolean containsSecond = cutRows.contains(second);
				
				if (containsFirst || containsSecond)
					return 0;
				else
					return third;
			}
		});
		
		cholesky = new SparseDoubleCholeskyDecomposition(localA, 1);
		log.trace("Matrix set.");
	}

}
