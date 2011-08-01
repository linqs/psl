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
package edu.umd.cs.psl.optimizer.conic.ipm;

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
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.Dualizer;

/**
 * Primal-dual short-step interior point method.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class IPM implements ConicProgramSolver {
	
	private static final Logger log = LoggerFactory.getLogger(IPM.class);
	
	public static final String CONFIG_NAMESPACE = "ipm";
	
	public static final String DUALIZE = CONFIG_NAMESPACE + ".dualize";
	public static final boolean DUALIZE_DEFAULT = true;
	
	public static final String INIT_FEASIBLE = CONFIG_NAMESPACE + ".initfeasible";
	public static final boolean INIT_FEASIBLE_DEFAULT = false;

	protected boolean dualize;
	protected boolean initFeasible;
	
	// Error messages
	protected static final String UNOWNED = "does not belong to this conic program.";
	protected static final String UNOWNED_VAR = "Variable " + UNOWNED;
	protected static final String UNOWNED_CONE = "Cone " + UNOWNED;
	protected static final String UNOWNED_LC = "Linear constraint " + UNOWNED;
	
	public IPM(ConfigBundle config) {
		dualize = config.getBoolean(DUALIZE, DUALIZE_DEFAULT);
		initFeasible = config.getBoolean(INIT_FEASIBLE, INIT_FEASIBLE_DEFAULT);
	}

	@Override
	public Double solve(ConicProgram program) {
		ConicProgram oldProgram = null;
		Dualizer dualizer = null;
		
		if (dualize) {
			log.debug("Dualizing!");
			oldProgram = program;
			dualizer = new Dualizer(program);
			program = dualizer.getData();
		}
		
		if (initFeasible)
			program.makeFeasible();

		double mu, primalFeasibilityDist, dualFeasibilityDist;
		DoubleMatrix2D A = program.getA();
		
		log.debug("Starting optimzation with {} variables and {} constraints.", A.columns(), A.rows());
		
		primalFeasibilityDist = program.distanceFromPrimalFeasibility();
		if (primalFeasibilityDist > 10e-8)
			log.error("Primal infeasible - Total distance: " + primalFeasibilityDist);
		
		dualFeasibilityDist = program.distanceFromDualFeasibility();
		if (dualFeasibilityDist > 10e-8)
			log.error("Dual infeasible - Total distance: " + dualFeasibilityDist);

		mu = doSolve(program);
		
		log.debug("Optimum found.");
		
		primalFeasibilityDist = program.distanceFromPrimalFeasibility();
		if (primalFeasibilityDist > 10e-8)
			log.error("Primal infeasible - Total distance: " + primalFeasibilityDist);
		
		dualFeasibilityDist = program.distanceFromDualFeasibility();
		if (dualFeasibilityDist > 10e-8)
			log.error("Dual infeasible - Total distance: " + dualFeasibilityDist);
		
		/* Updates variables */
		program.update();
		
		if (dualize) {
			dualizer.updateData();
			program = oldProgram;
		}
		
		return mu;
	}
	
	protected double doSolve(ConicProgram program) {
		DoubleMatrix1D x, s, g, r;
		DoubleMatrix2D Hinv, A;
		double mu, tau, muInitial, theta;
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
		muInitial = alg.mult(x, s) / program.getV();
		mu = muInitial;
		log.trace("Initial mu: {}", mu);
		
		/* Iterates until the duality gap (mu) is sufficiently small */
		inNeighborhood = false;
		while (mu > .01) {
			log.trace("Mu: {}", mu);
			log.trace("Setting barriers.");
			for (Cone cone : cones) {
				cone.setBarrierGradient(program.getVarMap(), x, g);
				cone.setBarrierHessianInv(program.getVarMap(), x, Hinv);
			}
			log.trace("Done setting barriers.");
			
			if (!inNeighborhood && initFeasible) {
				log.trace("Computing r.");
				r = s.copy().assign(g, DoubleFunctions.plusMultSecond(muInitial));
				log.trace("Done computing r.");
				log.trace("Computing theta.");
				theta = alg.mult(r, alg.mult(Hinv, r)) / muInitial;
				log.trace("Theta: {}", theta);
				if (theta < .1)
					inNeighborhood = true;
			}
			
			if (inNeighborhood || !initFeasible) {
				tau = .8;
			}
			else {
				mu = muInitial;
				tau = 1;
			}

			log.trace("Starting step.");
			step(program, g, Hinv, mu, tau, inNeighborhood);
			log.trace("Done step.");
			
			log.trace("Computing mu.");
			mu = alg.mult(x, s) / program.getV();
			log.trace("Done computing mu.");
		}
		
		return mu;
	}

	protected void step(ConicProgram program, DoubleMatrix1D g, DoubleMatrix2D Hinv, double mu, double tau, boolean inNeighborhood) {
		DoubleMatrix2D A;
		DoubleMatrix1D x, b, w, s, c, dx, dw, ds, r;
		
		A = program.getA();
		x = program.getX();
		b = program.getB();
		w = program.getW();
		s = program.getS();
		c = program.getC();
		
		ds = DoubleFactory1D.dense.make(A.columns());
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		SparseDoubleCholeskyDecomposition cd;
		
		SparseCCDoubleMatrix2D ccA = ((SparseDoubleMatrix2D) A).getColumnCompressed(false);
		SparseCCDoubleMatrix2D ccHinv = ((SparseDoubleMatrix2D) Hinv).getColumnCompressed(false);
		SparseCCDoubleMatrix2D partial = new SparseCCDoubleMatrix2D(A.rows(), A.columns());
		SparseCCDoubleMatrix2D coeff = new SparseCCDoubleMatrix2D(A.rows(), A.rows());
		
		log.trace("Computing coeff matrix.");
		ccA.zMult(ccHinv, partial, 1.0, 0.0, false, false);
		partial.zMult(ccA, coeff, 1.0, 0.0, false, true);
		log.trace("Done computing coeff matrix.");
		log.trace("Computing r.");
		if (initFeasible)
			r = alg.mult(partial, s.copy().assign(g, DoubleFunctions.plusMultSecond(tau * mu)));
		else
			r = b.copy().assign(alg.mult(A, x), DoubleFunctions.minus)
				.assign(alg.mult(partial, g).assign(DoubleFunctions.mult(tau)), DoubleFunctions.plus)
				.assign(DoubleFunctions.mult(mu))
				.assign(alg.mult(coeff, w), DoubleFunctions.minus)
				.assign(alg.mult(partial, c), DoubleFunctions.plus);
		log.trace("Done computing r.");
		log.trace("Computing Cholesky decomposition.");
		cd = new SparseDoubleCholeskyDecomposition(coeff, 1);
		log.trace("Done computing Cholesky decomposition.");
		log.trace("Nnz: {}", cd.getL().cardinality());
		dw = r;
		
		log.trace("Solving for dw.");
		cd.solve(dw);
		log.trace("Solving for ds.");
		ccA.zMult(dw, ds, 1.0, 0.0, true);
		if (initFeasible)
			ds.assign(DoubleFunctions.mult(-1.0));
		else
			ds.assign(alg.mult(ccA.getTranspose(), w), DoubleFunctions.plus).assign(s, DoubleFunctions.plus)
				.assign(c, DoubleFunctions.minus).assign(DoubleFunctions.mult(-1.0));
		log.trace("Solving for dx.");
		dx = alg.mult(ccHinv, g.copy().assign(DoubleFunctions.mult(-1*tau*mu)).assign(ds, DoubleFunctions.minus).assign(s, DoubleFunctions.minus))
			.assign(DoubleFunctions.div(mu));
		log.trace("Done solving.");
		
		if (!inNeighborhood) {
			double primalStepSize = 1.0;
			double dualStepSize = 1.0;
			log.trace("Computing primal step size.");
			for (Cone cone : program.getCones())
				primalStepSize = Math.min(primalStepSize, cone.getMaxStep(program.getVarMap(), x, dx));
			log.trace("Computing dual step size.");
			for (int i = 0; i < s.size(); i++) {
				if (ds.get(i) < 0)
					dualStepSize = Math.min(dualStepSize, (s.get(i) * .67) / (- ds.get(i)));
			}

			log.trace("Primal step size: {} * {}", primalStepSize, alg.norm2(dx));
			log.trace("Dual step size: {} * {}", dualStepSize, alg.norm2(ds));
			

			log.trace("Assigning dx.");
			dx.assign(DoubleFunctions.mult(primalStepSize));
			log.trace("Assigning dw.");
			dw.assign(DoubleFunctions.mult(dualStepSize));
			log.trace("Assigning ds.");
			ds.assign(DoubleFunctions.mult(dualStepSize));
			log.trace("Done assigning steps.");
		}
		
		log.trace("Assigning w.");
		w.assign(dw, DoubleFunctions.plus);
		log.trace("Assigning s.");
		s.assign(ds, DoubleFunctions.plus);
		log.trace("Assigning x.");
		x.assign(dx, DoubleFunctions.plus);
		log.trace("Done assigning variables.");
		
		log.trace("Dx check: {}", alg.norm2(alg.mult(A, dx)));
		log.trace("Distance from primal feasibility: {}", alg.norm2(b.copy().assign(alg.mult(A, x), DoubleFunctions.minus)));
		log.trace("Distance from dual feasibility: {}", alg.norm2(A.zMult(w, s.copy(), 1.0, 1.0, true).assign(c, DoubleFunctions.minus)));
	}
}
