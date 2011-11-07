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
package edu.umd.cs.psl.optimizer.conic.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleLUDecompositionQuick;
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleQRDecomposition;
import cern.colt.matrix.tdouble.algo.decomposition.SparseDoubleQRDecomposition;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgramEvent;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgramListener;
import edu.umd.cs.psl.optimizer.conic.program.Entity;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class FeasiblePointInitializer implements ConicProgramListener {
	
	private static final Logger log = LoggerFactory.getLogger(ConicProgram.class);
	
	private final ConicProgram program;
	
	private Set<LinearConstraint> primalInfeasible;
	private Set<Variable> dualInfeasible;
	private Map<LinearConstraint, IsolatedStructure> primalIsolated;
	private Map<Variable, LinearConstraint> dualIsolated;
	
	private boolean madePrimalFeasibleOnce;
	private boolean madeDualFeasibleOnce;
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
	}
	
	// Error messages
	private static final String UNEXPECTED_SENDER = "Unexpected sender type.";
	private static final String UNEXPECTED_DATA = "Unexpected data.";

	public FeasiblePointInitializer(ConicProgram p) {
		program = p;
		program.registerForConicProgramEvents(this);
		
		primalInfeasible = new HashSet<LinearConstraint>();
		dualInfeasible = new HashSet<Variable>();
		primalIsolated = new HashMap<LinearConstraint, FeasiblePointInitializer.IsolatedStructure>();
		dualIsolated = new HashMap<Variable, LinearConstraint>();
		
		madePrimalFeasibleOnce = false;
		madeDualFeasibleOnce = false;
	}
	
	public static boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}
	
	@Override
	public void notify(ConicProgram sender, ConicProgramEvent event, Entity entity, Object... data) {
		switch (event) {
		case NNOCCreated:
		case NNOCDeleted:
			if (entity instanceof NonNegativeOrthantCone) {
				NonNegativeOrthantCone nnoc = (NonNegativeOrthantCone) entity;
				switch (event) {
				case NNOCCreated:
					if (madeDualFeasibleOnce) dualInfeasible.add(nnoc.getVariable());
					break;
				case NNOCDeleted:
					dualInfeasible.remove(nnoc.getVariable());
					dualIsolated.remove(nnoc.getVariable());
					break;
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case SOCCreated:
		case SOCDeleted:
			if (entity instanceof SecondOrderCone) {
				SecondOrderCone soc = (SecondOrderCone) entity;
				switch (event) {
				case SOCCreated:
					if (madeDualFeasibleOnce)
						for (Variable v : soc.getVariables())
							dualInfeasible.add(v);
					break;
				case SOCDeleted:
					for (Variable v : soc.getVariables()) {
						dualInfeasible.remove(v);
						dualIsolated.remove(v);
					}
					break;
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case ObjCoeffChanged:
			if (entity instanceof Variable) {
				Variable var = (Variable) entity;
				if (madeDualFeasibleOnce) dualInfeasible.add(var);
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case ConCreated:
		case ConValueChanged:
		case ConDeleted:
			if (entity instanceof LinearConstraint) {
				LinearConstraint lc = (LinearConstraint) entity;
				switch(event) {
				case ConCreated:
				case ConValueChanged:
					if (madePrimalFeasibleOnce) primalInfeasible.add(lc);
					break;
				case ConDeleted:
					primalInfeasible.remove(lc);
					primalIsolated.remove(lc);
				}
			}
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		case VarAddedToCon:
		case VarRemovedFromCon:
			if (entity instanceof LinearConstraint && data.length > 0 && data[0] instanceof Variable) {
				LinearConstraint lc = (LinearConstraint) entity;
				Variable var = (Variable) data[0];
				if (madePrimalFeasibleOnce) {
					primalInfeasible.add(lc);
				}
				if (madeDualFeasibleOnce) {
					dualInfeasible.add(var);
				}
				primalIsolated.remove(lc);
				if (lc.equals(dualIsolated.get(var))) {
					dualIsolated.remove(var);
				}
				if (ConicProgramEvent.VarAddedToCon.equals(event)) {
					for (Variable v : lc.getVariables().keySet()) {
						if (lc.equals(dualIsolated.get(v))) {
							dualIsolated.remove(v);
						}
					}
				}
			}
			else if (entity instanceof LinearConstraint)
				throw new IllegalArgumentException(UNEXPECTED_DATA);
			else
				throw new IllegalArgumentException(UNEXPECTED_SENDER);
			break;
		}
	}

	public void makeFeasible() {
		makePrimalFeasible();
		makeDualFeasible();
	}
	
	public void makePrimalFeasible() {
		if (!supportsConeTypes(program.getConeTypes()))
			throw new IllegalStateException("Unsupported cone type.");
		
		LinearConstraint lc;
		IsolatedStructure iso;
		Map<LinearConstraint, Integer> lcInit = new HashMap<LinearConstraint, Integer>();
		Map<Variable, Integer> varInit = new HashMap<Variable, Integer>();
		
		final Set<IsolatedStructure> isoNeedsInit = new HashSet<IsolatedStructure>();
		
		LinearConstraintTraversal trav = new LinearConstraintTraversal(new LinearConstraintTraversalEvaluator() {
			@Override
			public boolean next(LinearConstraint con) {
				IsolatedStructure iso = getPrimalIsolatedStructure(con);
				if (iso != null) {
					isoNeedsInit.add(iso);
					return false;
				}
				else
					return true;
			}
		});

		int i,j;
		Iterator<LinearConstraint> itr;
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		DoubleMatrix1D x, dp, intDir, feasibilityCheck;
		SparseDoubleMatrix2D A;
		DoubleMatrix2D nullity;
		DenseDoubleLUDecompositionQuick lu = new DenseDoubleLUDecompositionQuick();
		Set<Cone> cones = new HashSet<Cone>();
		
		if (!madePrimalFeasibleOnce)
			 primalInfeasible.addAll(program.getConstraints());
		
		log.debug("Finding primal feasible point: {} constraints marked", primalInfeasible.size());
		
		while (!primalInfeasible.isEmpty()) {
			log.trace("{} primal constraints left to initialize.", primalInfeasible.size());
			lc = primalInfeasible.iterator().next();
			iso = getPrimalIsolatedStructure(lc);
			if (iso != null) {
				isoNeedsInit.add(iso);
				primalInfeasible.remove(lc);
			}
			else {
				i = 0;
				lcInit.clear();
				j = 0;
				varInit.clear();
				cones.clear();
				itr = trav.traverse(lc);
				
				/* Collects linear constraints to be initialized */
				while (itr.hasNext()) {
					lc = itr.next();
					primalInfeasible.remove(lc);
					lcInit.put(lc, i++);
				}
				
				/* Collects variables to be initialized */
				for (LinearConstraint con : lcInit.keySet())
					for (Variable v : con.getVariables().keySet())
						if (!varInit.containsKey(v)) {
							varInit.put((Variable) v, j++);
							cones.add((Cone) ((Variable) v).getCone());
						}
				
				/* Initializes data matrices */
				A = (SparseDoubleMatrix2D) DoubleFactory2D.sparse.make(lcInit.size(), varInit.size());
				x = DoubleFactory1D.dense.make(varInit.size());
				
				/* Constructs A, b, and AstarA */
				for (Map.Entry<LinearConstraint, Integer> con : lcInit.entrySet()) {
					for (Entry<Variable, Double> v : con.getKey().getVariables().entrySet()) {
						/*
						 * This monstrosity of a statement sets an element of A to its
						 * coefficient. The element is specified by the constraint index
						 * (the value of the lcInit entry) and the variable index
						 * (the value stored in varInit with the same key as the
						 * key of v). The value of v is the coefficient.
						 */
						A.set(con.getValue(), varInit.get(v.getKey()), v.getValue());
					}
					x.set(con.getValue(), con.getKey().getConstrainedValue());
				}
				
				/* Uses decompositions to find general and particular solutions to Ax = b */
				DenseDoubleQRDecomposition denseQR = new DenseDoubleQRDecomposition(new DenseDoubleMatrix2D(A.rows(), A.columns()).assign(A).viewDice());
				nullity = denseQR.getQ(false).viewPart(0, A.rows(), A.columns(), A.columns()-A.rows());
				
				SparseDoubleQRDecomposition qr = new SparseDoubleQRDecomposition(A.getColumnCompressed(false), 0);
				qr.solve(x);
				
				lu.decompose(alg.mult(nullity.viewDice(), nullity));
				intDir = x.copy();
				feasibilityCheck = x.viewSelection(DoubleFunctions.isLess(0.025));
				// TODO: Add check for getting stuck
				while (feasibilityCheck.size() > 0) {
					for (Cone c : cones) {
						((Cone) c).setInteriorDirection(varInit, x, intDir);
					}
					dp = alg.mult(nullity.viewDice(), intDir);
					lu.solve(dp);
					x.assign(alg.mult(nullity, dp), DoubleFunctions.plus);
					feasibilityCheck = x.viewSelection(DoubleFunctions.isLess(0.025));
				}

				/* Finalize initialization */
				for (Map.Entry<Variable, Integer> v : varInit.entrySet())
					program.getX().set(program.index(v.getKey()), x.get(v.getValue()));
			}
		}
		
		/* Initializes the isolated structures */
		for (IsolatedStructure toInit : isoNeedsInit) {
			toInit.makePrimalFeasible();
		}
		
		madePrimalFeasibleOnce = true;
	}
	
	public void makeDualFeasible() {
		if (!supportsConeTypes(program.getConeTypes()))
			throw new IllegalStateException("Unsupported cone type.");
		
		Variable var;
		LinearConstraint iso;
		Map<LinearConstraint, Integer> lcInit = new HashMap<LinearConstraint, Integer>();
		Map<Variable, Integer> varInit = new HashMap<Variable, Integer>();
		
		final Set<LinearConstraint> isoNeedsInit = new HashSet<LinearConstraint>();
		
		VariableTraversal trav = new VariableTraversal((new VariableTraversalEvaluator() {
			@Override
			public boolean next(Variable var) {
				LinearConstraint iso = getDualIsolatedConstraint(var);
				if (iso != null) {
					isoNeedsInit.add(iso);
					return false;
				}
				else
					return true;
			}
			
		}));

		int i,j;
		Iterator<Variable> itr;
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		DoubleMatrix1D w, dw, s, ds, c, r;
		DoubleMatrix2D A;
		DenseDoubleLUDecompositionQuick lu = new DenseDoubleLUDecompositionQuick();
		double stepSize;
		
		if (!madeDualFeasibleOnce) {
			for (Cone cone : program.getCones()) {
				if (cone instanceof NonNegativeOrthantCone) {
					dualInfeasible.add(((NonNegativeOrthantCone) cone).getVariable());
				}
				else
					throw new IllegalStateException("Unsupported cone type.");
			}
		}

		log.debug("Finding dual feasible point: {} constraints marked.", dualInfeasible.size());
		
		while (!dualInfeasible.isEmpty()) {
			log.trace("{} dual constraints left to initialize.", dualInfeasible.size());
			var = dualInfeasible.iterator().next();
			iso = getDualIsolatedConstraint(var);
			if (iso != null) {
				makeDualFeasible(iso);
				for (Variable v : iso.getVariables().keySet())
					dualInfeasible.remove(v);
			}
			else {
				i = 0;
				varInit.clear();
				j = 0;
				lcInit.clear();
				itr = trav.traverse(var);
				
				/* Collects variables to be initialized */
				while (itr.hasNext()) {
					var = itr.next();
					dualInfeasible.remove(var);
					if (!varInit.containsKey(var)){
						varInit.put(var, i++);
					}
				}
			
				/* Collects linear constraints to be initialized */
				for (Variable v : varInit.keySet()) {
					for (LinearConstraint lc : v.getLinearConstraints()) {
						if (!lcInit.containsKey(lc)) {
							lcInit.put((LinearConstraint) lc, j++);
						}
					}
				}
				
				/* Adds isolated linear constraints and any additional variables */
				for (LinearConstraint lc : isoNeedsInit) {
					lcInit.put(lc, j++);
					for (Variable v : lc.getVariables().keySet()) {
						if (!varInit.containsKey(v)) {
							varInit.put(v, i++);
							dualInfeasible.remove(v);
						}
					}
				}
				
				/* Initializes data matrices */
				A = DoubleFactory2D.sparse.make(lcInit.size(), varInit.size());
				w = DoubleFactory1D.dense.make(lcInit.size());
				s = DoubleFactory1D.dense.make(varInit.size());
				c = DoubleFactory1D.dense.make(varInit.size());
				r = DoubleFactory1D.dense.make(varInit.size());
				
				/* Constructs A and w */
				for (Map.Entry<LinearConstraint, Integer> lc : lcInit.entrySet()) {
					for (Map.Entry<Variable, Double> v : lc.getKey().getVariables().entrySet()) {
						if (varInit.containsKey(v.getKey())) {
							try{
							A.set(lc.getValue(), varInit.get(v.getKey()), v.getValue());
							}
							catch(IndexOutOfBoundsException e) {
								throw e;
							}
						}
					}
					w.set(lc.getValue(), lc.getKey().getLagrange());
				}
				
				lu.decompose(alg.mult(A, A.viewDice()));
				
				/* Constructs s and c */
				for (Map.Entry<Variable, Integer> v : varInit.entrySet()) {
					s.set(v.getValue(), v.getKey().getDualValue());
					double temp = 0.0;
					for (LinearConstraint lc : v.getKey().getLinearConstraints())
						if (!lcInit.containsKey(lc))
							temp += lc.getVariables().get(v.getKey()) * lc.getLagrange();
					c.set(v.getValue(), v.getKey().getObjectiveCoefficient() - temp);
				}
				
				/* Initializes r */
				r = alg.mult(A.viewDice(), w).assign(s, DoubleFunctions.plus).assign(c, DoubleFunctions.minus);
				
				boolean wStep = true;
				// TODO: Add check for getting stuck
				while (alg.norm2(r) > 10e-8) {
					dw = alg.mult(A, r);
					lu.solve(dw);
					
					if (wStep) {
						wStep = false;
						dw = alg.mult(A, r);
						lu.solve(dw);
						w.assign(dw, DoubleFunctions.minus);
					}
					else {
						wStep = true;
						ds = r;
						stepSize = 1;
						for (int k = 0; k < s.size(); k++) {
							if (s.get(k) - ds.get(k) * stepSize < 10e-8) {
								while (s.get(k) - ds.get(k) * stepSize < 10e-8) {
									stepSize *= .67;
									if (stepSize < 10e-2)
										s.set(k, s.get(k) + .5);
								}
							}
						}
						s.assign(ds, DoubleFunctions.minusMult(stepSize));
					}
					
					r = alg.mult(A.viewDice(), w).assign(s, DoubleFunctions.plus).assign(c, DoubleFunctions.minus);
				}
				
				/* Finalize initialization */
				for (Map.Entry<Variable, Integer> v : varInit.entrySet()) {
					program.getS().set(program.index(v.getKey()), s.get(v.getValue()));
				}
				for (Map.Entry<LinearConstraint, Integer> lc : lcInit.entrySet())
					program.getW().set(program.index(lc.getKey()), w.get(lc.getValue()));
				
				isoNeedsInit.clear();
			}
		}
		
		madeDualFeasibleOnce = true;
	}

	private void makeDualFeasible(LinearConstraint lc) {
		double temp;
		for (Map.Entry<Variable, Double> e : lc.getVariables().entrySet()) {
			temp = 0.0;
			for (LinearConstraint con : e.getKey().getLinearConstraints()) {
				temp += con.getVariables().get(e.getKey()) * con.getLagrange();
			}
			if (e.getKey().getObjectiveCoefficient() - temp - 0.05 < 0) {
				temp -= lc.getVariables().get(e.getKey()) * lc.getLagrange();
				temp = e.getKey().getObjectiveCoefficient() - temp - 0.25;
				program.getW().set(program.index(lc), temp / lc.getVariables().get(e.getKey()));
			}
		}
		
		for (Variable v : lc.getVariables().keySet()) {
			temp = 0.0;
			for (LinearConstraint con : v.getLinearConstraints()) {
				temp += con.getVariables().get(v) * con.getLagrange();
			}
			program.getS().set(program.index(v), v.getObjectiveCoefficient() - temp);
//			if (!v.isDualFeasible()) {
//				log.error("Variable dual infeasible after isolated initialization.");
//			}
		}
	}

	private IsolatedStructure getPrimalIsolatedStructure(LinearConstraint lc) {
		IsolatedStructure iso = primalIsolated.get(lc);
		if (iso != null)
			return iso;
		
		//if (Boolean.TRUE.equals(node.getAttribute(ConicProgram.CHECK_ISOLATED))) {
		/* Checks for linear isolated structure */
		Variable	positiveSlack = null,
					negativeSlack = null;
		for (Entry<Variable, Double> e : lc.getVariables().entrySet()) {
			if (e.getKey().getLinearConstraints().size() == 1
					&& e.getKey().getCone() instanceof NonNegativeOrthantCone) {
				if (e.getValue() < 0) {
					negativeSlack = e.getKey();
					if (positiveSlack != null)
						break;
				}
				else if (e.getValue() > 0) {
					positiveSlack = e.getKey();
					if (negativeSlack != null)
						break;
				}
			}
		}
		
		if (positiveSlack != null && negativeSlack != null) {
			iso = new LinearIsolatedStructure(lc, positiveSlack, negativeSlack);
			primalIsolated.put(lc, iso);
			return iso;
		}
		
		/* If execution reaches this line, then no isolated structure could be found */
		
		return null;
	}
	
	private LinearConstraint getDualIsolatedConstraint(Variable v) {
		LinearConstraint lc = dualIsolated.get(v);
		if (lc != null)
			return lc;

		boolean posCoeff, isolated;
		for (LinearConstraint con : v.getLinearConstraints()) {
			isolated = true;
			if (con.getVariables().get(v) != 0.0) {
				posCoeff = (con.getVariables().get(v) > 0.0) ? true : false;
				for (Double coeff : con.getVariables().values()) {
					isolated = isolated  && (((posCoeff && coeff > 0.0) ? true : false) || ((!posCoeff && coeff < 0.0)));
				}
				if (isolated) {
					dualIsolated.put(v, con);
					return con;
				}
			}
		}
		return null;
	}
	
	abstract private class IsolatedStructure {
		abstract void makePrimalFeasible();
	}
	
	private class LinearIsolatedStructure extends IsolatedStructure {
		private LinearConstraint lc;
		private Variable positiveSlack;
		private Variable negativeSlack;


		LinearIsolatedStructure(LinearConstraint lc, Variable positiveSlack,
				Variable negativeSlack) {
			super();
			this.lc = lc;
			this.positiveSlack = positiveSlack;
			this.negativeSlack = negativeSlack;
		}

		@Override
		void makePrimalFeasible() {
			double value, diff;

			value = 0.0;
			for (Entry<Variable, Double> e : lc.getVariables().entrySet()) {
				value += e.getKey().getValue() * e.getValue();
			}
			diff = lc.getConstrainedValue() - value;
			if (diff < 0) {
				program.getX().set(program.index(negativeSlack), negativeSlack.getValue() - diff);
			}
			else {
				program.getX().set(program.index(positiveSlack), positiveSlack.getValue() - diff);
			}
		}
	}
	
	private interface LinearConstraintTraversalEvaluator extends TraversalEvaluator<LinearConstraint> { }
	
	private class LinearConstraintTraversal extends Traversal<LinearConstraint> {
		private LinearConstraintTraversal(LinearConstraintTraversalEvaluator e) {
			super(e);
		}

		@Override
		Set<LinearConstraint> getNeighbors(LinearConstraint ego) {
			Set<LinearConstraint> neighbors = new HashSet<LinearConstraint>();
			for (Variable v : ego.getVariables().keySet())
				for (LinearConstraint con : v.getLinearConstraints())
					neighbors.add(con);
			return neighbors;
		}
	}
	
	private interface VariableTraversalEvaluator extends TraversalEvaluator<Variable> {	}
	
	private class VariableTraversal extends Traversal<Variable> {
		private VariableTraversal(VariableTraversalEvaluator e) {
			super(e);
		}

		@Override
		Set<Variable> getNeighbors(Variable ego) {
			Set<Variable> neighbors = new HashSet<Variable>();
			for (LinearConstraint con : ego.getLinearConstraints())
				for (Variable v : con.getVariables().keySet())
					neighbors.add(v);
			return neighbors;
		}
	}
	
	private interface TraversalEvaluator<T> {
		public boolean next(T n);
	}
	
	abstract private class Traversal<T> {
		private Stack<T> stack;
		private Set<T> closedSet;
		private TraversalEvaluator<T> eval;
		
		private Traversal(TraversalEvaluator<T> e) {
			stack = new Stack<T>();
			closedSet = new HashSet<T>();
			eval = e;
		}
		
		abstract Set<T> getNeighbors(T ego);
		
		Iterator<T> traverse(T origin) {
			if (!stack.empty())
				throw new IllegalStateException("Did not finish previous traversal.");
			
			closedSet.clear();
			
			if (eval.next(origin)) {
				stack.push(origin);
				closedSet.add(origin);
			}
			return new Iterator<T>() {

				@Override
				public boolean hasNext() {
					return !stack.isEmpty();
				}

				@Override
				public T next() {
					T next = stack.pop();
					for (T t : getNeighbors(next)) {
						if (!closedSet.contains(t) && eval.next(t)) {
							stack.push(t);
							closedSet.add(t);
						}
					}
					return next;
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
}
