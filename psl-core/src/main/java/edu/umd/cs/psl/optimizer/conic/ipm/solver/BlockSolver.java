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

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.function.tdouble.IntIntDoubleFunction;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.algo.solver.DefaultDoubleIterationMonitor;
import cern.colt.matrix.tdouble.algo.solver.DoubleCG;
import cern.colt.matrix.tdouble.algo.solver.DoubleIterationMonitor;
import cern.colt.matrix.tdouble.algo.solver.DoubleIterationReporter;
import cern.colt.matrix.tdouble.algo.solver.IterativeSolverDoubleNotConvergedException;
import cern.colt.matrix.tdouble.algo.solver.preconditioner.DoublePreconditioner;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.optimizer.conic.partition.ConicProgramPartition;
import edu.umd.cs.psl.optimizer.conic.partition.ObjectiveCoefficientPartitioner;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;

/**
 * Solves normal systems using the Schur's complement method, where the complement
 * is complementary to a block-diagonal submatrix found by partitioning.
 *
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class BlockSolver implements NormalSystemSolver {
	
	private static final Logger log = LoggerFactory.getLogger(BlockSolver.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "blocksolver";
	
	/**
	 * Key for integer property. The BlockSolver will throw an
	 * exception if the conjugate gradient solver completes this many iterations
	 * without solving the normal system.
	 */
	public static final String CG_MAX_ITER_KEY = CONFIG_PREFIX + ".maxcgiter";
	/** Default value for CG_MAX_ITER_KEY property */
	public static final int CG_MAX_ITER_DEFAULT = 1000000;
	
	/**
	 * Key for double property. The conjugate gradient solver will terminate
	 * as converged if the residual is less than this value times the
	 * initial residual.
	 */
	public static final String CG_REL_TOL_KEY = CONFIG_PREFIX + ".cgreltol";
	/** Default value for CG_REL_TOL_KEY property */
	public static final double CG_REL_TOL_DEFAULT = 10e-10;
	
	/**
	 * Key for double property. The conjugate gradient solver will terminate
	 * as converged if the residual is less than this value.
	 */
	public static final String CG_ABS_TOL_KEY = CONFIG_PREFIX + ".cgabstol";
	/** Default value for CG_REL_TOL_KEY property */
	public static final double CG_ABS_TOL_DEFAULT = 10e-50;
	
	/**
	 * Key for double property. The BlockSolver will throw an
	 * exception if the conjugate graident solver reaches an iterate
	 * whose residual is at least this value times the initial residual.
	 */
	public static final String CG_DIV_TOL_KEY = CONFIG_PREFIX + ".cgdivtol";
	/** Default value for CG_DIV_TOL_KEY property */
	public static final double CG_DIV_TOL_DEFAULT = 10e5;
	
	/**
	 * Key for non-negative integer property. The BlockSolver preconditions
	 * the Schur's complement matrix by a truncated series summation. Higher
	 * values generally result in fewer conjugate gradient iterations, but each
	 * iteration is more time consuming. 
	 */
	public static final String PRECONDITIONER_TERMS_KEY = CONFIG_PREFIX + ".preconditionerterms";
	/** Default value for PRECONDITIONER_TERMS_KEY property */
	public static final int PRECONDITIONER_TERMS_DEFAULT = 1;
	
	protected final int maxIter;
	protected final double relTol;
	protected final double absTol;
	protected final double divTol;
	protected final int terms;
	
	protected ConicProgram program;
	protected ConicProgramPartition partition;
	protected SparseDoubleCholeskyDecomposition choleskyB;
	protected SparseDoubleCholeskyDecomposition choleskyD;
	
	protected DoubleMatrix1D scratch;
	
	protected DoubleCG cg;
	protected DoubleIterationMonitor monitor;
	private DoubleMatrix1D x;
	
	protected DoubleMatrix2D B, C, D;
	protected int[] rowAssignments;
	protected boolean[] cutRows;
	
	public BlockSolver(ConfigBundle config) {
		maxIter = config.getInt(CG_MAX_ITER_KEY, CG_MAX_ITER_DEFAULT);
		relTol  = config.getDouble(CG_REL_TOL_KEY, CG_REL_TOL_DEFAULT);
		absTol  = config.getDouble(CG_ABS_TOL_KEY, CG_ABS_TOL_DEFAULT);
		divTol  = config.getDouble(CG_DIV_TOL_KEY, CG_DIV_TOL_DEFAULT);
		
		terms = config.getInt(PRECONDITIONER_TERMS_KEY, PRECONDITIONER_TERMS_DEFAULT);
		if (terms < 0)
			throw new IllegalArgumentException("Property " + PRECONDITIONER_TERMS_KEY + " must be non-negative.");
		
		monitor = new DefaultDoubleIterationMonitor(maxIter, relTol, absTol, divTol);
		monitor.setIterationReporter(new DoubleIterationReporter() {
			
			@Override
			public void monitor(double r, DoubleMatrix1D x, int i) {
				monitor(r, i);
			}
			
			@Override
			public void monitor(double r, int i) {
				if (i % 50 == 0)
					log.trace("Res. at itr {}: {}", i, r);
			}
		});
	}

	@Override
	public void setConicProgram(ConicProgram program) {
		this.program = program;
		ObjectiveCoefficientPartitioner partitioner = new ObjectiveCoefficientPartitioner(new EmptyBundle());
		partitioner.setConicProgram(program);
		partition = partitioner.getPartition();
		Set<LinearConstraint> cutConstraints = partition.getCutConstraints();
		
		/* Initializes the scratch vector used by the preconditioner */
		scratch = new DenseDoubleMatrix1D(program.getNumLinearConstraints() - cutConstraints.size());
		
		rowAssignments = new int[program.getNumLinearConstraints()];
		cutRows = new boolean[program.getNumLinearConstraints()];
		int nextUncut = 0;
		int nextCut = 0;
		int index;
		for (LinearConstraint con : program.getConstraints()) {
			index = program.getIndex(con);
			if (cutConstraints.contains(con)) {
				rowAssignments[index] = nextCut++;
				cutRows[index] = true;
			}
			else {
				rowAssignments[index] = nextUncut++;
				cutRows[index] = false;
			}
		}
		
		x = new DenseDoubleMatrix1D(partition.getCutConstraints().size());
		cg = new DoubleCG(x);
		cg.setIterationMonitor(monitor);
		
		log.debug("Cut {} constraints out of {}", partition.getCutConstraints().size(), program.getNumLinearConstraints());
	}

	@Override
	public void setA(SparseCCDoubleMatrix2D A) {
		log.trace("Starting to set A.");
		int numCut = partition.getCutConstraints().size();
		if (numCut > 0) {
			int numUncut = A.rows() - numCut;
			
			B = new SparseDoubleMatrix2D(numUncut, numUncut, numUncut * 4, 0.2, 0.5);
			C = new SparseDoubleMatrix2D(numUncut, numCut, numUncut * 2, 0.2, 0.5);
			D = new SparseDoubleMatrix2D(numCut, numCut, numCut * 4, 0.2, 0.5);
			
			A.forEachNonZero(new IntIntDoubleFunction() {
				
				@Override
				public double apply(int first, int second, double third) {
					boolean cutFirst = cutRows[first];
					boolean cutSecond = cutRows[second];
					/* Entry goes to D */
					if (cutFirst && cutSecond) {
						D.setQuick(rowAssignments[first], rowAssignments[second], third);
					}
					/* Entry goes to C */
					else if (cutSecond) {
						C.setQuick(rowAssignments[first], rowAssignments[second], third);
					}
					/* Entry goes to B */
					else if (!cutFirst) {
						B.setQuick(rowAssignments[first], rowAssignments[second], third);
					}
					
					return third;
				}
			});
			
			B = ((SparseDoubleMatrix2D) B).getColumnCompressed(false);
			C = ((SparseDoubleMatrix2D) C).getColumnCompressed(false);
			D = ((SparseDoubleMatrix2D) D).getColumnCompressed(false);
			
			choleskyB = new SparseDoubleCholeskyDecomposition(B, 1);
			choleskyD = new SparseDoubleCholeskyDecomposition(D, 1);
			
			cg.setPreconditioner(new DoublePreconditioner() {
				
				@Override
				public DoubleMatrix1D transApply(DoubleMatrix1D b, DoubleMatrix1D x) {
					return apply(b, x);
				}
				
				@Override
				public void setMatrix(DoubleMatrix2D A) {
					/* Intentionally blank */
				}
				
				@Override
				public DoubleMatrix1D apply(DoubleMatrix1D b, DoubleMatrix1D x) {
					DoubleMatrix1D x1 = null;
					x.assign(b);
					choleskyD.solve(x);
					
					for (int i = 0; i < terms; i++) {
						if (i == 0)
							x1 = x.copy();
						
						C.zMult(x1, scratch);
						choleskyB.solve(scratch);
						C.zMult(scratch, x1, 1.0, 0.0, true);
						choleskyD.solve(x1);
						
						x.assign(x1, DoubleFunctions.plus);
					}
					
					return x;
				}
			});
		}
		else {
			choleskyB = new SparseDoubleCholeskyDecomposition(A, 1);
		}
		
		log.trace("Finished setting A.");
	}

	@Override
	public void solve(DoubleMatrix1D b) {
		/* If the matrix was cut */
		if (partition.getCutConstraints().size() > 0) {
			DoubleMatrix1D b0 = new DenseDoubleMatrix1D(D.rows());
			DoubleMatrix1D b1 = new DenseDoubleMatrix1D(B.rows());
			DoubleMatrix1D y0 = new DenseDoubleMatrix1D(D.rows());
			
			for (int i = 0; i < b.size(); i++) {
				if (cutRows[i])
					b0.set(rowAssignments[i], b.getQuick(i));
				else
					b1.set(rowAssignments[i], b.getQuick(i));
			}
			
			DoubleMatrix1D y1 = b1.copy();
			
			DoubleMatrix1D b0Scratch = b0.copy();
			DoubleMatrix1D b1Scratch = b1.copy();
			
			/* Sets b0Scratch to b0 - C' * inv(B) * b1 */
			choleskyB.solve(b1Scratch);
			b0Scratch.assign(C.zMult(b1Scratch, null, 1.0, 0.0, true), DoubleFunctions.minus);
			
			try {
				cg.solve(new SchurComplement(), b0Scratch, y0);
				log.debug("Solved for complement in {} iterations.", monitor.iterations());
			} catch (IterativeSolverDoubleNotConvergedException e) {
				throw new IllegalArgumentException(e);
			}
			
			C.zMult(y0, b1Scratch);
			y1.assign(b1Scratch, DoubleFunctions.minus);
			choleskyB.solve(y1);
			
			/* Puts the results back into b */
			for (int i = 0; i < rowAssignments.length; i++) {
				b.setQuick(i, (cutRows[i]) ? y0.getQuick(rowAssignments[i]) : y1.getQuick(rowAssignments[i]));
			}
		}
		else {
			choleskyB.solve(b);
		}
	}
	
	protected class SchurComplement extends SparseDoubleMatrix2D {
		
		private static final long serialVersionUID = 112358132134L;
		
		private final DoubleMatrix1D scratch0;
		private final DoubleMatrix1D scratch1;

		public SchurComplement() {
			super(D.rows(), D.columns(), 1, 0.2, 0.5);
			scratch0 = new DenseDoubleMatrix1D(C.columns());
			scratch1 = new DenseDoubleMatrix1D(C.rows());
		}
		
		@Override
		public DoubleMatrix1D zMult(DoubleMatrix1D y, DoubleMatrix1D z, double alpha, double beta, boolean transposeA) {
			DoubleMatrix1D toAdd = null;
			if (beta != 0.0)
				toAdd = z.copy();
			
			zMult(y, z);
			
			if (alpha != 1.0)
				z.assign(DoubleFunctions.mult(alpha));
			
			if (beta != 0.0)
				z.assign(toAdd, DoubleFunctions.plus);
			
			return z;
		}
		
		@Override
		public DoubleMatrix1D zMult(DoubleMatrix1D y, DoubleMatrix1D z) {C.zMult(y, scratch1);
			choleskyB.solve(scratch1);
			C.zMult(scratch1, scratch0, 1.0, 0.0, true);
			
			D.zMult(y, z);
			
			z.assign(scratch0, DoubleFunctions.minus);
			
			return z;
		}
	}

}
