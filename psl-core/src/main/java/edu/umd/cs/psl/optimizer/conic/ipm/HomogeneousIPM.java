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

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.ipm.solver.CholeskyFactory;
import edu.umd.cs.psl.optimizer.conic.ipm.solver.NormalSystemSolver;
import edu.umd.cs.psl.optimizer.conic.ipm.solver.NormalSystemSolverFactory;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;
import edu.umd.cs.psl.optimizer.conic.util.Dualizer;

/**
 * Primal-dual interior-point method using the self-dual homogeneous model.
 * 
 * Supports conic programs with non-negative orthant cones and second-order cones.
 * 
 * This solver follows the algorithm presented in
 * E. D. Andersen, C. Roos and T. Terlaky. "On implementing a primal-dual
 * interior-point method for conic quadratic optimization."
 * <i>Math. Programming</i> 95(2), February 2003.
 * 
 * It also uses the product form for numeric stability presented in J. F. Sturm.
 * "Avoiding numerical cancellation in the interior point method for solving
 * semidefinite programs. <i>Math. Programming</i> 95(2), February 2003;
 * and J. F. Sturm. "Implementation of interior point methods for mixed semidefinite
 * and second order cone optimization problems."
 * <i>Optimization Methods and Software</i> 17(6), September 2002. 
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
	 * Key for double property. The IPM will consider the problem primal, dual, or
	 * gap feasible if the primal, dual, or gap infeasibility is less than its value,
	 * respectively.
	 */
	public static final String INFEASIBILITY_THRESHOLD_KEY = CONFIG_PREFIX + ".infeasibilitythreshold";
	/** Default value for INFEASIBILITY_THRESHOLD_KEY property. */
	public static final double INFEASIBILITY_THRESHOLD_DEFAULT = 10e-8;
	
	/**
	 * Key for double property. The IPM will iterate until the duality gap is
	 * less than its value.
	 */
	public static final String GAP_THRESHOLD_KEY = CONFIG_PREFIX + ".gapthreshold";
	/** Default value for GAP_THRESHOLD_KEY property. */
	public static final double GAP_THRESHOLD_DEFAULT = 10e-6;
	
	/**
	 * Key for double property. The IPM will multiply its value by another value
	 * and consider tau small if tau is less than that product.
	 */
	public static final String TAU_THRESHOLD_KEY = CONFIG_PREFIX + ".tauthreshold";
	/** Default value for TAU_THRESHOLD_KEY property. */
	public static final double TAU_THRESHOLD_DEFAULT = 10e-8;
	
	/**
	 * Key for double property. The IPM will consider mu small if mu is less than
	 * its value times the initial mu.
	 */
	public static final String MU_THRESHOLD_KEY = CONFIG_PREFIX + ".muthreshold";
	/** Default value for MU_THRESHOLD_KEY property. */
	public static final double MU_THRESHOLD_DEFAULT = 10e-8;
	
	/**
	 * Key for double property in (0,1). The IPM will stay in a neighborhood of
	 * the central path, the size of which is defined by its value. Larger
	 * values correspond to smaller neighborhoods.
	 */
	public static final String BETA_KEY = CONFIG_PREFIX + ".beta";
	/** Default value for BETA_KEY property. */
	public static final double BETA_DEFAULT = 10e-8;
	
	/**
	 * Key for double property in [0,1]. The IPM will use its value to determine
	 * how aggressively to minimize the objective (versus to follow the central path).
	 * Lower values correspond to more aggressive strategies.
	 */
	public static final String DELTA_KEY = CONFIG_PREFIX + ".delta";
	/** Default value for DELTA_KEY property. */
	public static final double DELTA_DEFAULT = 0.5;
	
	/**
	 * Key for {@link Factory} or String property.
	 * 
	 * Should be set to a {@link NormalSystemSolverFactory} or the fully qualified
	 * name of one. Will be used to instantiate a {@link NormalSystemSolver}.
	 */
	public static final String NORMAL_SYS_SOLVER_KEY = CONFIG_PREFIX + ".normalsolver";
	/**
	 * Default value for NORMAL_SYS_SOLVER_KEY.
	 * 
	 * Value is instance of {@link CholeskyFactory}. 
	 */
	public static final NormalSystemSolverFactory NORMAL_SYS_SOLVER_DEFAULT = new CholeskyFactory();
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
		supportedCones.add(ConeType.SecondOrderCone);
	}

	private ConicProgram currentProgram;
	
	private boolean dualized;
	private Dualizer dualizer;
	
	private final boolean tryDualize;
	private final double infeasibilityThreshold;
	private final double gapThreshold;
	private final double tauThreshold;
	private final double muThreshold;
	private final double beta;
	private final double delta;
	private final NormalSystemSolver solver;
	
	private int stepNum;
	
	/* Data structures for computing objective, infeasibility, etc. */
	private DoubleMatrix1D baseResP;
	private DoubleMatrix1D baseResD;
	private double baseResG;
	
	/* Data structures related to structure of the conic program */
	private int k;
	private SparseDoubleMatrix2D T;
	private DoubleMatrix1D e;
	
	/* Additional numeric variables for the homogeneous model */
	private double tau;
	private double kappa;
	private DoubleMatrix1D d;
	private DoubleMatrix1D detD;
	private DoubleMatrix1D v;
	
	/* Intermediates for computing residuals */
	private DoubleMatrix1D dxn;
	private DoubleMatrix1D dsn;
	private DoubleMatrix2D Dxn;
	private DoubleMatrix2D Dsn;
	
	/* Residuals */
	private DoubleMatrix1D r1;
	private DoubleMatrix1D r2;
	private double r3;
	private DoubleMatrix1D r4;
	private double r5;
	
	/* Intermediates for computing search directions*/
	private double mu;
	private SparseCCDoubleMatrix2D XBar;
	private SparseCCDoubleMatrix2D invXBar;
	private SparseCCDoubleMatrix2D ThetaW;
	private SparseCCDoubleMatrix2D invThetaInvW;
	private SparseCCDoubleMatrix2D invThetaSqInvWSq;
	private SparseCCDoubleMatrix2D AInvThetaSqInvWSq;
	private DoubleMatrix1D g1;
	private DoubleMatrix1D g2;
	
	/* Search direction */
	private DoubleMatrix1D dx;
	private DoubleMatrix1D dw;
	private DoubleMatrix1D ds;
	private double dTau;
	private double dKappa;
	
	/* Descaled search direction */
	private DoubleMatrix1D dxDescaled;
	private DoubleMatrix1D dwDescaled;
	private DoubleMatrix1D dsDescaled;
	private double dTauDescaled;
	private double dKappaDescaled;
	
	/* Scratch vectors */
	private DoubleMatrix1D scratchN1;
	private DoubleMatrix1D scratchN2;
	private DoubleMatrix1D scratchN3;
	private DoubleMatrix1D scratchM1;
	private DoubleMatrix1D scratchM2;
	
	public HomogeneousIPM(ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		tryDualize = config.getBoolean(DUALIZE_KEY, DUALIZE_DEFAULT);
		infeasibilityThreshold = config.getDouble(INFEASIBILITY_THRESHOLD_KEY, INFEASIBILITY_THRESHOLD_DEFAULT);
		gapThreshold = config.getDouble(GAP_THRESHOLD_KEY, GAP_THRESHOLD_DEFAULT);
		tauThreshold = config.getDouble(TAU_THRESHOLD_KEY, TAU_THRESHOLD_DEFAULT);
		muThreshold = config.getDouble(MU_THRESHOLD_KEY, MU_THRESHOLD_DEFAULT);
		beta = config.getDouble(BETA_KEY, BETA_DEFAULT);
		NormalSystemSolverFactory solverFactory = (NormalSystemSolverFactory) config.getFactory(NORMAL_SYS_SOLVER_KEY, NORMAL_SYS_SOLVER_DEFAULT);
		solver = solverFactory.getNormalSystemSolver(config);
		
		if (beta <= 0 || beta >= 1)
			throw new IllegalArgumentException("Property " + BETA_KEY + " must be in (0,1).");
		delta = config.getDouble(DELTA_KEY, DELTA_DEFAULT);
		if (delta < 0 || delta > 1)
			throw new IllegalArgumentException("Property " + DELTA_KEY + " must be in [0,1].");
		
		currentProgram = null;
		dualized = false;
	}

	@Override
	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}
	
	@Override
	public void setConicProgram(ConicProgram p) {
		currentProgram = p;
		if (tryDualize && Dualizer.supportsConeTypes(currentProgram.getConeTypes())) {
			dualized = true;
			dualizer = new Dualizer(currentProgram);
		}
		else {
			dualized = false;
		}
	}

	@Override
	public void solve() {
		if (currentProgram == null)
			throw new IllegalStateException("No conic program has been set.");
		
		ConicProgram program;
		boolean checkedOutDualProgram = false;

		currentProgram.checkOutMatrices();
		
		if (dualized && Dualizer.supportsConeTypes(currentProgram.getConeTypes())) {
			log.debug("Dualizing conic program.");
			dualizer.checkOutProgram();
			program = dualizer.getDualProgram();
			program.checkOutMatrices();
			checkedOutDualProgram = true;
		}
		else {
			program = currentProgram;
		}
		
		if (!supportsConeTypes(program.getConeTypes())) {
			throw new IllegalStateException("Program contains at least one unsupported cone."
					+ " Supported cones are non-negative orthant cones and second-order cones.");
		}

		DoubleMatrix2D A = program.getA();
		
		log.debug("Starting optimization with {} variables and {} constraints.", A.columns(), A.rows());
		
		doSolve(program);
		
		if (checkedOutDualProgram) {
			program.checkInMatrices();
			dualizer.checkInProgram();
		}
		
		currentProgram.checkInMatrices();
		
		log.debug("Completed optimization.");
	}
	
	private void doSolve(ConicProgram program) {
		solver.setConicProgram(program);
		
		DoubleMatrix2D A = program.getA();
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D w = program.getW();
		DoubleMatrix1D s = program.getS();
		DoubleMatrix1D c = program.getC();
		
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		double cDotX, bDotW;
		double gapInfeasibility, mu, primalInfeasibility, dualInfeasibility, gap;
		boolean primalFeasible, dualFeasible, gapIsSmall, gapFeasible, tauIsSmall, tauIsVerySmall, muIsSmall; 
		boolean solved, programInfeasible, illPosed;
		
		/* Initializes program matrices that can be reused for entire procedure */
		initializeProgramMatrices(program);
		
		/* Initializes program variables */
		T.zMult(e, x);
		s.assign(x);
		w.assign(0.0);
		
		/* Initializes special variables for the homogeneous model */
		tau = 1;
		kappa = x.zDotProduct(s) / k;
		d = x.copy().assign(DoubleFunctions.mult(Math.sqrt(2)));
		detD = x.copy();
		v = x.copy();
		
		/* Performs additional variable setup for NNOCs */
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.getIndex(cone.getVariable());
			d.setQuick(index, 1.0);
			detD.setQuick(index, 1.0);
			v.setQuick(index, 1.0);
		}
		
		/* Initializes data structures to be reused in each step */
		int m = A.rows();
		int n = A.columns();
		
		/*
		 * Initializes vectors for search direction intermediates. Matrices
		 * will be initialized the first time getIntermediates() is called. 
		 */
		g1 = new DenseDoubleMatrix1D(n);
		g2 = new DenseDoubleMatrix1D(m);
		
		/* Initializes vectors and matrices for intermediates for residuals */
		dxn = new DenseDoubleMatrix1D((int) x.size());
		dsn = new DenseDoubleMatrix1D((int) s.size());
		Dxn = new SparseDoubleMatrix2D(n, n, n*4, 0.2, 0.5);
		Dsn = new SparseDoubleMatrix2D(n, n, n*4, 0.2, 0.5);
		
		/* Initializes vectors for residuals */
		r1  = new DenseDoubleMatrix1D(m);
		r2  = new DenseDoubleMatrix1D(n);
		r4  = new DenseDoubleMatrix1D(n);
		
		/* Initializes vectors for search direction */
		dx = new DenseDoubleMatrix1D(n);
		ds = new DenseDoubleMatrix1D(n);
		dw = new DenseDoubleMatrix1D(m);
		
		/* Initializes vectors for descaled search direction */
		dxDescaled = new DenseDoubleMatrix1D(n);
		dsDescaled = new DenseDoubleMatrix1D(n);
		dwDescaled = new DenseDoubleMatrix1D(m);
		
		/* Initializes scratch vectors */
		scratchN1 = new DenseDoubleMatrix1D(n);
		scratchN2 = new DenseDoubleMatrix1D(n);
		scratchN3 = new DenseDoubleMatrix1D(n);
		scratchM1 = new DenseDoubleMatrix1D(m);
		scratchM2 = new DenseDoubleMatrix1D(m);
		
		/* Computes values to measure objective, infeasibility, etc. */
		baseResP = A.zMult(x, b.copy().assign(DoubleFunctions.mult(tau)), 1.0, -1.0, false);
		baseResD = A.zMult(w, c.copy().assign(DoubleFunctions.mult(tau)), 1.0, -1.0, true);
		baseResD.assign(s, DoubleFunctions.plus);
		baseResG = b.zDotProduct(w) - c.zDotProduct(x) - kappa;
		
		/* Computes values for stopping criteria */
		double muZero = (v.zDotProduct(v) + tau * kappa) / (k+1);
		double primalInfDenom = Math.max(1.0, alg.norm2(
				A.zMult(x, b.copy().assign(DoubleFunctions.mult(tau)), 1.0, -1.0, false)));
		double dualInfDenom = Math.max(1.0, alg.norm2(
				A.zMult(w, c.copy().assign(DoubleFunctions.mult(tau)), 1.0, -1.0, true)
				.assign(s, DoubleFunctions.plus)));
		double gapInfDenom = Math.max(1.0, Math.abs(b.zDotProduct(w) - c.zDotProduct(x) - kappa));
		
		stepNum = 0;
		do {
			step(program);
			
			mu     = (v.zDotProduct(v) + tau * kappa) / (k+1);
			cDotX  = c.zDotProduct(x);
			bDotW  = b.zDotProduct(w);
			
			primalInfeasibility = alg.norm2(
					A.zMult(x, b.copy().assign(DoubleFunctions.mult(tau)), 1.0, -1.0, false)
					) / primalInfDenom;
			
			dualInfeasibility = alg.norm2(
					A.zMult(w, c.copy().assign(DoubleFunctions.mult(tau)), 1.0, -1.0, true)
					.assign(s, DoubleFunctions.plus)
					) / dualInfDenom;
			
			gapInfeasibility = Math.abs(bDotW - cDotX - kappa) / gapInfDenom;
			gap = Math.abs(cDotX / tau - bDotW / tau) / (1 + Math.abs(bDotW / tau));
			
			log.trace("Itr: {} -- Comp: {} -- P. Inf: {} -- D. Inf: {} -- Sig: {}", new Object[] {++stepNum, mu, primalInfeasibility, dualInfeasibility, gap});
			
			primalFeasible  = primalInfeasibility <= infeasibilityThreshold;
			dualFeasible    = dualInfeasibility <= infeasibilityThreshold;
			gapFeasible     = gapInfeasibility <= infeasibilityThreshold;
			gapIsSmall      = gap <= gapThreshold;
			tauIsSmall      = tau <= tauThreshold * Math.max(1.0, kappa);
			tauIsVerySmall  = tau <= tauThreshold * Math.min(1.0, kappa);
			muIsSmall       = mu <= muThreshold * muZero;
			
			solved             = primalFeasible && dualFeasible && gapIsSmall;
			programInfeasible  = primalFeasible && dualFeasible && gapFeasible && tauIsSmall;
			illPosed           = muIsSmall && tauIsVerySmall;
		} while (!solved && !programInfeasible && !illPosed);
		
		removeMatrixReferences();
		
		if (illPosed)
			throw new IllegalArgumentException("Optimization program is ill-posed.");
		else if (programInfeasible)
			throw new IllegalArgumentException("Optimization program is infeasible.");
		else {
			x.assign(DoubleFunctions.div(tau));
			w.assign(DoubleFunctions.div(tau));
			s.assign(DoubleFunctions.div(tau));
		}
	}

	private void step(ConicProgram program) {
		/* Prepares for step */
		DoubleMatrix1D      x   = program.getX();
		DoubleMatrix1D      w   = program.getW();
		DoubleMatrix1D      s   = program.getS();
		DenseDoubleAlgebra  alg = new DenseDoubleAlgebra();
		getIntermediates(program);
		
		/* Computes affine scaling (Newton) direction */
		getResiduals(program, 0.0, false);
		getSearchDirection(program);
		
		/* Uses affine scaling direction to compute gamma */
		double  alphaMax = getMaxStepSize(program);
		double  gamma    = Math.min(delta, Math.pow(1-alphaMax, 2)) * (1-alphaMax);
		
		/* Computes corrected direction */
		getResiduals(program, gamma, true);
		getSearchDirection(program);
		descaleSearchDirection();
		
		/* Gets step size */
		        alphaMax = getMaxStepSize(program);
		double  stepSize = getStepSize(program, alphaMax, beta, gamma);
		
		/* Updates variables */
		x.assign(dxDescaled, DoubleFunctions.plusMultSecond(stepSize));
		w.assign(dwDescaled, DoubleFunctions.plusMultSecond(stepSize));
		s.assign(dsDescaled, DoubleFunctions.plusMultSecond(stepSize));
		tau += dTauDescaled * stepSize;
		kappa += dKappaDescaled * stepSize;
		
		baseResP.assign(DoubleFunctions.mult(1 - stepSize * (1 - gamma)));
		baseResD.assign(DoubleFunctions.mult(1 - stepSize * (1 - gamma)));
		baseResG *= (1 - stepSize * (1 - gamma));
		
		/* Processes NNOCs */
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.getIndex(cone.getVariable());
			v.setQuick(index, Math.sqrt(x.getQuick(index) * s.getQuick(index)));
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
					selection[0] = program.getIndex(var);
				else
					selection[selectionIndex++] = program.getIndex(var);
			}
			
			/* Selects the variables */
			DoubleMatrix1D  dSel    = d.viewSelection(selection);
			DoubleMatrix1D  detDSel = detD.viewSelection(selection);
			DoubleMatrix1D  vSel    = v.viewSelection(selection);
			
			/* Creates a Q matrix for the variables */
			DoubleMatrix2D Q = new SparseDoubleMatrix2D(nCone, nCone);
			Q.setQuick(0, 0, 1.0);
			for (int i = 1; i < nCone; i++)
				Q.setQuick(i, i, -1.0);
			
			/* Creates temporary vectors */
			DoubleMatrix1D xBarPlus = new DenseDoubleMatrix1D(nCone);
			xBarPlus.assign(vSel).assign(dx.viewSelection(selection).assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
			DoubleMatrix1D sBarPlus = new DenseDoubleMatrix1D(nCone);
			sBarPlus.assign(vSel).assign(ds.viewSelection(selection).assign(DoubleFunctions.mult(stepSize)), DoubleFunctions.plus);
			
			/* Computes intermediate values */
			double detXBarPlus = (Math.pow(xBarPlus.getQuick(0), 2) - xBarPlus.zDotProduct(xBarPlus, 1, nCone-1)) / 2;
			double detSBarPlus = (Math.pow(sBarPlus.getQuick(0), 2) - sBarPlus.zDotProduct(sBarPlus, 1, nCone-1)) / 2;
			double detVPlus = Math.sqrt(detXBarPlus * detSBarPlus);
			double traceVPlus = Math.sqrt(xBarPlus.zDotProduct(sBarPlus) + 2 * detVPlus);
			if (traceVPlus == 0.0)
				throw new IllegalStateException(Double.toString((xBarPlus.zDotProduct(sBarPlus) + 2 * detVPlus)));
			
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
	
	private void initializeProgramMatrices(ConicProgram program) {
		SparseCCDoubleMatrix2D A = program.getA();
		int size = A.columns();
		
		k = program.getNumCones();
		e = new DenseDoubleMatrix1D(size);
		T = new SparseDoubleMatrix2D(size, size, size*4, 0.2, 0.5);
		
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int i = program.getIndex(cone.getVariable());
			e.setQuick(i, 1.0);
			T.setQuick(i, i, 1.0);
		}
		
		for (SecondOrderCone cone : program.getSecondOrderCones()) {
			for (Variable var : cone.getVariables()) {
				int i = program.getIndex(var);
				e.setQuick(i, 0);
				T.setQuick(i, i, 1.0);
			}
			int i = program.getIndex(cone.getNthVariable());
			e.setQuick(i, 1.0);
		}
	}
	
	/**
	 * Computes matrices and vectors that will be used to find search directions
	 * during the current step.
	 * 
	 * The first time this method is called after a call to solve(), new sparse
	 * matrices will be created. At the end of the method call, they will be
	 * converted to sparse, column-compressed matrices. Those matrices will be
	 * reused until solve() is finished, since they will always have the same
	 * non-zero structure. 
	 * 
	 * @param program  program being solved
	 */
	private void getIntermediates(ConicProgram program) {
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		SparseCCDoubleMatrix2D A = program.getA();
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D s = program.getS();
		DoubleMatrix1D c = program.getC();
		int n = (int) x.size();
		
		mu = (v.zDotProduct(v) + tau * kappa) / (k+1);
		
		/* Declares local aliases */
		DoubleMatrix2D ThetaWLocal;
		DoubleMatrix2D invThetaInvWLocal;
		DoubleMatrix2D invThetaSqInvWSqLocal;
		DoubleMatrix2D XBarLocal;
		DoubleMatrix2D invXBarLocal;
		
		/* If this is the first time this method is called after a call to solve()... */
		if (ThetaW == null) {
			ThetaWLocal           = new SparseDoubleMatrix2D(n, n, n*4, 0.2, 0.5);
			invThetaInvWLocal     = new SparseDoubleMatrix2D(n, n, n*4, 0.2, 0.5);
			invThetaSqInvWSqLocal = new SparseDoubleMatrix2D(n, n, n*4, 0.2, 0.5);
			XBarLocal             = new SparseDoubleMatrix2D(n, n, n*4, 0.2, 0.5);
			invXBarLocal          = new SparseDoubleMatrix2D(n, n, n*4, 0.2, 0.5);
		}
		else {
			ThetaWLocal           = ThetaW;
			invThetaInvWLocal     = invThetaInvW;
			invThetaSqInvWSqLocal = invThetaSqInvWSq;
			XBarLocal             = XBar;
			invXBarLocal          = invXBar;
		}
		
		/* Processes NNOCs */
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.getIndex(cone.getVariable());
			double thetaSq = s.getQuick(index) / x.getQuick(index);
			double theta = Math.sqrt(thetaSq);
			double invTheta = 1 / theta;
			ThetaWLocal.setQuick(index, index, theta);
			invThetaInvWLocal.setQuick(index, index, invTheta);
			invThetaSqInvWSqLocal.setQuick(index, index, 1 / thetaSq);
			XBarLocal.setQuick(index, index, theta * x.getQuick(index));
			invXBarLocal.setQuick(index, index, invTheta * 1 / x.getQuick(index));
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
					selection[0] = program.getIndex(var);
				else
					selection[selectionIndex++] = program.getIndex(var);
			}
			
			/* Selects the variables */
			DoubleMatrix1D  dSel    = d.viewSelection(selection);
			double          detDSel = detD.viewSelection(selection).getQuick(0);
			DoubleMatrix1D  vSel    = v.viewSelection(selection);
			
			/* Creates a Q matrix for the variables */
			DoubleMatrix2D Q = new SparseDoubleMatrix2D(nCone, nCone);
			Q.setQuick(0, 0, 1.0);
			for (int i = 1; i < nCone; i++)
				Q.setQuick(i, i, -1.0);
			
			/*
			 * Computes selections of matrices
			 */
			
			/* Gets the selections */
//			DoubleMatrix2D  ThetaWSel           = ThetaW.viewSelection(selection, selection);
//			DoubleMatrix2D  invThetaInvWSel     = invThetaInvW.viewSelection(selection, selection);
//			DoubleMatrix2D  XBarSel             = XBar.viewSelection(selection, selection);
//			DoubleMatrix2D  invXBarSel          = invXBar.viewSelection(selection, selection);
//			DoubleMatrix2D  invThetaSqInvWSqSel = invThetaSqInvWSq.viewSelection(selection, selection);
			
			DoubleMatrix2D temp;
			
			/* Computes invThetaSqInvWSq selection */
			temp = alg.multOuter(dSel, dSel, null);
			for (int i = 0; i < temp.rows(); i++) {
				for (int j = 0; j < temp.columns(); j++) {
					invThetaSqInvWSqLocal.setQuick(selection[i], selection[j], temp.getQuick(i, j));
				}
			}
//			alg.multOuter(dSel, dSel, invThetaSqInvWSqSel);
			temp = Q.copy().assign(DoubleFunctions.mult(detDSel)); 
			for (int i = 0; i < temp.rows(); i++) {
				for (int j = 0; j < temp.columns(); j++) {
					invThetaSqInvWSqLocal.setQuick(selection[i], selection[j], invThetaSqInvWSqLocal.getQuick(selection[i], selection[j]) - temp.getQuick(i, j));
				}
			}
//			invThetaSqInvWSqSel.assign(Q.copy().assign(DoubleFunctions.mult(detDSel)), DoubleFunctions.minus);
			temp = getSOCFunction(getSOCSqrt(getSOCInverse(dSel, detDSel), 1 / detDSel), 1 / Math.sqrt(detDSel));
			for (int i = 0; i < temp.rows(); i++) {
				for (int j = 0; j < temp.columns(); j++) {
					ThetaWLocal.setQuick(selection[i], selection[j], temp.getQuick(i, j));
				}
			}
//			ThetaWSel.assign(getSOCFunction(getSOCSqrt(getSOCInverse(dSel, detDSel), 1 / detDSel), 1 / Math.sqrt(detDSel)));
			temp = getSOCFunction(getSOCSqrt(dSel, detDSel), Math.sqrt(detDSel));
			for (int i = 0; i < temp.rows(); i++) {
				for (int j = 0; j < temp.columns(); j++) {
					invThetaInvWLocal.setQuick(selection[i], selection[j], temp.getQuick(i, j));
				}
			}
//			invThetaInvWSel.assign(getSOCFunction(getSOCSqrt(dSel, detDSel), Math.sqrt(detDSel)));
			
			/* Computes selections of XBar and invXBar */
			DoubleMatrix1D xbar = vSel.copy();
			temp = getArrowheadMatrix(xbar);
			for (int i = 0; i < temp.rows(); i++) {
				for (int j = 0; j < temp.columns(); j++) {
					XBarLocal.setQuick(selection[i], selection[j], temp.getQuick(i, j));
				}
			}
//			XBarSel.assign(getArrowheadMatrix(xbar));
			temp = alg.multOuter(xbar, xbar, null);
			for (int i = 0; i < temp.rows(); i++) {
				for (int j = 0; j < temp.columns(); j++) {
					invXBarLocal.setQuick(selection[i], selection[j], temp.getQuick(i, j) / xbar.getQuick(0));
				}
			}
//			alg.multOuter(xbar, xbar, invXBarSel);
//			invXBarSel.assign(DoubleFunctions.div(xbar.getQuick(0)));
			
			for (int i = 0; i < xbar.size(); i++) {
				invXBarLocal.setQuick(selection[i], selection[0], -1 * xbar.getQuick(i));
				invXBarLocal.setQuick(selection[0], selection[i], -1 * xbar.getQuick(i));
			}
//			invXBarSel.viewColumn(0).assign(xbar).assign(DoubleFunctions.mult(-1));
//			invXBarSel.viewRow(0).assign(xbar).assign(DoubleFunctions.mult(-1));
//			
			invXBarLocal.setQuick(selection[0], selection[0], xbar.getQuick(0));
//			invXBarSel.setQuick(0, 0, xbar.getQuick(0));
			double normSq = Math.pow(alg.norm2(xbar.viewPart(1, nCone-1)), 2);
			double coeff = xbar.getQuick(0) - normSq / xbar.getQuick(0);
			for (int i = 1; i < nCone; i++)
				invXBarLocal.setQuick(selection[i], selection[i], invXBarLocal.getQuick(selection[i], selection[i]) + coeff);
//			for (int i = 1; i < nCone; i++)
//				invXBarSel.setQuick(i, i, invXBarSel.getQuick(i, i) + coeff);
			coeff = Math.pow(xbar.getQuick(0), 2) - normSq;
			for (int i = 0; i < selection.length; i++) {
				for (int j = 0; j < selection.length; j++) {
					invXBarLocal.setQuick(selection[i], selection[j],
							invXBarLocal.getQuick(selection[i], selection[j]) / coeff);
				}
			}
//			invXBarSel.assign(DoubleFunctions.div(Math.pow(xbar.getQuick(0), 2) - normSq));
		}
		
		if (ThetaW == null) {
			/* Creates column-compressed matrices */
			ThetaW            = ((SparseDoubleMatrix2D) ThetaWLocal).getColumnCompressed(false);
			invThetaInvW      = ((SparseDoubleMatrix2D) invThetaInvWLocal).getColumnCompressed(false);
			invThetaSqInvWSq  = ((SparseDoubleMatrix2D) invThetaSqInvWSqLocal).getColumnCompressed(false);
			XBar              = ((SparseDoubleMatrix2D) XBarLocal).getColumnCompressed(false);
			invXBar           = ((SparseDoubleMatrix2D) invXBarLocal).getColumnCompressed(false);
			
			/* Makes memory available to garbage collector */
			ThetaWLocal           = null;
			invThetaInvWLocal     = null;
			invThetaSqInvWSqLocal = null;
			XBarLocal             = null;
			invXBarLocal          = null;
		}
		
		/* Computes more intermediate matrices */
		AInvThetaSqInvWSq = new SparseCCDoubleMatrix2D(A.rows(), n);
		A.zMult(invThetaSqInvWSq, AInvThetaSqInvWSq, 1.0, 0.0, false, false);
		
		/* Computes M and gives it to the normal-system solver */
		SparseCCDoubleMatrix2D M = new SparseCCDoubleMatrix2D(A.rows(), A.rows());
		AInvThetaSqInvWSq.zMult(A, M, 1.0, 0.0, false, true);
		solver.setA(M);
		
		/* Computes intermediate vectors */
		
		/* g2 */
		g2.assign(b);
		AInvThetaSqInvWSq.zMult(c, g2, 1.0, 1.0, false);
		solver.solve(g2);
		
		/* g1 */
		A.zMult(g2, scratchN1, 1.0, 0.0, true);
		scratchN1.assign(c, DoubleFunctions.minus);
		invThetaInvW.zMult(scratchN1, g1, 1.0, 0.0, false);
	}
	
	/**
	 * Computes residuals for the system of equations to be solved.
	 * 
	 * This method optionally uses the search direction to correct the residuals
	 * with a second-order estimate. If the search direction is the Newton
	 * direction, then this is equivalent to the correction used in Mehrotra's
	 * predictor-corrector method.
	 * 
	 * @param program             program being solved
	 * @param gamma               parameter in [0,1] controlling adherence to
	 *                                central path. If 0, this method returns
	 *                                the Newton direction.
	 * @param useSearchDirection  whether the current search direction should
	 *                                be used to correct the residuals with a
	 *                                second-order estimate
	 */
	private void getResiduals(ConicProgram program, double gamma, boolean useSearchDirection) {
		r1.assign(baseResP).assign(DoubleFunctions.mult(gamma-1));
		r2.assign(baseResD).assign(DoubleFunctions.mult(gamma-1));
		r3 = baseResG * (gamma-1);
		
		XBar.zMult(e, scratchN1);
		XBar.zMult(scratchN1, scratchN2);
		r4.assign(e).assign(DoubleFunctions.mult(gamma*mu)).assign(scratchN2, DoubleFunctions.minus);
		
		r5 = gamma * mu - tau * kappa;
		
		/* Corrects residuals with second-order estimate */
		if (useSearchDirection) {
			T.zMult(dx, dxn);
			T.zMult(ds, dsn);
			
			for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
				int index = program.getIndex(cone.getVariable());
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
						selection[0] = program.getIndex(var);
					else
						selection[selectionIndex++] = program.getIndex(var);
				}
				
				/* Selects the variables */
				DoubleMatrix1D dxnSel = dxn.viewSelection(selection);
				DoubleMatrix1D dsnSel = dsn.viewSelection(selection);
				
				/* Gets the selections */
//				DoubleMatrix2D DxnSel  = Dxn.viewSelection(selection, selection);
//				DoubleMatrix2D DsnSel  = Dsn.viewSelection(selection, selection);
				
				/* Computes the arrowhead matrices */
				DoubleMatrix2D temp;
				temp = getArrowheadMatrix(dxnSel);
				for (int i = 0; i < temp.rows(); i++) {
					for (int j = 0; j < temp.columns(); j++) {
						Dxn.setQuick(selection[i], selection[j], temp.getQuick(i, j));
					}
				}
//				DxnSel.assign(getArrowheadMatrix(dxnSel));
				temp = getArrowheadMatrix(dsnSel);
				for (int i = 0; i < temp.rows(); i++) {
					for (int j = 0; j < temp.columns(); j++) {
						Dsn.setQuick(selection[i], selection[j], temp.getQuick(i, j));
					}
				}
//				DsnSel.assign(getArrowheadMatrix(dsnSel));
			}
			
			Dsn.zMult(e, scratchN1);
			Dxn.zMult(scratchN1, scratchN2);
			r4.assign(scratchN2, DoubleFunctions.minus);
			r5 -= dTau * dKappa;
		}
	}
	
	private void getSearchDirection(ConicProgram program) {
		SparseCCDoubleMatrix2D A = program.getA();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D c = program.getC();
		
		invXBar.zMult(r4, scratchN2);
		/* Aliases scratchN1 as TInvVR4. Don't reuse it! */
		DoubleMatrix1D TInvVR4 = T.zMult(scratchN2, scratchN1);
		
		/* h2 */
		/* Aliases scratchM1 as h2. Don't reuse it! */
		DoubleMatrix1D h2 = scratchM1.assign(r1);
		AInvThetaSqInvWSq.zMult(r2, scratchM2);
		h2.assign(scratchM2, DoubleFunctions.plus);
		invThetaInvW.zMult(TInvVR4, scratchN2);
		A.zMult(scratchN2, scratchM2);
		h2.assign(scratchM2, DoubleFunctions.minus);
		solver.solve(h2);
		
		/* h1 */
		/* Aliases scratchN2 as h1. Don't reuse it! */
		A.zMult(h2, scratchN2, 1.0, 0.0, true);
		invThetaInvW.zMult(scratchN2, scratchN3);
		DoubleMatrix1D h1 = scratchN2.assign(TInvVR4);
		h1.assign(scratchN3, DoubleFunctions.plus);
		h1.assign(invThetaInvW.zMult(r2, scratchN3), DoubleFunctions.minus);
	
		/* Computes search direction */
		dTau = r3 + c.zDotProduct(invThetaInvW.zMult(h1, scratchN3)) - b.zDotProduct(h2) + r5 / tau;
		dTau /= ((kappa/tau) - c.zDotProduct(invThetaInvW.zMult(g1, scratchN3)) + b.zDotProduct(g2));
		dx.assign(g1).assign(h1, DoubleFunctions.plusMultFirst(dTau));
		dw.assign(g2).assign(h2, DoubleFunctions.plusMultFirst(dTau));
		dKappa = (r5 - kappa*dTau) / tau;
		ds.assign(TInvVR4).assign(dx, DoubleFunctions.minus);
	}
	
	private double getMaxStepSize(ConicProgram program) {
		double alphaMax = 1.0;
		
		DoubleMatrix1D x = v;
		DoubleMatrix1D s = v;
		
		/* Checks distance to boundaries of cones */
		for (Cone cone : program.getCones()) {
			alphaMax = Math.min(cone.getMaxStep(program.getVarMap(), x, dx), alphaMax);
			alphaMax = Math.min(cone.getMaxStep(program.getVarMap(), s, ds), alphaMax);
		}
		
		/* Checks distance to min. tau */
		if (dTau < 0)
			alphaMax = Math.min(-0.95 * tau / dTau, alphaMax);
		
		/* Checks distance to min. kappa */
		if (dKappa < 0)
			alphaMax = Math.min(-0.95 * kappa / dKappa, alphaMax);
		
		return alphaMax;
	}
	
	private double getStepSize(ConicProgram program
			, double alphaMax, double beta, double gamma
			) {
		DoubleMatrix1D x = v;
		DoubleMatrix1D s = v;
		double stepSize = alphaMax;
		double stepSizeDecrement = alphaMax / 50;
		
		double ssCond = getStepSizeCondition(stepSize, beta, gamma, mu);
		
		while ((tau + stepSize * dTau) * (kappa + stepSize * dKappa) < ssCond) {
			stepSize -= stepSizeDecrement;
			ssCond = getStepSizeCondition(stepSize, beta, gamma, mu);
		}
		
		for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
			int index = program.getIndex(cone.getVariable());
			double vX1 = Math.pow(x.getQuick(index), 2);
			double vX2 = 2 * dx.getQuick(index) * x.getQuick(index);
			double vX3 = Math.pow(dx.getQuick(index), 2);

			double vS1 = Math.pow(s.getQuick(index), 2);
			double vS2 = 2 * ds.getQuick(index) * s.getQuick(index);
			double vS3 = Math.pow(ds.getQuick(index), 2);
			
			while (Math.sqrt(
					(vX1 + stepSize * vX2 + stepSize * stepSize * vX3)
					* (vS1 + stepSize * vS2 + stepSize * stepSize * vS3)
					) < ssCond) {
				stepSize -= stepSizeDecrement;
				ssCond = getStepSizeCondition(stepSize, beta, gamma, mu);
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
					selection[0] = program.getIndex(var);
				else
					selection[selectionIndex++] = program.getIndex(var);
			}
			
			/* Selects the variables */
			DoubleMatrix1D xSel = x.viewSelection(selection);
			DoubleMatrix1D sSel = s.viewSelection(selection);
			DoubleMatrix1D dxSel = dx.viewSelection(selection);
			DoubleMatrix1D dsSel = ds.viewSelection(selection);
			
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
				stepSize -= stepSizeDecrement;
				ssCond = getStepSizeCondition(stepSize, beta, gamma, mu);
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
	
	private void descaleSearchDirection() {
		invThetaInvW.zMult(dx, dxDescaled);
		dwDescaled.assign(dw);
		ThetaW.zMult(ds, dsDescaled);
		dTauDescaled = dTau;
		dKappaDescaled = dKappa;
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
	
	private void removeMatrixReferences() {
		baseResP = null;
		baseResD = null;
		
		T = null;
		e = null;
		
		d    = null;
		detD = null;
		v    = null;
		
		dxn = null;
		dsn = null;
		Dxn = null;
		Dsn = null;
		
		r1 = null;
		r2 = null;
		r4 = null;
		
		XBar = null;
		invXBar = null;
		ThetaW = null;
		invThetaInvW = null;
		invThetaSqInvWSq = null;
		AInvThetaSqInvWSq = null;
		g1 = null;
		g2 = null;
		
		dx = null;
		dw = null;
		ds = null;
		
		dxDescaled = null;
		dwDescaled = null;
		dsDescaled = null;
		
		scratchN1 = null;
		scratchN2 = null;
		scratchN3 = null;
		scratchM1 = null;
		scratchM2 = null;
	}
}
