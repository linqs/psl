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
package edu.umd.cs.psl.optimizer.conic.mosek;

import java.util.ArrayList;
import java.util.Collection;

import mosek.Env;
import mosek.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.list.tdouble.DoubleArrayList;
import cern.colt.list.tint.IntArrayList;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class MOSEK implements ConicProgramSolver {
	
	private static final Logger log = LoggerFactory.getLogger(MOSEK.class);
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
		supportedCones.add(ConeType.SecondOrderCone);
	}
	
	private Env environment;
	
	public MOSEK(ConfigBundle config) {
		environment = new mosek.Env();
		environment.init();
	}

	@Override
	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}

	@Override
	public Double solve(ConicProgram program) {
		program.checkOutMatrices();
		SparseDoubleMatrix2D A = program.getA();
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D c = program.getC();
		
		try {
			/* Initializes task */
			Task task = new Task(environment, A.rows(), A.columns());
			task.putcfix(0.0);
			MsgClass msgobj = new MsgClass();
			task.set_Stream(mosek.Env.streamtype.log, msgobj);
			
			/* Creates the variables and sets the objective coefficients */
			for (int i = 0; i < x.size(); i++) {
				task.append(mosek.Env.accmode.var,1);
				task.putcj(i, c.getQuick(i));
			}
			
			/* Processes NonNegativeOrthantCones */
			for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
				int index = program.index(cone.getVariable());
				task.putbound(Env.accmode.var, index, Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
			}
			
			/* Processes SecondOrderCones */
			for (SecondOrderCone cone : program.getSecondOrderCones()) {
				int[] indices = new int[cone.getN()];
				int i = 1;
				for (Variable v : cone.getVariables()) {
					int index = program.index(v);
					if (v.equals(cone.getNthVariable())) {
						indices[0] = index;
						task.putbound(Env.accmode.var, index, Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					}
					else {
						indices[i++] = index;
						task.putbound(Env.accmode.var, index, Env.boundkey.fr, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
					}
				}
				task.appendcone(Env.conetype.quad, 0.0, indices);
			}
			
			/* Sets the linear constraints */
			for (int j = 0; j < A.rows(); j++) {
				DoubleMatrix1D con = A.viewRow(j);
				IntArrayList indexList = new IntArrayList();
				DoubleArrayList valueList = new DoubleArrayList();
				con.getNonZeros(indexList, valueList);
				task.append(mosek.Env.accmode.con,1);
				task.putavec(mosek.Env.accmode.con, j, indexList.elements(), valueList.elements());
				task.putbound(mosek.Env.accmode.con,j,Env.boundkey.fx,b.getQuick(j),b.getQuick(j));
			}
			
			/* Solves the program */
			task.putobjsense(mosek.Env.objsense.minimize); 
			log.debug("Starting optimization with {} variables and {} constraints.", A.columns(), A.rows());
			
			task.optimize(); 
			
			log.debug("Completed optimization");
			task.solutionsummary(mosek.Env.streamtype.msg); 
			
			double[] solution = new double[A.columns()];
			task.getsolutionslice(mosek.Env.soltype.itr, /* Interior solution. */  
									mosek.Env.solitem.xx, /* Which part of solution. */  
									0, /* Index of first variable. */  
									A.columns(), /* Index of last variable+1 */  
									solution);
			
			/* Stores solution in conic program */
			x.assign(solution);
			program.checkInMatrices();
			
			task.dispose();
			
		} catch (mosek.MosekException e) {
			/* Catch both Error and Warning */ 
			throw new AssertionError(e.getMessage()); 
		}
		
		return null;
	}

	class MsgClass extends mosek.Stream { 
		public MsgClass () { 
			super (); 
		} 
		public void stream (String msg) {
			log.trace(msg);
		} 
	}
}
