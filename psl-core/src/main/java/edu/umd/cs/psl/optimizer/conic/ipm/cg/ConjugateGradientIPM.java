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
package edu.umd.cs.psl.optimizer.conic.ipm.cg;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.algo.solver.DefaultDoubleIterationMonitor;
import cern.colt.matrix.tdouble.algo.solver.DoubleCG;
import cern.colt.matrix.tdouble.algo.solver.IterativeSolverDoubleNotConvergedException;
import cern.colt.matrix.tdouble.algo.solver.preconditioner.DoubleIdentity;
import cern.colt.matrix.tdouble.algo.solver.preconditioner.DoublePreconditioner;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.optimizer.conic.ipm.IPM;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;

/**
 * Primal-dual short-step interior point method.
 * 
 * This IPM solves the normal system using an iterative conjugate gradient solver.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ConjugateGradientIPM extends IPM {
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "cgipm";
	
	/**
	 * Key for integer property. The ConjugateGradientIPM will throw an
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
	public static final double CG_REL_TOL_DEFAULT = 1e-10;
	
	/**
	 * Key for double property. The conjugate gradient solver will terminate
	 * as converged if the residual is less than this value.
	 */
	public static final String CG_ABS_TOL_KEY = CONFIG_PREFIX + ".cgabstol";
	/** Default value for CG_REL_TOL_KEY property */
	public static final double CG_ABS_TOL_DEFAULT = 1e-50;
	
	/**
	 * Key for double property. The ConjugateGradientIPM will throw an
	 * exception if the conjugate graident solver reaches an iterate
	 * whose residual is at least this value times the initial residual.
	 */
	public static final String CG_DIV_TOL_KEY = CONFIG_PREFIX + ".cgdivtol";
	/** Default value for CG_DIV_TOL_KEY property */
	public static final double CG_DIV_TOL_DEFAULT = 1e5;
	
	private final int maxIter;
	private final double relTol;
	private final double absTol;
	private final double divTol;

	public ConjugateGradientIPM(ConfigBundle config) {
		super(config);
		
		maxIter = config.getInt(CG_MAX_ITER_KEY, CG_MAX_ITER_DEFAULT);
		relTol = config.getDouble(CG_REL_TOL_KEY, CG_REL_TOL_DEFAULT);
		absTol = config.getDouble(CG_ABS_TOL_KEY, CG_ABS_TOL_DEFAULT);
		divTol = config.getDouble(CG_DIV_TOL_KEY, CG_DIV_TOL_DEFAULT);
	}
	
	@Override
	protected void solveNormalSystem(SparseCCDoubleMatrix2D A, DoubleMatrix1D x, ConicProgram program) {
		DoubleCG cg = new DoubleCG(x);
		cg.setIterationMonitor(new DefaultDoubleIterationMonitor(maxIter, relTol, absTol, divTol));
		DoublePreconditioner preconditioner = new DoubleIdentity();
		preconditioner.setMatrix(A);
		cg.setPreconditioner(preconditioner);
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
