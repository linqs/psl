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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.solver.DefaultDoubleIterationMonitor;
import cern.colt.matrix.tdouble.algo.solver.DoubleCG;
import cern.colt.matrix.tdouble.algo.solver.DoubleIterationMonitor;
import cern.colt.matrix.tdouble.algo.solver.DoubleIterationReporter;
import cern.colt.matrix.tdouble.algo.solver.IterativeSolverDoubleNotConvergedException;
import cern.colt.matrix.tdouble.algo.solver.preconditioner.DoubleIdentity;
import cern.colt.matrix.tdouble.algo.solver.preconditioner.DoublePreconditioner;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.optimizer.conic.ipm.solver.preconditioner.IdentityPreconditionerFactory;
import edu.umd.cs.psl.optimizer.conic.ipm.solver.preconditioner.PreconditionerFactory;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;

/**
 * Solves normal systems using a conjugate gradient method.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ConjugateGradient implements NormalSystemSolver {
	
	private static final Logger log = LoggerFactory.getLogger(ConjugateGradient.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "cgsolver";
	
	/**
	 * Key for integer property. The ConjugateGradient solver will throw an
	 * exception if the conjugate gradient solver completes this many iterations
	 * without solving the normal system.
	 */
	public static final String CG_MAX_ITER_KEY = CONFIG_PREFIX + ".maxcgiter";
	/** Default value for CG_MAX_ITER_KEY property */
	public static final int CG_MAX_ITER_DEFAULT = 1000000;
	
	/**
	 * Key for double property. The ConjugateGradient solver will terminate
	 * as converged if the residual is less than this value times the
	 * initial residual.
	 */
	public static final String CG_REL_TOL_KEY = CONFIG_PREFIX + ".cgreltol";
	/** Default value for CG_REL_TOL_KEY property */
	public static final double CG_REL_TOL_DEFAULT = 10e-10;
	
	/**
	 * Key for double property. The ConjugateGradient solver will terminate
	 * as converged if the residual is less than this value.
	 */
	public static final String CG_ABS_TOL_KEY = CONFIG_PREFIX + ".cgabstol";
	/** Default value for CG_REL_TOL_KEY property */
	public static final double CG_ABS_TOL_DEFAULT = 10e-50;
	
	/**
	 * Key for double property. The ConjugateGradient solver will throw an
	 * exception if the conjugate graident solver reaches an iterate
	 * whose residual is at least this value times the initial residual.
	 */
	public static final String CG_DIV_TOL_KEY = CONFIG_PREFIX + ".cgdivtol";
	/** Default value for CG_DIV_TOL_KEY property */
	public static final double CG_DIV_TOL_DEFAULT = 10e5;
	
	/**
	 * Key for {@link Factory} or String property.
	 * 
	 * Should be set to a {@link PreconditionerFactory} or the fully qualified
	 * name of one. Will be used to instantiate a {@link DoublePreconditioner}.
	 */
	public static final String PRECONDITIONER_KEY = CONFIG_PREFIX + ".preconditioner";
	/**
	 * Default value for PRECONDITIONER_KEY.
	 * 
	 * Value is instance of {@link DoubleIdentity}. 
	 */
	public static final PreconditionerFactory PRECONDITIONER_DEFAULT = new IdentityPreconditionerFactory();
	
	private final int maxIter;
	private final double relTol;
	private final double absTol;
	private final double divTol;
	private final PreconditionerFactory preconditionerFactory;
	
	private DoubleCG cg;
	private DoublePreconditioner preconditioner;
	private DoubleIterationMonitor monitor;
	private DoubleMatrix2D A;
	private DoubleMatrix1D x;
	
	public ConjugateGradient(ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		maxIter = config.getInt(CG_MAX_ITER_KEY, CG_MAX_ITER_DEFAULT);
		relTol  = config.getDouble(CG_REL_TOL_KEY, CG_REL_TOL_DEFAULT);
		absTol  = config.getDouble(CG_ABS_TOL_KEY, CG_ABS_TOL_DEFAULT);
		divTol  = config.getDouble(CG_DIV_TOL_KEY, CG_DIV_TOL_DEFAULT);
		
		preconditionerFactory = (PreconditionerFactory) config.getFactory(PRECONDITIONER_KEY, PRECONDITIONER_DEFAULT);
		
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
		x = new DenseDoubleMatrix1D(program.getA().rows());
		cg = new DoubleCG(x);
		cg.setIterationMonitor(monitor);
		preconditioner = preconditionerFactory.getPreconditioner(program);
		cg.setPreconditioner(preconditioner);
	}

	@Override
	public void setA(SparseCCDoubleMatrix2D A) {
		this.A = A;
		preconditioner.setMatrix(A);
	}

	@Override
	public void solve(DoubleMatrix1D b) {
		x.assign(0);
		try {
			cg.solve(A, b, x);
			log.debug("Solved in {} iterations.", monitor.iterations());
		}
		catch (IterativeSolverDoubleNotConvergedException e) {
			throw new IllegalArgumentException(e);
		}
		b.assign(x);
	}

}
