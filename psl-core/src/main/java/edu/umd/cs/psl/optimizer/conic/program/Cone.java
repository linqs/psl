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
package edu.umd.cs.psl.optimizer.conic.program;

import java.util.Map;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;

abstract public class Cone extends Entity {
	Cone(ConicProgram p) {
		super(p);
	}
	
	abstract public void delete();
	
	abstract public void setBarrierGradient(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix1D g);
	
	abstract public void setBarrierHessian(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix2D H);
	
	abstract public void setBarrierHessianInv(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix2D Hinv);
	
	abstract public boolean isInterior(Map<Variable, Integer> varMap, DoubleMatrix1D x);
	
	abstract public void setInteriorDirection(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix1D d);
	
	abstract public double getMaxStep(Map<Variable, Integer> varMap, DoubleMatrix1D x, DoubleMatrix1D dx);
}
