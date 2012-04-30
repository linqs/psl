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
import java.util.Map;
import java.util.Set;

import mosek.Env;
import mosek.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class MOSEK implements ConicProgramSolver {
	
	private static final Logger log = LoggerFactory.getLogger(MOSEK.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "mosek";
	
	/**
	 * Key for double property. The IPM will iterate until the relative duality gap
	 * is less than its value. Corresponds to the mosek.Env.dparam.intpnt_tol_rel_gap parameter.
	 */
	public static final String DUALITY_GAP_THRESHOLD_KEY = CONFIG_PREFIX + ".dualitygapthreshold";
	/** Default value for DUALITY_GAP_THRESHOLD_KEY property. */
	public static final double DUALITY_GAP_THRESHOLD_DEFAULT = 10e-8;
	
	/**
	 * Key for integer property. Controls the number of threads employed by the
	 * interior-point optimizer. If set to a positive number MOSEK will use this
	 * number of threads. If set to zero, the number of threads used will equal
	 * the number of cores detected on the machine. Corresponds to the
	 * mosek.Env.iparam.intpnt_num_threads parameter.
	 */
	public static final String NUM_THREADS_KEY = CONFIG_PREFIX + ".numthreads";
	/** Default value for DUALITY_GAP_THRESHOLD_KEY property. */
	public static final int NUM_THREADS_DEFAULT = 1;
	
	private ConicProgram program;
	private double dualityGap;
	private int numThreads;
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
		supportedCones.add(ConeType.SecondOrderCone);
	}
	
	private Env environment;
	
	public MOSEK(ConfigBundle config) {
		environment = new mosek.Env();
		environment.init();
		
		program = null;
		dualityGap = config.getDouble(DUALITY_GAP_THRESHOLD_KEY, DUALITY_GAP_THRESHOLD_DEFAULT);
		numThreads = config.getInt(NUM_THREADS_KEY, NUM_THREADS_DEFAULT);
	}

	@Override
	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}

	@Override
	public void setConicProgram(ConicProgram p) {
		program = p;
	}

	@Override
	public void solve() {
		if (program == null)
			throw new IllegalStateException("No conic program has been set.");
		
		program.checkOutMatrices();
		SparseCCDoubleMatrix2D A = program.getA();
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D c = program.getC();
		
		try {
			/* Initializes task */
			Task task = new Task(environment, A.rows(), A.columns());
			task.putcfix(0.0);
			MsgClass msgobj = new MsgClass();
			task.set_Stream(mosek.Env.streamtype.log, msgobj);
			task.putdouparam(mosek.Env.dparam.intpnt_tol_rel_gap, dualityGap);
			task.putintparam(mosek.Env.iparam.intpnt_num_threads, numThreads);
			
			/* Creates the variables and sets the objective coefficients */
			task.append(mosek.Env.accmode.var, (int) x.size());
			for (int i = 0; i < x.size(); i++) {
				task.putcj(i, c.getQuick(i));
			}
			
			/* Processes NonNegativeOrthantCones */
			for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
				int index = program.getIndex(cone.getVariable());
				task.putbound(Env.accmode.var, index, Env.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
			}
			
			/* Processes SecondOrderCones */
			for (SecondOrderCone cone : program.getSecondOrderCones()) {
				int[] indices = new int[cone.getN()];
				int i = 1;
				for (Variable v : cone.getVariables()) {
					int index = program.getIndex(v);
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
			Set<LinearConstraint> constraints = program.getConstraints();
			task.append(mosek.Env.accmode.con, constraints.size());
			
			Map<Variable, Double> variables;
			int listIndex, constraintIndex;
			int[] indexList;
			double[] valueList;
			for (LinearConstraint con : constraints) {
				constraintIndex = program.getIndex(con);
				variables = con.getVariables();
				indexList = new int[variables.size()];
				valueList = new double[variables.size()];
				listIndex = 0;
				for (Map.Entry<Variable, Double> e : variables.entrySet()) { 
					indexList[listIndex] = program.getIndex(e.getKey());
					valueList[listIndex++] = e.getValue();
				}
				task.putavec(mosek.Env.accmode.con, constraintIndex, indexList, valueList);
				task.putbound(mosek.Env.accmode.con, constraintIndex, Env.boundkey.fx,b.getQuick(constraintIndex), b.getQuick(constraintIndex));
			}
			
			/* Solves the program */
			task.putobjsense(mosek.Env.objsense.minimize); 
			log.debug("Starting optimization with {} variables and {} constraints.", A.columns(), A.rows());
			
			task.optimize(); 
			
			log.debug("Completed optimization");
			task.solutionsummary(mosek.Env.streamtype.msg);
			
			mosek.Env.solsta solsta[] = new mosek.Env.solsta[1];
			mosek.Env.prosta prosta[] = new mosek.Env.prosta[1];
			task.getsolutionstatus(mosek.Env.soltype.itr, prosta, solsta);
			
			double[] solution = new double[A.columns()];
			task.getsolutionslice(mosek.Env.soltype.itr, /* Interior solution. */  
									mosek.Env.solitem.xx, /* Which part of solution. */  
									0, /* Index of first variable. */  
									A.columns(), /* Index of last variable+1 */  
									solution);
			
			switch(solsta[0]) {
			case optimal:
			case near_optimal:
				/* Stores solution in conic program */
				x.assign(solution);
				program.checkInMatrices();
				break;
			case dual_infeas_cer:
			case prim_infeas_cer:
			case near_dual_infeas_cer:
			case near_prim_infeas_cer:
				throw new IllegalStateException("Infeasible.");
			case unknown:
				throw new IllegalStateException("Unknown solution status.");
			default:
				throw new IllegalStateException("Other solution status.");
			}
			
			task.dispose();
			
		} catch (mosek.MosekException e) {
			/* Catch both Error and Warning */ 
			throw new AssertionError(e.getMessage()); 
		}
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
