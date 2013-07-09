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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleCholeskyDecomposition;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.optimizer.conic.partition.CompletePartitioner;
import edu.umd.cs.psl.optimizer.conic.partition.ConicProgramPartition;
import edu.umd.cs.psl.optimizer.conic.partition.ObjectiveCoefficientCompletePartitioner;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class ParallelPartitionedIPM extends IPM {

	private static final Logger log = LoggerFactory.getLogger(ParallelPartitionedIPM.class);
	
	private CompletePartitioner partitioner;
	
	private final int threadPoolSize;
	
	public static final String CONFIG_PREFIX = "ppipm";
	
	public static final String THREAD_POOL_SIZE_KEY = CONFIG_PREFIX + ".threadpoolsize";
	public static final int THREAD_POOL_SIZE_DEFAULT = 1;
	
	public ParallelPartitionedIPM(ConfigBundle config) {
		super(config);
		threadPoolSize = config.getInt(THREAD_POOL_SIZE_KEY, THREAD_POOL_SIZE_DEFAULT);
		partitioner = new ObjectiveCoefficientCompletePartitioner(config);
	}
	
	@Override
	public void setConicProgram(ConicProgram p) {
		super.setConicProgram(p);
		partitioner.setConicProgram((dualized) ? dualizer.getDualProgram() : currentProgram);
	}
	
	@Override
	protected void doSolve(ConicProgram program) {
		
		int p;
		double mu, tau, muInitial, theta, err, epsilon_1;
		boolean inNeighborhood;
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		ExecutorService threadPool;
		Vector<PrimalStepRunnable> primalStepRunnables = new Vector<ParallelPartitionedIPM.PrimalStepRunnable>();
		Vector<DualStepRunnable> dualStepRunnables = new Vector<ParallelPartitionedIPM.DualStepRunnable>();
		
		int v = getV(program);
		Set<Cone> cones = program.getCones();
		Map<Variable, Integer> varMap = program.getVarMap();
		
		DoubleMatrix1D x, w, s, dx, dw, ds, r;
		
		x = program.getX();
		w = program.getW();
		s = program.getS();
		
		r = DoubleFactory1D.dense.make((int) x.size());
		DoubleMatrix1D rInitial = DoubleFactory1D.dense.make((int) x.size());
		DoubleMatrix1D g = DoubleFactory1D.dense.make((int) x.size());
		SparseDoubleMatrix2D H = new SparseDoubleMatrix2D((int) x.size(), (int) x.size(), 4 * (int) x.size(), 0.2, 0.5);
		SparseDoubleMatrix2D invH = new SparseDoubleMatrix2D((int) x.size(), (int) x.size(), 4 * (int) x.size(), 0.2, 0.5);
		
		dx = DoubleFactory1D.dense.make((int) x.size());
		ds = DoubleFactory1D.dense.make((int) s.size());
		dw = DoubleFactory1D.dense.make((int) w.size());
		
		partitioner.partition();
		
		partitioner.checkOutAllMatrices();
		
		// log.debug("Partitioner: {}", partitioner);
		
		Vector<Partition> partitions = new Vector<Partition>(partitioner.size());
		for (int i = 0; i < partitioner.size(); i++) {
			ConicProgramPartition cpp = partitioner.getPartition(i);
			Partition partition = new Partition();
			partition.A = cpp.getACopies();
			partition.innerA = cpp.getInnerACopies();
			partition.dx = cpp.get1DViewsByVars(dx);
			partition.innerDw = cpp.get1DViewsByInnerConstraints(dw);
			partition.ds = cpp.get1DViewsByVars(ds);
			partition.r = cpp.get1DViewsByVars(r);
			partition.invH = cpp.getSparse2DByVars(invH);
			partition.primalStepCDs = new Vector<SparseDoubleCholeskyDecomposition>();
			partition.dualStepCDs = new Vector<SparseDoubleCholeskyDecomposition>();
			partitions.add(partition);
		}
		
		/* Initializes mu */
		muInitial = alg.mult(x, s) / v;
		mu = muInitial;
		
		/* Iterates until the duality gap (mu) is sufficiently small */
		inNeighborhood = false;
		p = -1;
		while (mu >= dualityGapThreshold) {
			log.debug("Mu: {}", mu);
			
			for (Cone cone : cones) {
				cone.setBarrierGradient(varMap, x, g);
				cone.setBarrierHessian(varMap, x, H);
				cone.setBarrierHessianInv(varMap, x, invH);
			}
			for (int i = 0; i < partitions.size(); i++) {
				partitioner.getPartition(i).updateSparse2DByVars(invH, partitions.get(i).invH);
				partitions.get(i).primalStepCDs.clear();
				partitions.get(i).dualStepCDs.clear();
			}
			
			if (!inNeighborhood) {
				r.assign(s).assign(g, DoubleFunctions.plusMultSecond(muInitial));
				theta = alg.mult(r, alg.mult(invH, r)) / muInitial;
				log.debug("Theta: {}", theta);
				if (theta < .1)
					inNeighborhood = true;
			}
			
			if (inNeighborhood) {
				tau = 0.85;
			}
			else {
				mu = muInitial;
				tau = 1;
			}
			
			r.assign(g).assign(DoubleFunctions.mult(tau*mu)).assign(s, DoubleFunctions.plus);
			rInitial.assign(r);
			
			epsilon_1 = 0.01;
			err = Math.sqrt(alg.mult(r, alg.mult(invH, r))) /(mu * tau * Math.sqrt(v));
			Partition partition;
			log.debug("Initial error: {}", err);
			do {
				p = (p+1) % partitions.size();
				
				partition = partitions.get(p);
				log.trace("P = {}", p);
				
				if (partition.primalStepCDs.size() == 0 || partition.dualStepCDs.size() == 0) {
					prepareCDs(partition);
				}
				
				/* Sets up the thread pool and jobs */
				threadPool = Executors.newFixedThreadPool(threadPoolSize);
				primalStepRunnables.clear();
				dualStepRunnables.clear();
				
				for (int i = 0; i < partition.dx.size(); i++) {
					PrimalStepRunnable primal = new PrimalStepRunnable(partition.primalStepCDs.get(i), partition.r.get(i), partition.A.get(i), partition.invH.get(i), mu);
					threadPool.execute(primal);
					primalStepRunnables.add(primal);
					DualStepRunnable dual = new DualStepRunnable(partition.dualStepCDs.get(i), partition.r.get(i), partition.innerA.get(i), partition.invH.get(i));
					threadPool.execute(dual);
					dualStepRunnables.add(dual);
				}

				/* Runs the jobs */
				threadPool.shutdown();
				
				/* Waits for the thread pool */
				try {
					while (!threadPool.isTerminated()) {
						threadPool.awaitTermination(5, TimeUnit.MINUTES);
					}
				}
				catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
				
				/* Processes the results */
				for (int i = 0; i < partition.dx.size(); i++) {
					partition.dx.get(i).assign(primalStepRunnables.get(i).getDx(), DoubleFunctions.plus);
					partition.innerDw.get(i).assign(dualStepRunnables.get(i).getDw(), DoubleFunctions.plus);
					partition.ds.get(i).assign(dualStepRunnables.get(i).getDs(), DoubleFunctions.plus);
				}
				
				r.assign(rInitial).assign(alg.mult(H, dx).assign(DoubleFunctions.mult(mu)).assign(ds, DoubleFunctions.plus) , DoubleFunctions.plus);
				log.trace("Done updating r.");

				err = Math.sqrt(alg.mult(r, alg.mult(invH, r))) /(mu * tau * Math.sqrt(v));
				log.trace("Err: {}", err);
				if (Double.isNaN(err)) {
					throw new IllegalStateException();
				}
			} while (err > epsilon_1);
			
			log.debug("Remaining error: {}", err);
			
			double primalStepSize = 1.0;
			double dualStepSize = 1.0;
			for (Cone cone : cones) {
				primalStepSize = Math.min(primalStepSize, cone.getMaxStep(varMap, x, dx));
				dualStepSize = Math.min(dualStepSize, cone.getMaxStep(varMap, s, ds));
			}

			log.trace("Primal step size: {} * {}", primalStepSize, alg.norm2(dx));
			log.trace("Dual step size: {} * {}", dualStepSize, alg.norm2(ds));
			
			x.assign(dx, DoubleFunctions.plusMultSecond(primalStepSize));
			s.assign(ds, DoubleFunctions.plusMultSecond(dualStepSize));
			w.assign(dw, DoubleFunctions.plusMultSecond(dualStepSize));
			
			dx.assign(0);
			ds.assign(0);
			dw.assign(0);
			mu = alg.mult(x, s) / v;
		}
		
		partitioner.checkInAllMatrices();
	}
	
	private void prepareCDs(Partition partition) {
		Vector<CholeskyDecompositionRunnable> primalCDs = new Vector<ParallelPartitionedIPM.CholeskyDecompositionRunnable>(partition.dx.size());
		Vector<CholeskyDecompositionRunnable> dualCDs = new Vector<ParallelPartitionedIPM.CholeskyDecompositionRunnable>(partition.dx.size());
		
		ExecutorService threadPool = Executors.newFixedThreadPool(threadPoolSize);
		
		for (int i = 0; i < partition.dx.size(); i++) {
			SparseCCDoubleMatrix2D A = partition.A.get(i);
			SparseCCDoubleMatrix2D innerA = partition.innerA.get(i);
			DoubleMatrix2D Hinv = partition.invH.get(i);
			
			CholeskyDecompositionRunnable primalCDRunnable = new CholeskyDecompositionRunnable((SparseDoubleMatrix2D) Hinv, A);
			threadPool.execute(primalCDRunnable);
			primalCDs.add(primalCDRunnable);
			
			CholeskyDecompositionRunnable dualCDRunnable = new CholeskyDecompositionRunnable((SparseDoubleMatrix2D) Hinv, innerA);
			threadPool.execute(dualCDRunnable);
			dualCDs.add(dualCDRunnable);
		}
		
		threadPool.shutdown();

		try {
			while (!threadPool.isTerminated()) {
				threadPool.awaitTermination(5, TimeUnit.MINUTES);
			}
		}
		catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
		
		for (int i = 0; i < partition.dx.size(); i++) {
			partition.primalStepCDs.add(primalCDs.get(i).getDecomposition());
			partition.dualStepCDs.add(dualCDs.get(i).getDecomposition());
		}
	}
	
	private class CholeskyDecompositionRunnable implements Runnable {
		
		private final SparseDoubleMatrix2D Hinv;
		private final SparseCCDoubleMatrix2D A;
		private SparseDoubleCholeskyDecomposition cd;
		private boolean run;
		
		public CholeskyDecompositionRunnable(SparseDoubleMatrix2D Hinv, SparseCCDoubleMatrix2D A) {
			this.Hinv = Hinv;
			this.A = A;
			run = false;
		}

		@Override
		public void run() {
			if (!run) {
				SparseCCDoubleMatrix2D Hinv = ((SparseDoubleMatrix2D) this.Hinv).getColumnCompressed(false);
				SparseCCDoubleMatrix2D partial = new SparseCCDoubleMatrix2D(A.rows(), A.columns());
				SparseCCDoubleMatrix2D coeff = new SparseCCDoubleMatrix2D(A.rows(), A.rows());
				A.zMult(Hinv, partial, 1.0, 0.0, false, false);
				partial.zMult(A, coeff, 1.0, 0.0, false, true);
				cd = new SparseDoubleCholeskyDecomposition(coeff, 1);
				run = true;
			}
			else
				throw new IllegalStateException("Runnable already run.");
		}
		
		public SparseDoubleCholeskyDecomposition getDecomposition() {
			if (run)
				return cd;
			else
				throw new IllegalStateException("Runnable not yet run.");
		}
	}
	
	private class PrimalStepRunnable implements Runnable {
		
		private final SparseDoubleCholeskyDecomposition cd;
		private final DoubleMatrix1D r;
		private final SparseCCDoubleMatrix2D A;
		private final SparseDoubleMatrix2D Hinv;
		private final double mu;
		private DenseDoubleMatrix1D dx;
		private boolean run;
		
		PrimalStepRunnable(SparseDoubleCholeskyDecomposition cd, DoubleMatrix1D r, SparseCCDoubleMatrix2D A, SparseDoubleMatrix2D Hinv, double mu) {
			this.cd = cd;
			this.r = r;
			this.A = A;
			this.Hinv = Hinv;
			this.mu = mu;
			run = false;
		}

		@Override
		public void run() {
			if (!run) {
				DoubleMatrix1D dw, ds;
				DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
				
				ds = DoubleFactory1D.dense.make(A.columns());
				SparseCCDoubleMatrix2D Hinv = this.Hinv.getColumnCompressed(false);
				dw = alg.mult(A, alg.mult(Hinv, r.copy()));
				cd.solve(dw);
				A.zMult(dw, ds, 1.0, 0.0, true);
				ds.assign(DoubleFunctions.mult(-1.0));
				dx = (DenseDoubleMatrix1D) alg.mult(Hinv, r.copy().assign(ds, DoubleFunctions.plus)).assign(DoubleFunctions.div(-1 * mu));
				
				run = true;
			}
			else
				throw new IllegalStateException("Runnable already run.");
		}
		
		public DenseDoubleMatrix1D getDx() {
			if (run)
				return dx;
			else
				throw new IllegalStateException("Runnable not yet run.");
		}
	}
	
private class DualStepRunnable implements Runnable {
		
		private final SparseDoubleCholeskyDecomposition cd;
		private final DoubleMatrix1D r;
		private final SparseCCDoubleMatrix2D A;
		private final SparseDoubleMatrix2D Hinv;
		private DenseDoubleMatrix1D ds;
		private DenseDoubleMatrix1D dw;
		private boolean run;
		
		DualStepRunnable(SparseDoubleCholeskyDecomposition cd, DoubleMatrix1D r, SparseCCDoubleMatrix2D A, SparseDoubleMatrix2D Hinv) {
			this.cd = cd;
			this.r = r;
			this.A = A;
			this.Hinv = Hinv;
			run = false;
		}

		@Override
		public void run() {
			if (!run) {
				DenseDoubleAlgebra alg = new DenseDoubleAlgebra();

				SparseCCDoubleMatrix2D Hinv = this.Hinv.getColumnCompressed(false);
				ds = new DenseDoubleMatrix1D(A.columns());
				dw = (DenseDoubleMatrix1D) alg.mult(A, alg.mult(Hinv, r.copy()));
				cd.solve(dw);
				A.zMult(dw, ds, -1.0, 0.0, true);
				
				run = true;
			}
			else
				throw new IllegalStateException("Runnable already run.");
		}
		
		public DenseDoubleMatrix1D getDs() {
			if (run)
				return ds;
			else
				throw new IllegalStateException("Runnable not yet run.");
		}
		
		public DenseDoubleMatrix1D getDw() {
			if (run)
				return dw;
			else
				throw new IllegalStateException("Runnable not yet run.");
		}
	}
	
	private class Partition {
		private List<SparseCCDoubleMatrix2D> A;
		private List<SparseCCDoubleMatrix2D> innerA;
		private List<DoubleMatrix1D> dx;
		private List<DoubleMatrix1D> innerDw;
		private List<DoubleMatrix1D> ds;
		private List<DoubleMatrix1D> r;
		private List<SparseDoubleMatrix2D> invH;
		private List<SparseDoubleCholeskyDecomposition> primalStepCDs;
		private List<SparseDoubleCholeskyDecomposition> dualStepCDs;
	}
}
