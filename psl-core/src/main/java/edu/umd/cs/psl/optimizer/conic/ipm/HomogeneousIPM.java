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

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

/**
 * Primal-dual interior-point method using the homogeneous model.
 * 
 * This solver follows the algorithm presented in
 * E. D. Andersen, C. Roos and T. Terlaky. "On implementing a primal-dual
 * interior-point method for conic quadratic optimization."
 * <i>Math. Programming</i> 95(2), February 2003.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 *
 */
public class HomogeneousIPM implements ConicProgramSolver {
	
	private static final Logger log = LoggerFactory.getLogger(HomogeneousIPM.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "hipm";
	
	/**
	 * Key for double property. The IPM will iterate until the duality gap
	 * is less than its value.
	 */
	public static final String DUALITY_GAP_RED_THRESHOLD_KEY = CONFIG_PREFIX + ".dualitygapredthreshold";
	/** Default value for DUALITY_GAP_THRESHOLD_KEY property. */
	public static final double DUALITY_GAP_RED_THRESHOLD_DEFAULT = 0.01;
	
	/**
	 * Key for double property. The IPM will iterate until the primal and dual infeasibilites
	 * are each less than its value.
	 */
	public static final String INFEASIBILITY_RED_THRESHOLD_KEY = CONFIG_PREFIX + ".infeasibilityredthreshold";
	/** Default value for INFEASIBILITY_THRESHOLD_KEY property. */
	public static final double INFEASIBILITY_RED_THRESHOLD_DEFAULT = 10e-8;
	
	/**
	 * Key for double property. The IPM will iterate until the primal and dual infeasibilites
	 * are each less than its value.
	 */
	public static final String SIG_THRESHOLD_KEY = CONFIG_PREFIX + ".sigthreshold";
	/** Default value for INFEASIBILITY_THRESHOLD_KEY property. */
	public static final double SIG_THRESHOLD_DEFAULT = 10e-8;
	
	/**
	 * Key for double property. The IPM will iterate until the primal and dual infeasibilites
	 * are each less than its value.
	 */
	public static final String TAU_THRESHOLD_KEY = CONFIG_PREFIX + ".tauthreshold";
	/** Default value for INFEASIBILITY_THRESHOLD_KEY property. */
	public static final double TAU_THRESHOLD_DEFAULT = 10e-8;
	
	/**
	 * Key for double property. The IPM will iterate until the primal and dual infeasibilites
	 * are each less than its value.
	 */
	public static final String MU_THRESHOLD_KEY = CONFIG_PREFIX + ".muthreshold";
	/** Default value for INFEASIBILITY_THRESHOLD_KEY property. */
	public static final double MU_THRESHOLD_DEFAULT = 10e-8;

	private double dualityGapRedThreshold;
	private double infeasibilityRedThreshold;
	private double sigThreshold;
	private double tauThreshold;
	private double muThreshold;
	
	private int stepNum;
	
	public HomogeneousIPM(ConfigBundle config) {
		dualityGapRedThreshold = config.getDouble(DUALITY_GAP_RED_THRESHOLD_KEY, DUALITY_GAP_RED_THRESHOLD_DEFAULT);
		infeasibilityRedThreshold = config.getDouble(INFEASIBILITY_RED_THRESHOLD_KEY, INFEASIBILITY_RED_THRESHOLD_DEFAULT);
		sigThreshold = config.getDouble(SIG_THRESHOLD_KEY, SIG_THRESHOLD_DEFAULT);
		tauThreshold = config.getDouble(TAU_THRESHOLD_KEY, TAU_THRESHOLD_DEFAULT);
		muThreshold = config.getDouble(MU_THRESHOLD_KEY, MU_THRESHOLD_DEFAULT);
	}

	@Override
	public Double solve(ConicProgram program) {
		program.checkOutMatrices();

		double mu;
		DoubleMatrix2D A = program.getA();
		
		log.debug("Starting optimzation with {} variables and {} constraints.", A.columns(), A.rows());
		
		mu = doSolve(program);
		
		log.debug("Optimum found.");
		
		program.checkInMatrices();
		
		return mu;
	}
	
	private double doSolve(ConicProgram program) {
		DoubleMatrix2D A = program.getA();
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D w = program.getW();
		DoubleMatrix1D s = program.getS();
		DoubleMatrix1D c = program.getC();
		
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		double cDotX, bDotW;
		double gap, mu, primalInfeasibility, dualInfeasibility, sig;
		boolean primalFeasible, dualFeasible, isSig, gapIsReduced, tauIsSmall, muIsSmall; 
		boolean solved, programInfeasible, illPosed;
		
		/* Initializes program matrices that can be reused for entire procedure */
		HIPMProgramMatrices pm = getProgramMatrices(program);
		
		/* Initializes program variables */
		pm.T.zMult(pm.e, x);
		s.assign(x);
		w.assign(0.0);
//		for (Cone cone : program.getCones()) {
//			cone.setBarrierGradient(program.getVarMap(), x, s);
//		}
//		s.assign(DoubleFunctions.mult(-1.0));
//		x.assign(1.0);
//		w.assign(0.0);
//		s.assign(1.0);
		
		/* Initializes special variables for the homogeneous model */
		HIPMVars vars = new HIPMVars();
		vars.tau = 1;
		vars.kappa = x.zDotProduct(s) / pm.k;
		
		/* Computes values for stopping criteria */
		double muZero = (x.zDotProduct(s) + vars.tau * vars.kappa) / pm.k;
		double primalInfRedDenom = Math.max(1.0, alg.norm2(
				A.zMult(x, b.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, false)));
		double dualInfRedDenom = Math.max(1.0, alg.norm2(
				A.zMult(w, c.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, true)
				.assign(s, DoubleFunctions.plus)));
		double gapRedDenom = Math.max(1.0, Math.abs(b.zDotProduct(w) - c.zDotProduct(x) - vars.kappa));
		
		stepNum = 0;
		do {
			step(program, pm, vars);
			
			mu		= (x.zDotProduct(s) + vars.tau * vars.kappa) / pm.k;
			cDotX	= c.zDotProduct(x);
			bDotW	= b.zDotProduct(w);
			
			primalInfeasibility = alg.norm2(
					A.zMult(x, b.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, false)
					) / primalInfRedDenom;
			
			dualInfeasibility = alg.norm2(
					A.zMult(w, c.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, true)
					.assign(s, DoubleFunctions.plus)
					) / dualInfRedDenom;
			
			gap = Math.abs(bDotW - cDotX - vars.kappa) / gapRedDenom;
			sig = Math.abs(cDotX / vars.tau - bDotW / vars.tau) / (1 + Math.abs(bDotW / vars.tau));
			
			log.trace("Itr: {} -- P. Inf: {} -- D. Inf: {} -- Sig: {}", new Object[] {++stepNum, primalInfeasibility, dualInfeasibility, sig});
			
			primalFeasible	= primalInfeasibility <= infeasibilityRedThreshold;
			dualFeasible	= dualInfeasibility <= infeasibilityRedThreshold;
			isSig			= sig <= sigThreshold;
			gapIsReduced	= gap <= dualityGapRedThreshold;
			tauIsSmall		= vars.tau <= tauThreshold * Math.max(1.0, vars.kappa);
			muIsSmall		= mu <= muThreshold * muZero;
			
			solved				= primalFeasible && dualFeasible && isSig;
			programInfeasible	= primalFeasible && dualFeasible && gapIsReduced && tauIsSmall;
			illPosed			= muIsSmall && tauIsSmall;
		} while (!solved && !programInfeasible && !illPosed);
		
		if (illPosed)
			throw new IllegalArgumentException("Optimization program is ill-posed.");
		else if (programInfeasible)
			throw new IllegalArgumentException("Optimization program is infeasible.");
		else {
			x.assign(DoubleFunctions.div(vars.tau));
			w.assign(DoubleFunctions.div(vars.tau));
			s.assign(DoubleFunctions.div(vars.tau));
		}
		
		return mu;
	}

	private void step(ConicProgram program, HIPMProgramMatrices pm, HIPMVars vars) {
		log.trace("Getting intermediates.");
		HIPMIntermediates im = getIntermediates(program, pm, vars);
		log.trace("Getting predictor residuals.");
		HIPMResiduals res = getResiduals(program, pm, vars, im, 0.0, null);
		log.trace("Getting Newton search direction.");
		HIPMSearchDirection sd = getSearchDirection(program, pm, vars, res, im);
		log.trace("Getting Newton max step size.");
		double alphaMax = getMaxStepSize(program, vars, sd);
		log.trace("{}", alphaMax);
		double delta = 0.5;
		double gamma = Math.min(delta, Math.pow(1-alphaMax, 2)) * (1-alphaMax);
		log.trace("Getting corrected residuals.");
		res = getResiduals(program, pm, vars, im, gamma, sd);
		log.trace("Getting corrected search direction.");
		sd = getSearchDirection(program, pm, vars, res, im);
		logResults(program, pm, vars, res, im, sd, gamma);
		log.trace("Getting step size.");
		alphaMax = getMaxStepSize(program, vars, sd);
		double stepSize = getStepSize(program, vars, im, sd, alphaMax, 10e-8, gamma);
		log.trace("Step size: {}", stepSize);
		
		program.getX().assign(sd.dx.assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
		program.getW().assign(sd.dw.assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
		program.getS().assign(sd.ds.assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
		vars.tau += sd.dTau * stepSize;
		vars.kappa += sd.dKappa * stepSize;
	}
	
	private HIPMProgramMatrices getProgramMatrices(ConicProgram program) {
		HIPMProgramMatrices pm = new HIPMProgramMatrices();
		
		SparseDoubleMatrix2D A = program.getA();
		int size = A.columns();
		
		pm.k	= program.getCones().size();
		pm.e	= new DenseDoubleMatrix1D(size);
		pm.T	= new SparseDoubleMatrix2D(size, size);
		pm.invT	= new SparseDoubleMatrix2D(size, size);
		pm.Q	= new SparseDoubleMatrix2D(size, size);
		pm.invQ	= new SparseDoubleMatrix2D(size, size);
		
		// TODO: Watch out for new cone types. Should throw exception if they exist. 
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int i = program.index(cone.getVariable());
			pm.e.setQuick(i, 1.0);
			pm.T.setQuick(i, i, 1.0);
			pm.invT.setQuick(i, i, 1.0);
			pm.Q.setQuick(i, i, 1.0);
			pm.invQ.setQuick(i, i, 1.0);
		}
		
		for (SecondOrderCone cone : program.getSecondOrderCones()) {
			for (Variable var : cone.getVariables()) {
				int i = program.index(var);
				pm.e.setQuick(i, 0);
				pm.T.setQuick(i, i, 1.0);
				pm.invT.setQuick(i, i, 1.0);
				pm.Q.setQuick(i, i, -1.0);
				pm.invQ.setQuick(i, i, -1.0);
			}
			int i = program.index(cone.getNthVariable());
			pm.e.setQuick(i, 1.0);
			pm.Q.setQuick(i, i, 1.0);
			pm.invQ.setQuick(i, i, 1.0);
		}
		
		return pm;
	}
	
	private HIPMIntermediates getIntermediates(ConicProgram program
			, HIPMProgramMatrices pm, HIPMVars vars
			) {
		HIPMIntermediates im = new HIPMIntermediates();
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		SparseDoubleMatrix2D A = program.getA();
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D s = program.getS();
		DoubleMatrix1D c = program.getC();
		int n = (int) x.size();
		
		im.mu = (x.zDotProduct(s) + vars.tau * vars.kappa) / pm.k;
		
		im.ThetaW			= new SparseDoubleMatrix2D(n, n);
		im.invThetaInvW		= new SparseDoubleMatrix2D(n, n);
		im.ThetaSqWSq		= new SparseDoubleMatrix2D(n, n);
		im.invThetaSqInvWSq	= new SparseDoubleMatrix2D(n, n);
		im.XBar				= new SparseDoubleMatrix2D(n, n);
		im.invXBar			= new SparseDoubleMatrix2D(n, n);
		im.SBar				= new SparseDoubleMatrix2D(n, n);
		
		log.trace("Processing NNOCs.");
		
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.index(cone.getVariable());
			double thetaSq = s.getQuick(index) / x.getQuick(index);
			double theta = Math.sqrt(thetaSq);
			double invTheta = 1 / theta;
			im.ThetaW.setQuick(index, index, theta);
			im.invThetaInvW.setQuick(index, index, invTheta);
			im.ThetaSqWSq.setQuick(index, index, thetaSq);
			im.invThetaSqInvWSq.setQuick(index, index, 1 / thetaSq);
			im.XBar.setQuick(index, index, theta * x.getQuick(index));
			im.invXBar.setQuick(index, index, invTheta * 1 / x.getQuick(index));
			im.SBar.setQuick(index, index, invTheta * s.getQuick(index));
		}
		
		log.trace("Processing SOCs.");
		
		for (SecondOrderCone cone : program.getSecondOrderCones()) {
			/* Collects the cone's variables */
			Set<Variable> coneVars = cone.getVariables();
			Variable nthVariable = cone.getNthVariable();
			int nCone = coneVars.size();
			
			/* Creates an array of the variables' indices */
			int[] selection = new int[nCone];
			int selectionIndex = 1;
			for (Variable var : coneVars) {
				if (nthVariable.equals(var))
					selection[0] = program.index(var);
				else
					selection[selectionIndex++] = program.index(var);
			}
			
			/* Selects the variables */
			DoubleMatrix1D xSel = x.viewSelection(selection);
			DoubleMatrix1D sSel = s.viewSelection(selection);
			
			/* Creates a Q matrix for the variables */
			DoubleMatrix2D Q = new SparseDoubleMatrix2D(nCone, nCone);
			Q.setQuick(0, 0, 1.0);
			for (int i = 1; i < nCone; i++)
				Q.setQuick(i, i, -1.0);
			
			/* Computes Q-norms */
			double xSelQNorm = Math.sqrt(Q.zMult(xSel, null).zDotProduct(xSel));
			double sSelQNorm = Math.sqrt(Q.zMult(sSel, null).zDotProduct(sSel));
			
			/* Computes wCone vector */
			double gamma = Math.sqrt(0.5 + xSel.zDotProduct(sSel) / (2 * xSelQNorm * sSelQNorm));
			DoubleMatrix1D wCone = sSel.copy().assign(DoubleFunctions.div(sSelQNorm));
			wCone.assign(Q.zMult(xSel, null).assign(DoubleFunctions.div(xSelQNorm)), DoubleFunctions.plus);
			wCone.assign(DoubleFunctions.div(2 * gamma));
			
			/*
			 * Computes selections of matrices
			 */
			
			/* Gets the selections */
			DoubleMatrix2D ThetaWSel			= im.ThetaW.viewSelection(selection, selection);
			DoubleMatrix2D invThetaInvWSel		= im.invThetaInvW.viewSelection(selection, selection);
			DoubleMatrix2D XBarSel				= im.XBar.viewSelection(selection, selection);
			DoubleMatrix2D invXBarSel			= im.invXBar.viewSelection(selection, selection);
			DoubleMatrix2D SBarSel				= im.SBar.viewSelection(selection, selection);
			DoubleMatrix2D ThetaSqWSqSel		= im.ThetaSqWSq.viewSelection(selection, selection);
			DoubleMatrix2D invThetaSqInvWSqSel	= im.invThetaSqInvWSq.viewSelection(selection, selection);
			
			/* Computes W selection */
			//DoubleMatrix1D e = new DenseDoubleMatrix1D(nCone).assign(1.0);
			double beta = Math.sqrt(sSelQNorm / xSelQNorm);
			ThetaWSel.viewColumn(0).assign(wCone);
			ThetaWSel.viewRow(0).assign(wCone);
			DoubleMatrix2D subMatrixSelection = ThetaWSel.viewPart(1, 1, nCone-1, nCone-1);
			DoubleMatrix1D subVectorSelection = wCone.viewPart(1, nCone-1);
			alg.multOuter(subVectorSelection, subVectorSelection, subMatrixSelection);
			subMatrixSelection.assign(DoubleFunctions.div(1+wCone.getQuick(0)));
			for (int i = 1; i < nCone; i++)
				ThetaWSel.setQuick(i, i, ThetaWSel.getQuick(i, i) + 1);
			ThetaWSel.assign(DoubleFunctions.mult(beta));
			try{
			invThetaInvWSel.assign(alg.inverse(ThetaWSel));
			}
			catch (IllegalArgumentException exception) {
				throw exception;
			}
			
			/* Computes squared selections */
			invThetaInvWSel.zMult(invThetaInvWSel, invThetaSqInvWSqSel);
			ThetaWSel.zMult(ThetaWSel, ThetaSqWSqSel);
			
			/* Computes selections of XBar, invXBar, and SBar */
			XBarSel.assign(getArrowheadMatrix(ThetaWSel.zMult(xSel, null)));
			invXBarSel.assign(alg.inverse(XBarSel));
			SBarSel.assign(getArrowheadMatrix(invThetaInvWSel.zMult(sSel, null)));
		}
		
		log.trace("Computing intermediate matrices.");
		
		/* Computes more intermediate matrices */
		im.AInvThetaSqInvWSq = new SparseCCDoubleMatrix2D(A.rows(), n);
		A.getColumnCompressed(false).zMult(im.invThetaSqInvWSq.getColumnCompressed(false), im.AInvThetaSqInvWSq, 1.0, 0.0, false, false);
		
		log.trace("Computing M.");
		
		/* Computes M and finds its Cholesky factorization */
		SparseCCDoubleMatrix2D M = new SparseCCDoubleMatrix2D(A.rows(), A.rows());
		im.AInvThetaSqInvWSq.zMult(A.getColumnCompressed(false), M, 1.0, 0.0, false, true);
		log.trace("Starting decomposition.");
		im.M = new SparseDoubleCholeskyDecomposition(M, 1);
		log.trace("Finished decomposition.");
		
		/* Computes intermediate vectors */
		
		/* g2 */
		im.g2 = im.AInvThetaSqInvWSq.zMult(c, b.copy(), 1.0, 1.0, false);
		im.M.solve(im.g2);
		
		/* g1 */
		im.g1 = im.invThetaSqInvWSq.zMult(
				c.copy().assign(A.zMult(im.g2, null, 1.0, 0.0, true), DoubleFunctions.minus)
				, null).assign(DoubleFunctions.mult(-1.0));
		
		return im;
	}
	
	/**
	 * Computes residuals for the system of equations to be solved.
	 * 
	 * This method optionally uses a search direction to correct the residuals
	 * with a second-order estimate. If the search direction is the Newton
	 * direction, then this is equivalent to the correction used in Mehrotra's
	 * predictor-corrector method.
	 * 
	 * @param program	program being solved
	 * @param pm		saved program information
	 * @param vars		homogeneous-model variables
	 * @param im		saved step-specific computations
	 * @param gamma		parameter in [0,1] controlling adherence to central path.
	 * 						If 0, this method returns the Newton direction.
	 * @param sd		search direction used to estimate second-order terms.
	 * 						If null, no second-order terms are added.
	 * @return  the computed residuals
	 */
	private HIPMResiduals getResiduals(ConicProgram program
			, HIPMProgramMatrices pm, HIPMVars vars, HIPMIntermediates im
			, double gamma, HIPMSearchDirection sd
			) {
		HIPMResiduals res = new HIPMResiduals();
		
		SparseDoubleMatrix2D A = program.getA();
		DenseDoubleMatrix1D x = program.getX();
		DenseDoubleMatrix1D b = program.getB();
		DenseDoubleMatrix1D w = program.getW();
		DenseDoubleMatrix1D s = program.getS();
		DenseDoubleMatrix1D c = program.getC();
		
		res.r1 = A.zMult(x, b.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, false)
				.assign(DoubleFunctions.mult(gamma-1));
		res.r2 = A.zMult(w, c.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, true);
		res.r2.assign(s, DoubleFunctions.plus).assign(DoubleFunctions.mult(gamma-1));
		res.r3 = (gamma-1) * (c.zDotProduct(x) - b.zDotProduct(w) + vars.kappa);
		res.r4 = pm.e.copy().assign(DoubleFunctions.mult(gamma*im.mu)).assign(im.XBar.zMult(im.SBar.zMult(pm.e, null), null), DoubleFunctions.minus);
		res.r5 = gamma * im.mu - vars.tau * vars.kappa;
		
		/* Corrects residuals with second-order estimate */
		if (sd != null) {
			DoubleMatrix1D dxn = pm.T.zMult(im.ThetaW.zMult(sd.dx, null), null);
			DoubleMatrix1D dsn = pm.T.zMult(im.invThetaInvW.zMult(sd.ds, null), null);
			int size = (int) dxn.size();
			DoubleMatrix2D Dxn = new SparseDoubleMatrix2D(size, size);
			DoubleMatrix2D Dsn = new SparseDoubleMatrix2D(size, size);
			
			for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
				int index = program.index(cone.getVariable());
				Dxn.setQuick(index, index, dxn.getQuick(index));
				Dsn.setQuick(index, index, dsn.getQuick(index));
			}
			
			for (SecondOrderCone cone : program.getSecondOrderCones()) {
				/* Collects the cone's variables */
				Set<Variable> coneVars = cone.getVariables();
				Variable nthVariable = cone.getNthVariable();
				int nCone = coneVars.size();
				
				/* Creates an array of the variables' indices */
				int[] selection = new int[nCone];
				int selectionIndex = 1;
				for (Variable var : coneVars) {
					if (nthVariable.equals(var))
						selection[0] = program.index(var);
					else
						selection[selectionIndex++] = program.index(var);
				}
				
				/* Selects the variables */
				DoubleMatrix1D dxnSel = dxn.viewSelection(selection);
				DoubleMatrix1D dsnSel = dsn.viewSelection(selection);
				
				/* Gets the selections */
				DoubleMatrix2D DxnSel				= Dxn.viewSelection(selection, selection);
				DoubleMatrix2D DsnSel				= Dsn.viewSelection(selection, selection);
				
				/* Computes the arrowhead matrices */
				DxnSel.assign(getArrowheadMatrix(dxnSel));
				DsnSel.assign(getArrowheadMatrix(dsnSel));
			}
			
			res.r4.assign(Dxn.zMult(Dsn.zMult(pm.e, null), null), DoubleFunctions.minus);
			res.r5 -= sd.dTau * sd.dKappa;
			log.trace("Dot product: {}", sd.dx.zDotProduct(sd.ds));
			log.trace("Fancier product: {}", Dxn.zMult(Dsn.zMult(pm.e, null), null).zDotProduct(pm.e));
		}
		
		return res;
	}
	
	private HIPMSearchDirection getSearchDirection(ConicProgram program
			, HIPMProgramMatrices pm, HIPMVars vars, HIPMResiduals res
			, HIPMIntermediates im
			) {
		HIPMSearchDirection sd = new HIPMSearchDirection();
		SparseDoubleMatrix2D A = program.getA();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D c = program.getC();
		
		/* h2 */
		DoubleMatrix1D h2 = res.r1.copy().assign(
				im.AInvThetaSqInvWSq.zMult(res.r2.copy().assign(
						im.ThetaW.zMult(
								pm.invT.zMult(im.invXBar.zMult(res.r4, null)
								, null), null)
						, DoubleFunctions.minus), null)
				, DoubleFunctions.plus);
		im.M.solve(h2);
		
		/* h1 */
		DoubleMatrix1D h1 = im.invThetaSqInvWSq.zMult(
				res.r2.copy().assign(
						im.ThetaW.zMult(im.invXBar.zMult(res.r4, null), null)
						, DoubleFunctions.minus)
				.assign(
						A.zMult(h2, null, 1.0, 0.0, true)
						, DoubleFunctions.minus)
				, null).assign(DoubleFunctions.mult(-1.0));
	
		/* Computes search direction */
		sd.dTau = (res.r3 - c.zDotProduct(h1) + b.zDotProduct(h2) - res.r5 / vars.tau)
				/ (-1*(vars.kappa/vars.tau) + c.zDotProduct(im.g1) - b.zDotProduct(im.g2));
		sd.dx = im.g1.copy().assign(DoubleFunctions.mult(sd.dTau)).assign(h1, DoubleFunctions.plus);
		sd.dw = im.g2.copy().assign(DoubleFunctions.mult(sd.dTau)).assign(h2, DoubleFunctions.plus);
		sd.dKappa = (res.r5 - vars.kappa*sd.dTau) / vars.tau;
		sd.ds = im.ThetaW.zMult(pm.T.zMult(im.invXBar.zMult(
				res.r4.copy().assign(
						im.SBar.zMult(pm.T.zMult(im.ThetaW.zMult(sd.dx, null), null), null)
						, DoubleFunctions.minus)
				, null), null), null);
		
		return sd;
	}
	
	private double getMaxStepSize(ConicProgram program
			, HIPMVars vars, HIPMSearchDirection sd
			) {
		double alphaMax = 1.0;
		
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D s = program.getS();
		
		/* Checks distance to boundaries of cones */
		for (Cone cone : program.getCones()) {
			alphaMax = Math.min(cone.getMaxStep(program.getVarMap(), x, sd.dx), alphaMax);
			alphaMax = Math.min(cone.getMaxStep(program.getVarMap(), s, sd.ds), alphaMax);
		}
		
		/* Checks distance to min. tau */
		if (sd.dTau < 0)
			alphaMax = Math.min(-1 * vars.tau / sd.dTau, alphaMax);
		
		/* Checks distance to min. kappa */
		if (sd.dKappa < 0)
			alphaMax = Math.min(-1 * vars.kappa / sd.dKappa, alphaMax);
		
		return alphaMax;
	}
	
	private double getStepSize(ConicProgram program
			, HIPMVars vars, HIPMIntermediates im, HIPMSearchDirection sd
			, double alphaMax, double beta, double gamma
			) {
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D s = program.getS();
		double stepSize = alphaMax;
		double stepSizeDecrement = alphaMax / 20;
		
		double ssCond = getStepSizeCondition(stepSize, beta, gamma, im.mu);
		
		while ((vars.tau + stepSize * sd.dTau) * (vars.kappa + stepSize * sd.dKappa) < ssCond) {
			log.trace("Decrementing step size.");
			stepSize -= stepSizeDecrement;
			ssCond = getStepSizeCondition(stepSize, beta, gamma, im.mu);
		}
		
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.index(cone.getVariable());
			double vX1 = Math.pow(x.getQuick(index), 2);
			double vX2 = 2 * sd.dx.getQuick(index) * x.getQuick(index);
			double vX3 = Math.pow(sd.dx.getQuick(index), 2);

			double vS1 = Math.pow(s.getQuick(index), 2);
			double vS2 = 2 * sd.ds.getQuick(index) * s.getQuick(index);
			double vS3 = Math.pow(sd.ds.getQuick(index), 2);
			
//			while (Math.sqrt(
//					(vX1 + stepSize * vX2 + stepSize * stepSize * vX3)
//					* (vS1 + stepSize * vS2 + stepSize * stepSize * vS3)
//					) < ssCond) {
			while ((x.getQuick(index) + sd.dx.getQuick(index) * stepSize)
					* (s.getQuick(index) + sd.ds.getQuick(index) * stepSize)
					< ssCond) {
				log.trace("Decrementing step size.");
				stepSize -= stepSizeDecrement;
				ssCond = getStepSizeCondition(stepSize, beta, gamma, im.mu);
				if (stepSize <= 0)
					throw new IllegalStateException("Stuck.");
			}
		}
		
		for (SecondOrderCone cone : program.getSecondOrderCones()) {
			/* Collects the cone's variables */
			Set<Variable> coneVars = cone.getVariables();
			Variable nthVariable = cone.getNthVariable();
			int nCone = coneVars.size();
			
			/* Creates an array of the variables' indices */
			int[] selection = new int[nCone];
			int selectionIndex = 1;
			for (Variable var : coneVars) {
				if (nthVariable.equals(var))
					selection[0] = program.index(var);
				else
					selection[selectionIndex++] = program.index(var);
			}
			
			/* Selects the variables */
			DoubleMatrix1D xSel = x.viewSelection(selection);
			DoubleMatrix1D sSel = s.viewSelection(selection);
			DoubleMatrix1D dxSel = sd.dx.viewSelection(selection);
			DoubleMatrix1D dsSel = sd.ds.viewSelection(selection);
			
			/* Creates a Q matrix for the variables */
			DoubleMatrix2D Q = new SparseDoubleMatrix2D(nCone, nCone);
			Q.setQuick(0, 0, 1.0);
			for (int i = 1; i < nCone; i++)
				Q.setQuick(i, i, -1.0);
			
			double vX1 = Q.zMult(xSel, null).zDotProduct(xSel);
			double vX2 = 2 * Q.zMult(xSel, null).zDotProduct(dxSel);
			double vX3 = Q.zMult(dxSel, null).zDotProduct(dxSel);

			double vS1 = Q.zMult(sSel, null).zDotProduct(sSel);
			double vS2 = 2 * Q.zMult(sSel, null).zDotProduct(dsSel);
			double vS3 = Q.zMult(dsSel, null).zDotProduct(dsSel);
			
//			while (Math.sqrt(
//					(vX1 + stepSize * vX2 + stepSize * stepSize * vX3)
//					* (vS1 + stepSize * vS2 + stepSize * stepSize * vS3)
//					) < ssCond) {
			DoubleMatrix1D newX = xSel.copy().assign(dxSel.copy().assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
			DoubleMatrix1D newS = sSel.copy().assign(dsSel.copy().assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
			while (Q.zMult(newX, null).zDotProduct(newX) * Q.zMult(newS, null).zDotProduct(newS)
					/ newX.zDotProduct(newS)
					< ssCond) {
				log.trace("Decrementing step size.");
				stepSize -= stepSizeDecrement;
				ssCond = getStepSizeCondition(stepSize, beta, gamma, im.mu);
				if (stepSize <= 0)
					throw new IllegalStateException("Stuck.");
			}
		}
		
		return stepSize;
	}
	
	private double getStepSizeCondition(double stepSize, double beta, double gamma, double mu) {
		return beta * (1 - stepSize * (1 - gamma)) * mu;
	}
	
	private DoubleMatrix2D getArrowheadMatrix(DoubleMatrix1D v) {
		int n = (int) v.size();
		double v0 = v.getQuick(0);
		DoubleMatrix2D ahm = new SparseDoubleMatrix2D(n, n);
		if (n > 1) {
			ahm.viewColumn(0).assign(v);
			ahm.viewRow(0).assign(v);
			for (int i = 1; i < n; i++) {
				ahm.setQuick(i, i, v0);
			}
		}
		else {
			ahm.setQuick(0, 0, v0);
		}
		
		return ahm;
	}
	
	private void logResults(ConicProgram program
			, HIPMProgramMatrices pm, HIPMVars vars, HIPMResiduals res
			, HIPMIntermediates im, HIPMSearchDirection sd, double gamma
			) {
		DoubleMatrix2D A = program.getA();
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D w = program.getW();
		DoubleMatrix1D s = program.getS();
		DoubleMatrix1D c = program.getC();
		
		log.trace("Gamma: {}", gamma);
		
		/* Computes how closely the system was solved */
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		log.trace("First equations: {}", alg.norm2(program.getA().zMult(sd.dx, b.copy().assign(DoubleFunctions.mult(sd.dTau)), 1.0, -1.0, false)
				.assign(res.r1, DoubleFunctions.minus)));
		log.trace("Second equations: {}", alg.norm2(program.getA().zMult(sd.dx, b.copy().assign(DoubleFunctions.mult(sd.dTau)), 1.0, -1.0, false)
				.assign(res.r1, DoubleFunctions.minus)));
		log.trace("Third equations: {}", c.zDotProduct(sd.dx) - b.zDotProduct(sd.dw) + sd.dKappa - res.r3);
		log.trace("Fourth equations: {}", alg.norm2(im.XBar.zMult(pm.T.zMult(im.invThetaInvW.zMult(sd.ds, null), null), null)
				.assign(im.SBar.zMult(pm.T.zMult(im.ThetaW.zMult(sd.dx, null), null), null), DoubleFunctions.plus)
				.assign(res.r4, DoubleFunctions.minus)));
		log.trace("Fifth equations: {}", vars.tau*sd.dKappa + vars.kappa*sd.dTau - res.r5);
		
		/* Computes properties of search direction */
		log.trace("Norm primal: {}", alg.norm2(A.zMult(x.copy().assign(DoubleFunctions.mult(1-gamma)).assign(sd.dx, DoubleFunctions.plus), null)
				.assign(b.copy().assign(DoubleFunctions.mult((1-gamma)*vars.tau+sd.dTau)), DoubleFunctions.minus)));
		log.trace("Norm dual: {}", alg.norm2(A.zMult(w.copy().assign(DoubleFunctions.mult(1-gamma)).assign(sd.dw, DoubleFunctions.plus), null, 1.0, 0.0, true)
				.assign(s.copy().assign(DoubleFunctions.mult(1-gamma)).assign(sd.ds, DoubleFunctions.plus), DoubleFunctions.plus)
				.assign(c.copy().assign(DoubleFunctions.mult((1-gamma)*vars.tau+sd.dTau)), DoubleFunctions.minus)));
		log.trace("Gap condition: {}", c.zDotProduct(x.copy().assign(DoubleFunctions.mult(1-gamma)).assign(sd.dx, DoubleFunctions.plus))
				- b.zDotProduct(w.copy().assign(DoubleFunctions.mult(1-gamma)).assign(sd.dw, DoubleFunctions.plus))
				+ ((1-gamma) * vars.kappa + sd.dKappa));
		
		/* Verifies consequences of properties */
		log.trace("First consequence: {}", x.copy().assign(DoubleFunctions.mult(1-gamma)).assign(sd.dx, DoubleFunctions.plus)
				.zDotProduct(s.copy().assign(DoubleFunctions.mult(1-gamma)).assign(sd.ds, DoubleFunctions.plus))
				+ ((1-gamma) * vars.kappa + sd.dKappa) * ((1-gamma)*vars.tau+sd.dTau));
		log.trace("Second consequence: {}", Math.pow(1-gamma, 2) * (x.zDotProduct(s) + vars.tau*vars.kappa)
				+ (1-gamma) * (x.zDotProduct(sd.ds)
						+ sd.dx.zDotProduct(s)
						+ vars.tau*sd.dKappa + sd.dTau*vars.kappa)
				+ sd.dx.zDotProduct(sd.ds) + sd.dTau * sd.dKappa);
		
		/* Computes another property of the search direction */
		log.trace("Other condition: {}", x.zDotProduct(sd.ds) + s.zDotProduct(sd.dx) + vars.tau*sd.dKappa + sd.dTau*vars.kappa
				- (gamma - 1) * im.mu * pm.k);
		
		log.trace("{}", im.XBar.zMult(im.SBar, null).zMult(pm.e, null).zDotProduct(pm.e) + vars.tau*vars.kappa);
		log.trace("{}", im.mu * pm.k);
		
		/* Verifies orthogonality of search direction components */
		log.trace("Step dot: {}", sd.dx.zDotProduct(sd.ds) + sd.dTau * sd.dKappa);
	}

	private class HIPMProgramMatrices {
		private int k;
		private SparseDoubleMatrix2D T;
		private SparseDoubleMatrix2D invT;
		private SparseDoubleMatrix2D Q;
		private SparseDoubleMatrix2D invQ;
		private DoubleMatrix1D e;
	}
	
	private class HIPMVars {
		private double tau;
		private double kappa;
	}
	
	private class HIPMIntermediates {
		private double mu;
		private SparseDoubleMatrix2D XBar;
		private SparseDoubleMatrix2D invXBar;
		private SparseDoubleMatrix2D SBar;
		private SparseDoubleMatrix2D ThetaW;
		private SparseDoubleMatrix2D invThetaInvW;
		private SparseCCDoubleMatrix2D AInvThetaSqInvWSq;
		private SparseDoubleMatrix2D ThetaSqWSq;
		private SparseDoubleMatrix2D invThetaSqInvWSq;
		private SparseDoubleCholeskyDecomposition M;
		private DoubleMatrix1D g1;
		private DoubleMatrix1D g2;
	}
	
	private class HIPMResiduals {
		private DoubleMatrix1D r1;
		private DoubleMatrix1D r2;
		private double r3;
		private DoubleMatrix1D r4;
		private double r5;
	}
	
	private class HIPMSearchDirection {
		private DoubleMatrix1D dx;
		private DoubleMatrix1D dw;
		private DoubleMatrix1D ds;
		private double dTau;
		private double dKappa;
	}
}
