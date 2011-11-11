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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

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
import edu.umd.cs.psl.optimizer.conic.partition.SequentialHierarchicalCompletePartitioner;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class PartitionedIPM extends IPM {

	private static final Logger log = LoggerFactory.getLogger(PartitionedIPM.class);
	
	private CompletePartitioner partitioner;
	
	public PartitionedIPM(ConfigBundle config) {
		super(config);
		partitioner = new SequentialHierarchicalCompletePartitioner(config);
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
			partitions.add(partition);
		}
		
		/* Initializes mu */
		muInitial = alg.mult(x, s) / v;
		mu = muInitial;
		
		/* Iterates until the duality gap (mu) is sufficiently small */
		inNeighborhood = false;
		p = -1;
		while (mu > .01) {
			log.trace("Mu: {}", mu);
			
			for (Cone cone : cones) {
				cone.setBarrierGradient(varMap, x, g);
				cone.setBarrierHessian(varMap, x, H);
				cone.setBarrierHessianInv(varMap, x, invH);
			}
			for (int i = 0; i < partitions.size(); i++)
				partitioner.getPartition(i).updateSparse2DByVars(invH, partitions.get(i).invH);
			
			if (!inNeighborhood) {
				r.assign(s).assign(g, DoubleFunctions.plusMultSecond(muInitial));
				theta = alg.mult(r, alg.mult(invH, r)) / muInitial;
				log.trace("Theta: {}", theta);
				if (theta < .1)
					inNeighborhood = true;
			}
			
			if (inNeighborhood) {
				tau = .8;
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
			do {
				p = (p+1) % partitions.size();
				partition = partitions.get(p);
				log.trace("P = {}", p);
				for (int i = 0; i < partition.dx.size(); i++) {
					log.trace("i = {}", i);
					log.trace("{} variables and {} constraints", partition.A.get(i).columns(), partition.A.get(i).rows());
					log.trace("Full space step.");
					fullSpaceStep(partition.r.get(i), partition.A.get(i), partition.invH.get(i), partition.dx.get(i), mu);
					log.trace("Subspace step.");
					subspaceStep(partition.r.get(i), partition.innerA.get(i), partition.invH.get(i), partition.ds.get(i), partition.innerDw.get(i));
				}
				log.trace("Updating r.");
				r.assign(rInitial).assign(alg.mult(H, dx).assign(DoubleFunctions.mult(mu)).assign(ds, DoubleFunctions.plus) , DoubleFunctions.plus);
				log.trace("Done updating r.");

				log.trace("Full space step.");
				err = Math.sqrt(alg.mult(r, alg.mult(invH, r))) /(mu * tau * Math.sqrt(v));
				log.trace("Err: {}", err);
				if (Double.isNaN(err)) {
					throw new IllegalStateException();
				}
			} while (err > epsilon_1);
			
			if (!inNeighborhood) {
				double primalStepSize = 1.0;
				double dualStepSize = 1.0;
				for (Cone cone : cones)
					primalStepSize = Math.min(primalStepSize, cone.getMaxStep(varMap, x, dx));
				for (Cone cone : cones)
					dualStepSize = Math.min(primalStepSize, cone.getMaxStep(varMap, s, ds));

				log.trace("Primal step size: {} * {}", primalStepSize, alg.norm2(dx));
				log.trace("Dual step size: {} * {}", dualStepSize, alg.norm2(ds));
				
				x.assign(dx, DoubleFunctions.plusMultSecond(primalStepSize));
				s.assign(ds, DoubleFunctions.plusMultSecond(dualStepSize));
				w.assign(dw, DoubleFunctions.plusMultSecond(dualStepSize));
			}
			else {
				x.assign(dx, DoubleFunctions.plus);
				s.assign(ds, DoubleFunctions.plus);
				w.assign(dw, DoubleFunctions.plus);
			}
			
			dx.assign(0);
			ds.assign(0);
			dw.assign(0);
			mu = alg.mult(x, s) / v;
		}
		
		partitioner.checkInAllMatrices();
	}

	private void fullSpaceStep(DoubleMatrix1D r, DoubleMatrix2D A, DoubleMatrix2D Hinv, DoubleMatrix1D dx, double mu) {
		DoubleMatrix1D dw, ds;
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		SparseDoubleCholeskyDecomposition cd;

		ds = DoubleFactory1D.dense.make(A.columns());

		SparseCCDoubleMatrix2D partial = new SparseCCDoubleMatrix2D(A.rows(), A.columns());
		SparseCCDoubleMatrix2D coeff = new SparseCCDoubleMatrix2D(A.rows(), A.rows());
		Hinv = ((SparseDoubleMatrix2D) Hinv).getColumnCompressed(false);
		
		A.zMult(Hinv, partial, 1.0, 0.0, false, false);
		partial.zMult(A, coeff, 1.0, 0.0, false, true);
		
		cd = new SparseDoubleCholeskyDecomposition(coeff, 1);
		dw = alg.mult(A, alg.mult(Hinv, r.copy()));
		cd.solve(dw);
		if (dw.copy().assign(DoubleFunctions.compare(Double.NEGATIVE_INFINITY)).cardinality() != dw.size()) {
			throw new IllegalStateException();
		}
		A.zMult(dw, ds, 1.0, 0.0, true);
		ds.assign(DoubleFunctions.mult(-1.0));
		if (alg.mult(Hinv, r.copy().assign(ds, DoubleFunctions.plus).assign(DoubleFunctions.mult(-1.0))).assign(DoubleFunctions.div(mu)).assign(DoubleFunctions.compare(Double.NEGATIVE_INFINITY)).cardinality() != ds.size()) {
			throw new IllegalStateException();
		}
		dx.assign(alg.mult(Hinv, r.copy().assign(ds, DoubleFunctions.plus).assign(DoubleFunctions.mult(-1.0))).assign(DoubleFunctions.div(mu)), DoubleFunctions.plus);
	}
	
	private void subspaceStep(DoubleMatrix1D r, DoubleMatrix2D innerA, DoubleMatrix2D Hinv, DoubleMatrix1D ds, DoubleMatrix1D innerDw) {
		DoubleMatrix1D dw;
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		SparseDoubleCholeskyDecomposition cd;

		SparseDoubleMatrix2D HinvNew = new SparseDoubleMatrix2D(Hinv.columns(), Hinv.columns());
		
		for (int i = 0; i < Hinv.columns(); i++) {
			HinvNew.setQuick(i, i, Hinv.getQuick(i, i));
		}
		Hinv = HinvNew.getColumnCompressed(false);
		
		DenseDoubleMatrix1D newDs = new DenseDoubleMatrix1D((int) ds.size());

		SparseCCDoubleMatrix2D partial = new SparseCCDoubleMatrix2D(innerA.rows(), innerA.columns());
		SparseCCDoubleMatrix2D coeff = new SparseCCDoubleMatrix2D(innerA.rows(), innerA.rows());
		innerA.zMult(Hinv, partial, 1.0, 0.0, false, false);
		partial.zMult(innerA, coeff, 1.0, 0.0, false, true);
		cd = new SparseDoubleCholeskyDecomposition(coeff, 1);
		dw = alg.mult(innerA, alg.mult(Hinv, r.copy()));
		cd.solve(dw);
		innerA.zMult(dw, newDs, 1.0, 0.0, true);
		newDs.assign(DoubleFunctions.mult(-1.0));
		ds.assign(newDs, DoubleFunctions.plus);
		innerDw.assign(dw, DoubleFunctions.plus);
	}
	
	private class Partition {
		private List<SparseCCDoubleMatrix2D> A;
		private List<SparseCCDoubleMatrix2D> innerA;
		private List<DoubleMatrix1D> dx;
		private List<DoubleMatrix1D> innerDw;
		private List<DoubleMatrix1D> ds;
		private List<DoubleMatrix1D> r;
		private List<SparseDoubleMatrix2D> invH;
	}
}
