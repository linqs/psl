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
package edu.umd.cs.psl.optimizer.conic.partition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.function.tdouble.IntIntDoubleFunction;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConeType;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgramEvent;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgramListener;
import edu.umd.cs.psl.optimizer.conic.program.Entity;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.RotatedSecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;

public class ConicProgramPartition implements ConicProgramListener {

	private static final Logger log = LoggerFactory.getLogger(ConicProgramPartition.class);
	
	private ConicProgram program;
	
	private Vector<Set<Cone>> elements;
	
	private Map<Cone, Integer> coneMap;
	
	private Set<Cone> unassignedCones;
	
	private Set<LinearConstraint> cutConstraints;
	
	private boolean checkedOut;
	
	private boolean cutConstraintsDirty;
	
	private Vector<SparseCCDoubleMatrix2D> APart;
	private Vector<SparseCCDoubleMatrix2D> innerAPart;
	private int[] varElementMap;
	private int[] varIndexMap;
	private int[][] varSelections;
	private int[][] innerConSelections;
	
	private static final ArrayList<ConeType> supportedCones = new ArrayList<ConeType>(2);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
		supportedCones.add(ConeType.SecondOrderCone);
	}
	
	public ConicProgramPartition(ConicProgram p, Collection<Set<Cone>> partition) {
		if (!supportsConeTypes(p.getConeTypes()))
			throw new IllegalArgumentException("Program contains unsupported cone type.");
		
		program = p;
		
		unassignedCones = new HashSet<Cone>(program.getCones());
		cutConstraints = new HashSet<LinearConstraint>();
		elements = new Vector<Set<Cone>>(partition.size());
		coneMap = new HashMap<Cone, Integer>(unassignedCones.size());
		
		checkedOut = false;
		cutConstraintsDirty = true;
		
		int i = 0;
		for (Set<Cone> cones : partition) {
			elements.add(new HashSet<Cone>());
			for (Cone c : cones)
				addCone(c, i);
			i++;
		}
		
//		program.registerForConicProgramEvents(this);
	}

	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}
	
	private void verifyCheckedOut() {
		if (!checkedOut)
			throw new IllegalAccessError("Matrices are not checked out.");
	}
	
	private void verifyCheckedIn() {
		if (checkedOut)
			throw new IllegalAccessError("Matrices are not checked in.");
	}
	
	public void checkOutMatrices() {
		int varIndex, lcIndex, innerLcIndex;
		boolean isInnerConstraint;
		SparseDoubleMatrix2D temp;
		
		verifyCheckedIn();
		if (!supportsConeTypes(program.getConeTypes()))
			throw new IllegalArgumentException("Program contains unsupported cone type.");
		
		APart = new Vector<SparseCCDoubleMatrix2D>(size());
		innerAPart = new Vector<SparseCCDoubleMatrix2D>(size());
		varElementMap = new int[program.getNumVariables()];
		varIndexMap = new int[program.getNumVariables()];
		varSelections = new int[size()][];
		innerConSelections = new int[size()][];
		
		Map<Variable, Integer> varMapPart = new HashMap<Variable, Integer>();
		Map<LinearConstraint, Integer> lcMapPart = new HashMap<LinearConstraint, Integer>();
		Map<LinearConstraint, Integer> innerLcMapPart = new HashMap<LinearConstraint, Integer>();
		
		Set<Variable> varsToProcess = new HashSet<Variable>();
		
		int total = program.getNumLinearConstraints();
		
		/* Processes each partition element */
		for (int i = 0; i < size(); i++) {
			varIndex = 0;
			lcIndex = 0;
			innerLcIndex = 0;
			
			/* Collects the variables to process */
			for (Cone c : elements.get(i)) {
				if (c instanceof NonNegativeOrthantCone) {
					varsToProcess.add(((NonNegativeOrthantCone) c).getVariable());
				}
				else if (c instanceof SecondOrderCone) {
					varsToProcess.addAll(((SecondOrderCone) c).getVariables());
				}
				else if (c instanceof RotatedSecondOrderCone) {
					varsToProcess.addAll(((RotatedSecondOrderCone) c).getVariables());
				}
				else
					throw new IllegalStateException("Unrecognized cone type.");
			}
				
			/* Processes each variable and incident linear constraints */
			for (Variable v : varsToProcess) {
				varMapPart.put(v, varIndex);
				varElementMap[program.getIndex(v)] = i;
				varIndexMap[program.getIndex(v)] = varIndex++;
				for (LinearConstraint lc : v.getLinearConstraints()) {
					if (!lcMapPart.containsKey(lc)) {
						lcMapPart.put(lc, lcIndex++);
						/* Checks if constraint has been cut */
						isInnerConstraint = true;
						for (Variable v2 : lc.getVariables().keySet())
							isInnerConstraint = isInnerConstraint && elements.get(i).contains(v2.getCone());
						if (isInnerConstraint) {
							innerLcMapPart.put(lc, innerLcIndex++);
						}
					}
				}
			}
			
			/* Constructs A WITH cut constraints for this element */
			temp = new SparseDoubleMatrix2D(lcMapPart.size(), varMapPart.size());
			
			for (Map.Entry<LinearConstraint, Integer> e : lcMapPart.entrySet()) {
				for (Map.Entry<Variable, Double> lcEntry : e.getKey().getVariables().entrySet()) {
					if (varMapPart.containsKey(lcEntry.getKey())) {
						temp.setQuick(e.getValue(), varMapPart.get(lcEntry.getKey()), lcEntry.getValue());
					}
				}
			}
			
			APart.add(temp.getColumnCompressed(false));
			
			/* Constructs A WITHOUT cut constraints for this element */
			temp = new SparseDoubleMatrix2D(innerLcMapPart.size(), varMapPart.size());
			
			for (Map.Entry<LinearConstraint, Integer> e : innerLcMapPart.entrySet()) {
				for (Map.Entry<Variable, Double> lcEntry : e.getKey().getVariables().entrySet()) {
					temp.setQuick(e.getValue(), varMapPart.get(lcEntry.getKey()), lcEntry.getValue());
				}
			}
			
			innerAPart.add(temp.getColumnCompressed(false));
			
			/* Constructs selection arrays */
			varSelections[i] = new int[varMapPart.size()];
			innerConSelections[i] = new int[innerLcMapPart.size()];
			
			for (Map.Entry<Variable, Integer> e : varMapPart.entrySet())
				varSelections[i][e.getValue()] = program.getIndex(e.getKey());
			for (Map.Entry<LinearConstraint, Integer> e : innerLcMapPart.entrySet())
				innerConSelections[i][e.getValue()] = program.getIndex(e.getKey());
			
			/* Cleans up for next iteration */
			total -= innerLcMapPart.size();
			varMapPart.clear();
			lcMapPart.clear();
			innerLcMapPart.clear();
			varsToProcess.clear();
		}
		
		log.debug("Num cut constraints: {}", total);
		
		checkedOut = true;
	}
	
	public void checkInMatrices() {
		verifyCheckedOut();
		checkedOut = false;
	}
	
	public int size() {
		return elements.size();
	}
	
	public void addCone(Cone c, Integer i) {
		verifyCheckedIn();
		
		if (getElement(c) != null)
			throw new IllegalStateException("Cone already belongs to a partition element.");
		elements.get(i).add(c);
		coneMap.put(c, i);
		unassignedCones.remove(c);
		markCutConstraintSetDirty();
	}
	
	public boolean removeCone(Cone c) {
		verifyCheckedIn();
		
		Integer element = coneMap.get(c);
		
		if (element != null) {
			coneMap.remove(c);
			if (elements.get(element).remove(c)) {
				unassignedCones.add(c);
				markCutConstraintSetDirty();
				return true;
			}
			else
				return false;
		}
		else
			return false;
	}
	
	public Set<Cone> getUnassignedCones() {
		// TODO
		throw new UnsupportedOperationException();
	}
	
	public Integer getElement(Cone c) {
		return coneMap.get(c);
	}
	
	public Set<LinearConstraint> getCutConstraints() {
		if (cutConstraintsDirty) {
			Set<Variable> varsToProcess = new HashSet<Variable>();
			
			/* Processes each partition element */
			for (int i = 0; i < size(); i++) {
				/* Collects the variables to process */
				for (Cone c : elements.get(i)) {
					if (c instanceof NonNegativeOrthantCone) {
						varsToProcess.add(((NonNegativeOrthantCone) c).getVariable());
					}
					else if (c instanceof SecondOrderCone) {
						varsToProcess.addAll(((SecondOrderCone) c).getVariables());
					}
					else if (c instanceof RotatedSecondOrderCone) {
						varsToProcess.addAll(((RotatedSecondOrderCone) c).getVariables());
					}
					else
						throw new IllegalStateException("Unrecognized cone type.");
				}
					
				/* Processes each variable and incident linear constraints */
				for (Variable v : varsToProcess) {
					for (LinearConstraint lc : v.getLinearConstraints()) {
						if (!cutConstraints.contains(lc)) {
							/* Checks if constraint has been cut */
							boolean isInnerConstraint = true;
							for (Variable v2 : lc.getVariables().keySet())
								isInnerConstraint = isInnerConstraint && elements.get(i).contains(v2.getCone());
							if (!isInnerConstraint) {
								cutConstraints.add(lc);
							}
						}
					}	
				}
				
				varsToProcess.clear();
			}
			
			cutConstraintsDirty = false;
		}

		return Collections.unmodifiableSet(cutConstraints);
	}
	
	public List<SparseCCDoubleMatrix2D> getACopies() {
		return getConstraintMatrixCopies(APart);
	}
	
	public List<SparseCCDoubleMatrix2D> getInnerACopies() {
		return getConstraintMatrixCopies(innerAPart);
	}
	
	private List<SparseCCDoubleMatrix2D> getConstraintMatrixCopies(List<SparseCCDoubleMatrix2D> matrices) {
		verifyCheckedOut();
		Vector<SparseCCDoubleMatrix2D> list = new Vector<SparseCCDoubleMatrix2D>(matrices.size());
		for (SparseCCDoubleMatrix2D m : matrices)
			list.add((SparseCCDoubleMatrix2D) m.copy());
		return list;
	}
	
	public List<DoubleMatrix1D> get1DViewsByVars(DoubleMatrix1D v) {
		return get1DViews(v, varSelections);
	}
	
	public List<DoubleMatrix1D> get1DViewsByInnerConstraints(DoubleMatrix1D v) {
		return get1DViews(v, innerConSelections);
	}
	
	private List<DoubleMatrix1D> get1DViews(DoubleMatrix1D v, int[][] selections) {
		verifyCheckedOut();
		Vector<DoubleMatrix1D> vectors = new Vector<DoubleMatrix1D>(size());
		for (int[] selection : selections)
			vectors.add(v.viewSelection(selection));
		return vectors;
	}
	
	public List<SparseDoubleMatrix2D> getSparse2DByVars(SparseDoubleMatrix2D m) {
		verifyCheckedOut();
		Vector<SparseDoubleMatrix2D> matrices = new Vector<SparseDoubleMatrix2D>(size());
		for (int i = 0; i < size(); i++)
			matrices.add(new SparseDoubleMatrix2D(APart.get(i).columns(), APart.get(i).columns(), APart.get(i).columns(), 0.2, 0.5));
		updateSparse2DByVars(m, matrices);
		return matrices;
	}
	
	public void updateSparse2DByVars(SparseDoubleMatrix2D m, final List<SparseDoubleMatrix2D> matrices) {
		m.forEachNonZero(new IntIntDoubleFunction() {
			
			@Override
			public double apply(int first, int second, double third) {
				int block = varElementMap[first];
				matrices.get(block).set(varIndexMap[first], varIndexMap[second], third);
				return third;
			}
		});
	}

	@Override
	public void notify(ConicProgram sender, ConicProgramEvent event, Entity entity, Object... data) {
		switch (event) {
		case MatricesCheckedIn:
			verifyCheckedIn();
			break;
		case NNOCCreated:
			unassignedCones.add((Cone) entity);
			break;
		case SOCCreated:
			unassignedCones.add((Cone) entity);
			break;
		case NNOCDeleted:
			removeCone((Cone) entity);
			break;
		case SOCDeleted:
			removeCone((Cone) entity);
			break;
		}
	}
	
	private void markCutConstraintSetDirty() {
		cutConstraintsDirty = true;
	}
}
