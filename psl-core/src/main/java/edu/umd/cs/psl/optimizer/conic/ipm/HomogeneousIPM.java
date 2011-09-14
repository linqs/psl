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
import cern.colt.matrix.tdouble.algo.SparseDoubleAlgebra;
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
	public static final double SIG_THRESHOLD_DEFAULT = 10e-6;
	
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
	
	private DoubleMatrix1D baseResP;
	private DoubleMatrix1D baseResD;
	private double baseResG;
	
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
		
		log.debug("Completed optimization.");
		
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
		
		/* Initializes special variables for the homogeneous model */
		HIPMVars vars = new HIPMVars();
		vars.tau = 1;
		vars.kappa = x.zDotProduct(s) / pm.k;
		vars.d = x.copy().assign(DoubleFunctions.mult(Math.sqrt(2)));
		vars.detD = x.copy();
		vars.v = x.copy();
		
		/* Processes NNOCs */
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.index(cone.getVariable());
			vars.d.setQuick(index, 1.0);
			vars.detD.setQuick(index, 1.0);
			vars.v.setQuick(index, 1.0);
		}
		
		baseResP = A.zMult(x, b.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, false);
		baseResD = A.zMult(w, c.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, true);
		baseResD.assign(s, DoubleFunctions.plus);
		baseResG = b.zDotProduct(w) - c.zDotProduct(x) - vars.kappa;
		
		/* Computes values for stopping criteria */
		double muZero = (vars.v.zDotProduct(vars.v) + vars.tau * vars.kappa) / (pm.k+1);
		double primalInfRedDenom = Math.max(1.0, alg.norm2(
				A.zMult(x, b.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, false)));
		double dualInfRedDenom = Math.max(1.0, alg.norm2(
				A.zMult(w, c.copy().assign(DoubleFunctions.mult(vars.tau)), 1.0, -1.0, true)
				.assign(s, DoubleFunctions.plus)));
		double gapRedDenom = Math.max(1.0, Math.abs(b.zDotProduct(w) - c.zDotProduct(x) - vars.kappa));
		
		stepNum = 0;
		do {
			step(program, pm, vars);
			
			mu		= (vars.v.zDotProduct(vars.v) + vars.tau * vars.kappa) / (pm.k+1);
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
			
			log.trace("Itr: {} -- Comp: {} -- P. Inf: {} -- D. Inf: {} -- Sig: {}", new Object[] {++stepNum, mu, primalInfeasibility, dualInfeasibility, sig});
			
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
		/* Prepares for step */
		DoubleMatrix1D		x	= program.getX();
		DoubleMatrix1D		w	= program.getW();
		DoubleMatrix1D		s	= program.getS();
		DenseDoubleAlgebra	alg	= new DenseDoubleAlgebra();
		HIPMIntermediates	im	= getIntermediates(program, pm, vars);
		
		/* Computes affine scaling (Newton) direction */
		HIPMResiduals		res			= getResiduals(program, pm, vars, im, 0.0, null);
		HIPMSearchDirection	sd			= getSearchDirection(program, pm, vars, res, im);
		HIPMSearchDirection	descaledSD	= descaleSearchDirection(sd, im);
		
		/* Uses affine scaling direction to compute gamma */
		double	alphaMax	= getMaxStepSize(program, vars, descaledSD);
		double	delta		= 0.5;
		double	gamma		= Math.min(delta, Math.pow(1-alphaMax, 2)) * (1-alphaMax);
		
		/* Computes corrected direction */
		res			= getResiduals(program, pm, vars, im, gamma, sd);
		sd			= getSearchDirection(program, pm, vars, res, im);
		descaledSD	= descaleSearchDirection(sd, im);
		
		/* Gets step size */
				alphaMax	= getMaxStepSize(program, vars, descaledSD);
		double	stepSize	= getStepSize(program, vars, im, sd, alphaMax, 10e-8, gamma);
		
		/* Updates variables */
		x.assign(descaledSD.dx.copy().assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
		w.assign(descaledSD.dw.copy().assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
		s.assign(descaledSD.ds.copy().assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
		vars.tau += descaledSD.dTau * stepSize;
		vars.kappa += descaledSD.dKappa * stepSize;
		
		baseResP.assign(DoubleFunctions.mult(1 - stepSize * (1 - gamma)));
		baseResD.assign(DoubleFunctions.mult(1 - stepSize * (1 - gamma)));
		baseResG *= (1 - stepSize * (1 - gamma));
		
		/* Processes NNOCs */
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.index(cone.getVariable());
			vars.v.setQuick(index, Math.sqrt(x.getQuick(index) * s.getQuick(index)));
		}
		
		/* Processes SOCs */
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
			DoubleMatrix1D dSel		= vars.d.viewSelection(selection);
			DoubleMatrix1D detDSel	= vars.detD.viewSelection(selection);
			DoubleMatrix1D vSel		= vars.v.viewSelection(selection);
			
			/* Creates a Q matrix for the variables */
			DoubleMatrix2D Q = new SparseDoubleMatrix2D(nCone, nCone);
			Q.setQuick(0, 0, 1.0);
			for (int i = 1; i < nCone; i++)
				Q.setQuick(i, i, -1.0);
			
			/* Creates temporary vectors */
			DoubleMatrix1D xBarPlus = new DenseDoubleMatrix1D(nCone);
			xBarPlus.assign(vSel).assign(sd.dx.viewSelection(selection).assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
			DoubleMatrix1D sBarPlus = new DenseDoubleMatrix1D(nCone);
			sBarPlus.assign(vSel).assign(sd.ds.viewSelection(selection).assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
			
			/* Computes intermediate values */
			double detXBarPlus = (Math.pow(xBarPlus.getQuick(0), 2) - xBarPlus.zDotProduct(xBarPlus, 1, nCone-1)) / 2;
			double detSBarPlus = (Math.pow(sBarPlus.getQuick(0), 2) - sBarPlus.zDotProduct(sBarPlus, 1, nCone-1)) / 2;
			double detVPlus = Math.sqrt(detXBarPlus * detSBarPlus);
			double traceVPlus = Math.sqrt(xBarPlus.zDotProduct(sBarPlus) + 2 * detVPlus);
			
			DoubleMatrix1D chi = Q.zMult(sBarPlus, null);
			chi.assign(DoubleFunctions.mult(detVPlus/detSBarPlus));
			chi.assign(xBarPlus, DoubleFunctions.plus);
			chi.assign(DoubleFunctions.div(traceVPlus));
			double detChi = detVPlus / detSBarPlus;
			
			DoubleMatrix1D sqrtD = dSel.copy();
			sqrtD.setQuick(0, sqrtD.getQuick(0) + Math.sqrt(2 * detDSel.getQuick(0)));
			sqrtD.assign(DoubleFunctions.div(Math.sqrt(Math.sqrt(2) * dSel.getQuick(0) + 2 * Math.sqrt(detDSel.getQuick(0)))));
			double detSqrtD = Q.zMult(sqrtD, null).zDotProduct(sqrtD) / 2;
			DoubleMatrix2D POfSqrtD = alg.multOuter(sqrtD, sqrtD, null);
			POfSqrtD.assign(Q.copy().assign(DoubleFunctions.mult(detSqrtD)), DoubleFunctions.minus);
			
			DoubleMatrix1D dPlus = POfSqrtD.zMult(chi, null);
			double detDPlus = detDSel.getQuick(0) * detChi;
			double traceDPlus = Math.sqrt(2.0) * dPlus.getQuick(0);
			
			DoubleMatrix1D psi = Q.zMult(sBarPlus, null);
			psi.assign(DoubleFunctions.mult(-1 * detVPlus/detSBarPlus));
			psi.assign(xBarPlus, DoubleFunctions.plus);
			
			double alpha = dSel.zDotProduct(psi) / (traceDPlus + 2 * Math.sqrt(detDPlus));
			
			DoubleMatrix1D phi = chi.copy().assign(DoubleFunctions.mult(-1 * alpha));
			phi.assign(psi, DoubleFunctions.plus);
			phi.assign(DoubleFunctions.div(2 * Math.sqrt(detChi)));
			
			double gammaForUpdate = (alpha + Math.sqrt(2.0) * phi.getQuick(0))
					/ (Math.sqrt(2.0) * dSel.getQuick(0) + 2 * Math.sqrt(detDSel.getQuick(0)));
			
			/* Updates v */
			vSel.setQuick(0, traceVPlus / Math.sqrt(2.0));
			for (int i = 1; i < nCone; i++)
				vSel.setQuick(i, phi.getQuick(i) + gammaForUpdate * dSel.get(i));
			
			/* Updates d and detD */
			dSel.assign(dPlus);
			detDSel.setQuick(0, detDPlus);
		}
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
		
		im.mu = (vars.v.zDotProduct(vars.v) + vars.tau * vars.kappa) / (pm.k+1);
		
		im.ThetaW			= new SparseDoubleMatrix2D(n, n);
		im.invThetaInvW		= new SparseDoubleMatrix2D(n, n);
		im.invThetaSqInvWSq	= new SparseDoubleMatrix2D(n, n);
		im.XBar				= new SparseDoubleMatrix2D(n, n);
		im.invXBar			= new SparseDoubleMatrix2D(n, n);
		im.SBar				= new SparseDoubleMatrix2D(n, n);
		
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.index(cone.getVariable());
			double thetaSq = s.getQuick(index) / x.getQuick(index);
			double theta = Math.sqrt(thetaSq);
			double invTheta = 1 / theta;
			im.ThetaW.setQuick(index, index, theta);
			im.invThetaInvW.setQuick(index, index, invTheta);
			im.invThetaSqInvWSq.setQuick(index, index, 1 / thetaSq);
			im.XBar.setQuick(index, index, theta * x.getQuick(index));
			im.invXBar.setQuick(index, index, invTheta * 1 / x.getQuick(index));
			im.SBar.setQuick(index, index, invTheta * s.getQuick(index));
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
			DoubleMatrix1D dSel		= vars.d.viewSelection(selection);
			double			detDSel	= vars.detD.viewSelection(selection).getQuick(0);
			DoubleMatrix1D vSel		= vars.v.viewSelection(selection);
			
			/* Creates a Q matrix for the variables */
			DoubleMatrix2D Q = new SparseDoubleMatrix2D(nCone, nCone);
			Q.setQuick(0, 0, 1.0);
			for (int i = 1; i < nCone; i++)
				Q.setQuick(i, i, -1.0);
			
			/*
			 * Computes selections of matrices
			 */
			
			/* Gets the selections */
			DoubleMatrix2D ThetaWSel			= im.ThetaW.viewSelection(selection, selection);
			DoubleMatrix2D invThetaInvWSel		= im.invThetaInvW.viewSelection(selection, selection);
			DoubleMatrix2D XBarSel				= im.XBar.viewSelection(selection, selection);
			DoubleMatrix2D invXBarSel			= im.invXBar.viewSelection(selection, selection);
			DoubleMatrix2D SBarSel				= im.SBar.viewSelection(selection, selection);
			DoubleMatrix2D invThetaSqInvWSqSel	= im.invThetaSqInvWSq.viewSelection(selection, selection);
			
			/* Computes invThetaSqInvWSq selection */
			alg.multOuter(dSel, dSel, invThetaSqInvWSqSel);
			invThetaSqInvWSqSel.assign(Q.copy().assign(DoubleFunctions.mult(detDSel)), DoubleFunctions.minus);
			ThetaWSel.assign(getSOCFunction(getSOCSqrt(getSOCInverse(dSel, detDSel), 1 / detDSel), 1 / Math.sqrt(detDSel)));
			invThetaInvWSel.assign(getSOCFunction(getSOCSqrt(dSel, detDSel), Math.sqrt(detDSel)));
			
			/* Computes selections of XBar, SBar, and invXBar */
			DoubleMatrix1D xbar = vSel.copy();
			XBarSel.assign(getArrowheadMatrix(xbar));
			DoubleMatrix1D sbar = vSel.copy();
			SBarSel.assign(getArrowheadMatrix(sbar));
			alg.multOuter(xbar, xbar, invXBarSel);
			invXBarSel.assign(DoubleFunctions.div(xbar.getQuick(0)));
			invXBarSel.viewColumn(0).assign(xbar).assign(DoubleFunctions.mult(-1));
			invXBarSel.viewRow(0).assign(xbar).assign(DoubleFunctions.mult(-1));
			invXBarSel.setQuick(0, 0, xbar.getQuick(0));
			double normSq = Math.pow(alg.norm2(xbar.viewPart(1, nCone-1)), 2);
			double coeff = xbar.getQuick(0) - normSq / xbar.getQuick(0);
			for (int i = 1; i < nCone; i++)
				invXBarSel.setQuick(i, i, invXBarSel.getQuick(i, i) + coeff);
			invXBarSel.assign(DoubleFunctions.div(Math.pow(xbar.getQuick(0), 2) - normSq));
			if (alg.inverse(XBarSel).assign(invXBarSel, DoubleFunctions.minus).assign(DoubleFunctions.abs).getMaxLocation()[0] > 10e-8)
				coeff += 0;
		}
		
		/* Computes more intermediate matrices */
		im.AInvThetaSqInvWSq = new SparseCCDoubleMatrix2D(A.rows(), n);
		A.getColumnCompressed(false).zMult(im.invThetaSqInvWSq.getColumnCompressed(false), im.AInvThetaSqInvWSq, 1.0, 0.0, false, false);
		
		/* Computes M and finds its Cholesky factorization */
		SparseCCDoubleMatrix2D APhi = new SparseCCDoubleMatrix2D(A.rows(), A.columns());
		A.getColumnCompressed(false).zMult(im.invThetaInvW.getColumnCompressed(false), APhi);
		SparseCCDoubleMatrix2D M = new SparseCCDoubleMatrix2D(A.rows(), A.rows());
		APhi.zMult(APhi, M, 1.0, 0.0, false, true);
		log.trace("Starting decomposition.");
		try {
			im.M = new SparseDoubleCholeskyDecomposition(M, 1);
		}
		catch (IllegalArgumentException e) {
			//System.out.println(alg.toVerboseString(M.getDense()));
			throw e;
		}
		log.trace("Finished decomposition.");
		
		/* Computes intermediate vectors */
		
		/* g2 */
		im.g2 = im.AInvThetaSqInvWSq.zMult(c, b.copy(), 1.0, 1.0, false);
		im.M.solve(im.g2);
		
		/* g1 */
		im.g1 = im.invThetaInvW.getColumnCompressed(false).zMult(A.getColumnCompressed(false).zMult(im.g2, null, 1.0, 0.0, true), null);
		im.g1.assign(im.invThetaInvW.zMult(c, null), DoubleFunctions.minus);
		
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
		
		res.r1 = baseResP.copy().assign(DoubleFunctions.mult(gamma-1));
		res.r2 = baseResD.copy().assign(DoubleFunctions.mult(gamma-1));
		res.r3 = baseResG * (gamma-1);
		res.r4 = pm.e.copy().assign(DoubleFunctions.mult(gamma*im.mu)).assign(im.XBar.zMult(im.SBar.zMult(pm.e, null), null), DoubleFunctions.minus);
		res.r5 = gamma * im.mu - vars.tau * vars.kappa;
		
		/* Corrects residuals with second-order estimate */
		if (sd != null) {
			DoubleMatrix1D dxn = pm.T.zMult(sd.dx, null);
			DoubleMatrix1D dsn = pm.T.zMult(sd.ds, null);
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
				DoubleMatrix2D DxnSel	= Dxn.viewSelection(selection, selection);
				DoubleMatrix2D DsnSel	= Dsn.viewSelection(selection, selection);
				
				/* Computes the arrowhead matrices */
				DxnSel.assign(getArrowheadMatrix(dxnSel));
				DsnSel.assign(getArrowheadMatrix(dsnSel));
			}
			
			res.r4.assign(Dxn.zMult(Dsn.zMult(pm.e, null), null), DoubleFunctions.minus);
			res.r5 -= sd.dTau * sd.dKappa;
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
		
		DoubleMatrix1D TInvVR4 = pm.T.getColumnCompressed(false)
				.zMult(im.invXBar.getColumnCompressed(false).zMult(res.r4, null), null);
		
		/* h2 */
		DoubleMatrix1D h2 = res.r1.copy()
				.assign(im.AInvThetaSqInvWSq.zMult(res.r2.copy(), null), DoubleFunctions.plus)
				.assign(A.getColumnCompressed(false)
						.zMult(im.invThetaInvW.getColumnCompressed(false).zMult(TInvVR4, null), null),
						DoubleFunctions.minus);
		im.M.solve(h2);
		
		/* h1 */
		DoubleMatrix1D h1 = TInvVR4.copy();
		h1.assign(im.invThetaInvW.getColumnCompressed(false)
				.zMult(A.getColumnCompressed(false).zMult(h2, null, 1.0, 0.0, true), null),
				DoubleFunctions.plus);
		h1.assign(im.invThetaInvW.zMult(res.r2, null), DoubleFunctions.minus);
	
		/* Computes search direction */
		sd.dTau = (res.r3 + c.zDotProduct(im.invThetaInvW.zMult(h1, null)) - b.zDotProduct(h2) + res.r5 / vars.tau)
				/ ((vars.kappa/vars.tau) - c.zDotProduct(im.invThetaInvW.zMult(im.g1, null)) + b.zDotProduct(im.g2));
		sd.dx = im.g1.copy().assign(DoubleFunctions.mult(sd.dTau)).assign(h1, DoubleFunctions.plus);
		sd.dw = im.g2.copy().assign(DoubleFunctions.mult(sd.dTau)).assign(h2, DoubleFunctions.plus);
		sd.dKappa = (res.r5 - vars.kappa*sd.dTau) / vars.tau;
		sd.ds = TInvVR4.copy().assign(sd.dx, DoubleFunctions.minus);
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
			alphaMax = Math.min(-0.95 * vars.tau / sd.dTau, alphaMax);
		
		/* Checks distance to min. kappa */
		if (sd.dKappa < 0)
			alphaMax = Math.min(-0.95 * vars.kappa / sd.dKappa, alphaMax);
		
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
			log.trace("Decrementing step size because of tau and/or kappa.");
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
			
			while (Math.sqrt(
					(vX1 + stepSize * vX2 + stepSize * stepSize * vX3)
					* (vS1 + stepSize * vS2 + stepSize * stepSize * vS3)
					) < ssCond) {
//			while ((x.getQuick(index) + sd.dx.getQuick(index) * stepSize)
//					* (s.getQuick(index) + sd.ds.getQuick(index) * stepSize)
//					< ssCond) {
				log.trace("Decrementing step size because of NNOC.");
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
			
			while (Math.sqrt(
					(vX1 + stepSize * vX2 + stepSize * stepSize * vX3)
					* (vS1 + stepSize * vS2 + stepSize * stepSize * vS3)
					) < ssCond) {
//			DoubleMatrix1D newX = xSel.copy().assign(dxSel.copy().assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
//			DoubleMatrix1D newS = sSel.copy().assign(dsSel.copy().assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
//			while (Q.zMult(newX, null).zDotProduct(newX) * Q.zMult(newS, null).zDotProduct(newS)
//					/ newX.zDotProduct(newS)
//					< ssCond) {
				log.trace("Decrementing step size because of SOC.");
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
	
	private HIPMSearchDirection descaleSearchDirection(HIPMSearchDirection sd, HIPMIntermediates im) {
		HIPMSearchDirection newSD = new HIPMSearchDirection();
		newSD.dx = im.invThetaInvW.zMult(sd.dx, null);
		newSD.dw = sd.dw.copy();
		newSD.ds = im.ThetaW.zMult(sd.ds, null);
		newSD.dTau = sd.dTau;
		newSD.dKappa = sd.dKappa;
		return newSD;
	}
	
	private DoubleMatrix2D getSOCFunction(DoubleMatrix1D x, double detX) {
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		int n = (int) x.size();
		
		/* Creates a Q matrix for the variables */
		DoubleMatrix2D Q = new SparseDoubleMatrix2D(n, n);
		Q.setQuick(0, 0, 1.0);
		for (int i = 1; i < n; i++)
			Q.setQuick(i, i, -1.0);
		
		DoubleMatrix2D P = alg.multOuter(x, x, null);
		P.assign(Q.copy().assign(DoubleFunctions.mult(detX)), DoubleFunctions.minus);
		return P;
	}
	
	private DoubleMatrix1D getSOCInverse(DoubleMatrix1D x, double detX) {
		int n = (int) x.size();
		/* Creates a Q matrix for the variables */
		DoubleMatrix2D Q = new SparseDoubleMatrix2D(n, n);
		Q.setQuick(0, 0, 1.0);
		for (int i = 1; i < n; i++)
			Q.setQuick(i, i, -1.0);
		
		return Q.zMult(x, null).assign(DoubleFunctions.div(detX));
	}
	
	private DoubleMatrix1D getSOCSqrt(DoubleMatrix1D x, double detX) {
		DoubleMatrix1D sqrtD = x.copy();
		sqrtD.setQuick(0, sqrtD.getQuick(0) + Math.sqrt(2 * detX));
		sqrtD.assign(DoubleFunctions.div(Math.sqrt(Math.sqrt(2) * x.getQuick(0) + 2 * Math.sqrt(detX))));
		return sqrtD;
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
		private DoubleMatrix1D d;
		private DoubleMatrix1D detD;
		private DoubleMatrix1D v;
	}
	
	private class HIPMIntermediates {
		private double mu;
		private SparseDoubleMatrix2D XBar;
		private SparseDoubleMatrix2D invXBar;
		private SparseDoubleMatrix2D SBar;
		private SparseDoubleMatrix2D ThetaW;
		private SparseDoubleMatrix2D invThetaInvW;
		private SparseCCDoubleMatrix2D AInvThetaSqInvWSq;
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
