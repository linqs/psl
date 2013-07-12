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
package edu.umd.cs.psl.optimizer.conic.ipm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.util.Dualizer;
import edu.umd.cs.psl.optimizer.conic.util.FeasiblePointInitializer;

/**
 * Primal-dual short-step interior point method.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class IPM implements ConicProgramSolver {
	
	private static final Logger log = LoggerFactory.getLogger(IPM.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "ipm";
	
	/**
	 * Key for boolean property. If true, the IPM will initialize the
	 * conic program to a feasible point before solving it.
	 * 
	 * @see FeasiblePointInitializer
	 */
	public static final String INIT_FEASIBLE_KEY = CONFIG_PREFIX + ".initfeasible";
	/** Default value for INIT_FEASIBLE_KEY property */
	public static final boolean INIT_FEASIBLE_DEFAULT = true;
	
	/**
	 * Key for boolean property. If true, the IPM will dualize the conic
	 * program before solving it. The IPM will substitute the results back
	 * into the original problem, so this should only affect the computational
	 * cost of {@link #solve(ConicProgram)}, not the quality of the solution.
	 * 
	 * @see Dualizer
	 */
	public static final String DUALIZE_KEY = CONFIG_PREFIX + ".dualize";
	/** Default value for DUALIZE_KEY property */
	public static final boolean DUALIZE_DEFAULT = true;
	
	/**
	 * Key for double property. The IPM will iterate until the duality gap
	 * is less than its value.
	 */
	public static final String DUALITY_GAP_THRESHOLD_KEY = CONFIG_PREFIX + ".dualitygapthreshold";
	/** Default value for DUALITY_GAP_THRESHOLD_KEY property. */
	public static final double DUALITY_GAP_THRESHOLD_DEFAULT = 0.0001;
	
	/**
	 * Key for double property. The IPM will iterate until the primal and dual infeasibilites
	 * are each less than its value.
	 * 
	 * @see ConicProgram#getPrimalInfeasibility()
	 * @see ConicProgram#getDualInfeasibility()
	 */
	public static final String INFEASIBILITY_THRESHOLD_KEY = CONFIG_PREFIX + ".infeasibilitythreshold";
	/** Default value for INFEASIBILITY_THRESHOLD_KEY property. */
	public static final double INFEASIBILITY_THRESHOLD_DEFAULT = 10e-8;
	
	protected ConicProgram currentProgram;
	
	protected FeasiblePointInitializer initializer;
	
	protected boolean dualized;
	protected Dualizer dualizer;
	
	protected final boolean initFeasible;
	protected final boolean tryDualize;
	protected final double dualityGapThreshold;
	protected final double infeasibilityThreshold;
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
	}
	
	private int stepNum;
	
	public IPM(ConfigBundle config) {
		initFeasible = config.getBoolean(INIT_FEASIBLE_KEY, INIT_FEASIBLE_DEFAULT);
		tryDualize = config.getBoolean(DUALIZE_KEY, DUALIZE_DEFAULT);
		dualityGapThreshold = config.getDouble(DUALITY_GAP_THRESHOLD_KEY, DUALITY_GAP_THRESHOLD_DEFAULT);
		infeasibilityThreshold = config.getDouble(INFEASIBILITY_THRESHOLD_KEY, INFEASIBILITY_THRESHOLD_DEFAULT);
		
		currentProgram = null;
		dualized = false;
		dualizer = null;
		initializer = null;
	}

	@Override
	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}
	
	@Override public void setConicProgram(ConicProgram p) {
		currentProgram = p;
		
		if (initFeasible && FeasiblePointInitializer.supportsConeTypes(currentProgram.getConeTypes())) {
			initializer = new FeasiblePointInitializer(currentProgram);
		}
		else {
			initializer = null;
		}
		
		if (tryDualize && Dualizer.supportsConeTypes(currentProgram.getConeTypes())) {
			dualized = true;
			dualizer = new Dualizer(currentProgram);
		}
		else {
			dualized = false;
		}
		
		if (initFeasible && FeasiblePointInitializer.supportsConeTypes(currentProgram.getConeTypes())) {
			initializer = new FeasiblePointInitializer((dualized) ? dualizer.getDualProgram() : currentProgram);
		}
		else {
			initializer = null;
		}
	}

	@Override
	public void solve() {
		if (currentProgram == null)
			throw new IllegalStateException("No conic program has been set.");
		
		ConicProgram program;
		
		currentProgram.checkOutMatrices();
		
		if (dualized) {
			dualizer.checkOutProgram();
			program = dualizer.getDualProgram();
			program.checkOutMatrices();
		}
		else {
			program = currentProgram;
		}
		
		if (initializer != null)
			initializer.makeFeasible();
		
		if (!supportsConeTypes(program.getConeTypes())) {
			throw new IllegalStateException("Program contains at least one unsupported cone."
					+ " Supported cones are non-negative orthant cones.");
		}

		DoubleMatrix2D A = program.getA();
		
		log.debug("Starting optimization with {} variables and {} constraints.", A.columns(), A.rows());
		
		if (program.getDualInfeasibility() > 0.01 || program.getPrimalInfeasibility() > 0.01)
			throw new IllegalStateException();
		
		doSolve(program);
		
		if (program.getDualInfeasibility() > 0.01 || program.getPrimalInfeasibility() > 0.01) {
			log.warn("Current primal infeasibility: {}.", program.getPrimalInfeasibility());
			log.warn("Current dual infeasibility: {}.", program.getDualInfeasibility());
			throw new IllegalStateException();
		}
		
		if (dualized) {
			program.checkInMatrices();
			dualizer.checkInProgram();
		}
		
		currentProgram.checkInMatrices();
		
		log.debug("Completed optimization.");
	}
	
	protected void doSolve(ConicProgram program) {
		DoubleMatrix1D x, s, g, r;
		DoubleMatrix2D Hinv, A;
		double mu, primalInfeasibility, dualInfeasibility, tau, muInitial, theta;
		boolean inNeighborhood;
		Set<Cone> cones = program.getCones();
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		A = program.getA();
		x = program.getX();
		s = program.getS();
		
		/* Initializes barrier matrices */
		g = DoubleFactory1D.dense.make(A.columns(), 1);
		Hinv = DoubleFactory2D.sparse.make(A.columns(), A.columns());
		
		/* Initializes mu */
		muInitial = alg.mult(x, s) / getV(program);
		mu = muInitial;
		
		/* Computes initial infeasibility */
		primalInfeasibility = program.getPrimalInfeasibility();
		dualInfeasibility = program.getDualInfeasibility();
		
		log.debug("Itr: Start -- Gap: {} -- P. Inf: {} -- D. Inf: {} -- Obj: {}", new Object[] {mu, primalInfeasibility, dualInfeasibility, alg.mult(program.getC(), x)});
		
		/*
		 * Iterates until the duality gap (mu) is sufficiently small and the
		 * point is sufficiently close to primal-dual feasibility
		 */
		stepNum = 0;
		inNeighborhood = false;
		while (mu >= dualityGapThreshold || primalInfeasibility >= infeasibilityThreshold || dualInfeasibility >= infeasibilityThreshold) {
			for (Cone cone : cones) {
				cone.setBarrierGradient(program.getVarMap(), x, g);
				cone.setBarrierHessianInv(program.getVarMap(), x, Hinv);
			}
			
			if (!inNeighborhood) {
				r = s.copy().assign(g, DoubleFunctions.plusMultSecond(muInitial));
				theta = alg.mult(r, alg.mult(Hinv, r)) / muInitial;
				if (theta < .1)
					inNeighborhood = true;
			}
			
			if (inNeighborhood && primalInfeasibility < infeasibilityThreshold && dualInfeasibility < infeasibilityThreshold) {
				tau = .85;
			}
			else {
				mu = muInitial;
				tau = 1;
			}

			step(program, g, Hinv, mu, tau, inNeighborhood);
			
			mu = alg.mult(x, s) / getV(program);
			primalInfeasibility = program.getPrimalInfeasibility();
			dualInfeasibility = program.getDualInfeasibility();
			log.debug("Itr: {} -- Gap: {} -- P. Inf: {} -- D. Inf: {} -- Obj: {}", new Object[] {++stepNum, mu, primalInfeasibility, dualInfeasibility, alg.mult(program.getC(), x)});
		}
	}

	protected void step(ConicProgram program, DoubleMatrix1D g, DoubleMatrix2D Hinv, double mu, double tau, boolean inNeighborhood) {
		SparseCCDoubleMatrix2D A;
		DoubleMatrix1D x, b, w, s, c, dx, dw, ds, r;
		
		A = program.getA();
		x = program.getX();
		b = program.getB();
		w = program.getW();
		s = program.getS();
		c = program.getC();
		
		ds = DoubleFactory1D.dense.make(A.columns());
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		SparseCCDoubleMatrix2D ccHinv = ((SparseDoubleMatrix2D) Hinv).getColumnCompressed(false);
		SparseCCDoubleMatrix2D partial = new SparseCCDoubleMatrix2D(A.rows(), A.columns());
		SparseCCDoubleMatrix2D coeff = new SparseCCDoubleMatrix2D(A.rows(), A.rows());
		
		A.zMult(ccHinv, partial, 1.0, 0.0, false, false);
		partial.zMult(A, coeff, 1.0, 0.0, false, true);
		r = b.copy().assign(alg.mult(A, x), DoubleFunctions.minus)
			.assign(alg.mult(partial, g).assign(DoubleFunctions.mult(tau)), DoubleFunctions.plus)
			.assign(DoubleFunctions.mult(mu))
			.assign(alg.mult(coeff, w), DoubleFunctions.minus)
			.assign(alg.mult(partial, c), DoubleFunctions.plus);
		dw = r;
		solveNormalSystem(coeff, dw, program);
		
		A.zMult(dw, ds, 1.0, 0.0, true);
		ds.assign(alg.mult(A.getTranspose(), w), DoubleFunctions.plus).assign(s, DoubleFunctions.plus)
			.assign(c, DoubleFunctions.minus).assign(DoubleFunctions.mult(-1.0));
		dx = alg.mult(ccHinv, g.copy().assign(DoubleFunctions.mult(-1*tau*mu)).assign(ds, DoubleFunctions.minus).assign(s, DoubleFunctions.minus))
			.assign(DoubleFunctions.div(mu));
		
		if (!inNeighborhood) {
			double primalStepSize = 1.0;
			double dualStepSize = 1.0;
			for (Cone cone : program.getCones()) {
				primalStepSize = Math.min(primalStepSize, cone.getMaxStep(program.getVarMap(), x, dx));
				dualStepSize = Math.min(dualStepSize, cone.getMaxStep(program.getVarMap(), s, ds));
			}

			dx.assign(DoubleFunctions.mult(primalStepSize));
			dw.assign(DoubleFunctions.mult(dualStepSize));
			ds.assign(DoubleFunctions.mult(dualStepSize));
		}
		
		w.assign(dw, DoubleFunctions.plus);
		s.assign(ds, DoubleFunctions.plus);
		x.assign(dx, DoubleFunctions.plus);
	}
	
	protected int getV(ConicProgram program) {
		return program.getNumNNOC() + 2*program.gtNumSOC();
	}
	
	protected void solveNormalSystem(SparseCCDoubleMatrix2D A, DoubleMatrix1D x, ConicProgram program) {
		SparseDoubleCholeskyDecomposition cd = new SparseDoubleCholeskyDecomposition(A, 1);
		cd.solve(x);
	}
}
