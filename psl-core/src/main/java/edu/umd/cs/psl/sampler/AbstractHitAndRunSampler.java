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
package edu.umd.cs.psl.sampler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ujmp.core.Matrix;
import org.ujmp.core.MatrixFactory;
import org.ujmp.core.calculation.Calculation;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.util.FunctionAnalyser;

/**
 * Samples points in a constrained continuous Markov random field, using
 * the "hit-and-run" sampling scheme described in Broecheler and Getoor,
 * "Computing marginal distributions over continuous Markov networks for
 * statistical relational learning." NIPS 2010.
 * 
 * Subclasses of this abstract class should implement specific uses of the
 * sampled points.
 */
abstract public class AbstractHitAndRunSampler implements Sampler {

	private static final Logger log = LoggerFactory.getLogger(AbstractHitAndRunSampler.class);

	public static final int defaultMaxNoSteps = 1000000;
	public static final int defaultSignificantDigits = 4;
	public static final double defaultBurnInStepsPercentage=0.01;
	
	private final double epsilon = 1e-4;
	private final double errorEpsilon = 0.01;
	private final int maxDimension2Display=10;
	
	private final int maxActiveConstraints=2;
	
	private final long roundingScheme;
	private final int maxSteps;
	
	private int noSteps;
	private int noSamples;
	private int dimensions;
	
	private transient Map<AtomFunctionVariable,Integer> atomIndex;
	
	protected transient Matrix currentPt;
	
	private final transient HitAndRunSamplerStatistics stats;
	
	public AbstractHitAndRunSampler() {
		this(defaultSignificantDigits);
	}
	
	public AbstractHitAndRunSampler( int maxNoSteps) {
		this(maxNoSteps, defaultSignificantDigits);
	}
 	
	public AbstractHitAndRunSampler(int maxNoSteps, int significantDigits) {
		noSteps=0;
		noSamples=0;
		dimensions=0;
		atomIndex =new HashMap<AtomFunctionVariable,Integer>();
		currentPt=null;
		
		roundingScheme= (long)Math.pow(10, significantDigits);
		maxSteps=maxNoSteps;
		stats = new HitAndRunSamplerStatistics(this);
	}
	
	public HitAndRunSamplerStatistics getStatistics() {
		return stats;
	}
	
	public int getNoSamples() {
		return noSamples;
	}
	
	protected int getorSetIndex(AtomFunctionVariable atomvar) {
		if (atomvar.isConstant()) throw new IllegalArgumentException("Cannot retrieve index for known atom!");
		Integer index = atomIndex.get(atomvar);
		if (index==null) {
			index = dimensions;
			dimensions++;
			atomIndex.put(atomvar, index);
			processNewDimension(atomvar, index);
		}
		return index.intValue();
	}
	
	protected int getIndex(AtomFunctionVariable atomvar) {
		if (atomvar.isConstant()) throw new IllegalArgumentException("Cannot retrieve index for known atom!");
		Integer index= atomIndex.get(atomvar);
		if (index==null) throw new IllegalArgumentException("Atom has not yet been assigned a dimension!");
		return index;
	}
	
	abstract protected void processNewDimension(AtomFunctionVariable var, int index);
	
	abstract protected double sampleAlpha(Matrix direction, Matrix Aobj, Matrix objConst, double alphaLow, double alphaHigh);
	
	abstract protected void processSampledPoint(Iterable<GroundKernel> groundKernels);
	
	public void sample(Iterable<GroundKernel> evidences, double activationThreshold, int activatorThreshold) {
		
		//Check dimensionality and inputs
		int noEqConstraints = 0;
		int noIneqConstraints = 0;
		int noObjectiveFuns = 0;
		
		for (GroundKernel e : evidences) {
			if (e instanceof GroundCompatibilityKernel) {
				noObjectiveFuns++;
			} else if (e instanceof GroundConstraintKernel) {
				ConstraintTerm con = ((GroundConstraintKernel)e).getConstraintDefinition();
				switch(con.getComparator()) {
				case Equality:
					noEqConstraints++;
					break;
				case LargerThan:
				case SmallerThan:
					noIneqConstraints++;
					break;
				default: throw new AssertionError("Unknown comparator type: " + con.getComparator());
				}				
			} else throw new AssertionError("Unknown evidence type: "  +e);
			//Determine dimensionality
			for (GroundAtom a : e.getAtoms()) {
				if (a instanceof RandomVariableAtom)
					getorSetIndex(a.getVariable());
			}
		}
		log.debug("Dimesions: {}",dimensions);
		
		Matrix Aeq = MatrixFactory.sparse(noEqConstraints,dimensions);
		//Aeq.showGUI();
		Matrix beq = MatrixFactory.dense(noEqConstraints,1);
		log.trace("Equality Constraints: {}",noEqConstraints);
		
		Matrix Aineq = MatrixFactory.sparse(noIneqConstraints+2*dimensions,dimensions);
		Matrix bsmaller = MatrixFactory.dense(noIneqConstraints+2*dimensions,1);
		log.trace("Inequality Constraints: {}",noIneqConstraints);
		
		Matrix Aobj = MatrixFactory.sparse(noObjectiveFuns,dimensions);
		Matrix objConst = MatrixFactory.dense(noObjectiveFuns,1);
		log.trace("Objective Funs: {}",noObjectiveFuns);
		
		//Construct equality and inequality matrix
		int eqCount=0, ineqCount=0, objCount=0;
		for (GroundKernel e : evidences) {
			if (e instanceof GroundCompatibilityKernel) {
				GroundCompatibilityKernel ev = (GroundCompatibilityKernel)e;
				FunctionTerm corefun = FunctionAnalyser.getCoreObjectiveFunction(ev.getFunctionDefinition());
				if (corefun==null) corefun = ev.getFunctionDefinition();//throw new AssertionError("Expected standard evidence form: " + ev);
				if (!corefun.isLinear()) throw new AssertionError("Expected linear probabilistic evidence only, but got: " + ev);
				double constant = setMatrixRow(Aobj,objCount,corefun,false);
				objConst.setAsDouble(constant, objCount, 0);
				objCount++;				
			} else if (e instanceof GroundConstraintKernel) {
				GroundConstraintKernel ev = (GroundConstraintKernel)e;
				ConstraintTerm con = ev.getConstraintDefinition();
				if (!con.getFunction().isLinear()) throw new AssertionError("Expected linear constraints only, but got: " + ev);
				double value = con.getValue();
				boolean negate=false;
				double constant;
				switch(con.getComparator()) {
				case Equality:
					constant = setMatrixRow(Aeq,eqCount,con.getFunction(),negate);
					beq.setAsDouble(value-constant, eqCount, 0);
					eqCount++;
					break;
				case LargerThan:
					negate=true;
					value = -value;
				case SmallerThan:
					constant = setMatrixRow(Aineq,ineqCount,con.getFunction(),negate);
					bsmaller.setAsDouble(value-constant, ineqCount, 0);
					ineqCount++;
					break;
				default: throw new AssertionError("Unknown comparator type: " + con.getComparator());
				}
			}
		}
		assert eqCount==noEqConstraints;
		assert ineqCount == noIneqConstraints;
		assert objCount==noObjectiveFuns;
		
		//Add [0,1] bounds to atom truth values
		for (int i=0;i<dimensions;i++) {
			Aineq.setAsDouble(1, ineqCount, i);
			bsmaller.setAsDouble(1, ineqCount,0);
			
			Aineq.setAsDouble(-1, ineqCount+1, i);
			bsmaller.setAsDouble(0, ineqCount+1,0);
			ineqCount+=2;
		}
		
		//Starting point
		if (currentPt==null) {
			currentPt = MatrixFactory.sparse(dimensions,1);
			for (Map.Entry<AtomFunctionVariable, Integer> entry : atomIndex.entrySet()) {
				AtomFunctionVariable atomvar = entry.getKey();
				currentPt.setAsDouble(atomvar.getValue(), entry.getValue(),0);
			}
		} else if (currentPt!=null) {
			//Adjust in length if necessary
			if (currentPt.getSize(1)!=dimensions) {
				assert currentPt.getSize(1)<dimensions;
				currentPt.setSize(dimensions,1);
			}
		} //else throw new IllegalArgumentException("Need to specify a starting point!");
		
		//Determine projection based on equality matrix. Note that we assume that Aeq has full rank!
	    Matrix projection;
	    if (noEqConstraints==0) projection = MatrixFactory.eye(dimensions,dimensions);
	    else {
	    	//QR decomposition to determine the projection
	    	log.trace("Aeq {}",Arrays.toString(Aeq.getSize()));
	    	//log.debug("Aeq' {}",Arrays.toString(Aeq.transpose().getSize()));
	    	Matrix[] svd = Aeq.svd();
	    	log.trace("U {}",Arrays.toString(svd[0].getSize()));
	    	log.trace("o {}",Arrays.toString(svd[1].getSize()));
	    	log.trace("V' {}",Arrays.toString(svd[2].getSize()));
	    	if (dimensions<maxDimension2Display) {
	    	log.trace("U \n{}",svd[0]);
	    	log.trace("o \n{}",svd[1]);
	    	log.trace("V' \n{}",svd[2]);
	    	}
	    	assert svd[2].getSize(0)==dimensions && svd[2].getSize(1)==dimensions;
	    	if (dimensions<maxDimension2Display) log.trace("SVD: \n{}",svd[0].mtimes(svd[1]).mtimes(svd[2].transpose()));
	    	
	    	//Determine rank
	    	int eqRank = 0;
	    	while (eqRank<noEqConstraints && svd[1].getAsDouble(eqRank,eqRank)!=0.0) eqRank++; //TODO: is checking for 0.0 ok here, or rather |x|<epsilon?
	    	log.trace("Rank of Aeq {}",eqRank);
	    	projection = svd[2].subMatrix(Calculation.Ret.NEW, 0, eqRank, dimensions-1, dimensions-1);
	    }
    	if (dimensions<maxDimension2Display) log.trace("P {}",Arrays.toString(projection.getSize()));
	    long reducedDim = projection.getSize(1);
	    //assert reducedDim == dimensions-noEqConstraints : reducedDim + " vs. " + dimensions + " - " + noEqConstraints;

    	if (dimensions<maxDimension2Display) {
	    log.trace("Aobj \n{}",Aobj);
	    log.trace("objConstant \n{}",objConst);
	    log.trace("Aeq \n{}",Aeq);
	    log.trace("P \n{}",projection);
	    log.trace("Aineq \n{}",Aineq);
	    log.trace("bsmaller \n{}",bsmaller);
    	}
    	
    	stats.finishedSetup(noEqConstraints, noIneqConstraints, noObjectiveFuns, dimensions, (int)reducedDim);
	    
	    //int maxNumberSteps = Math.min(maxSteps, (int)(stepFactor*Math.pow(reducedDim, 2.0)) );
	    int maxNumberSteps = maxSteps;
	    log.debug("Number of steps to take: {}",maxNumberSteps);
	    
	    //Sampling
	    HashSet<Atom> activatedAtoms = new HashSet<Atom>();
	    List<Integer> activeConstraints = new ArrayList<Integer>();
	    Random dimGenerator = new Random();
	    boolean inCorner = false;
	    do {
	    	if ((noSteps+1)%10000==0) log.debug("Starting step #{}",noSteps+1);
	    	else log.trace("Starting step #{}",noSteps+1);
	    	//Generate random direction
		    Matrix direction;
		    
		    if (inCorner) {
		    	stats.inCorner();
		    	log.debug("Has been cornered with {} active constraints on step {}",activeConstraints.size(),noSteps);
		    	assert activeConstraints.size()>0 : activeConstraints.size();
		    	inCorner=false;
		    	//Special direction sampling to avoid getting stuck in a corner

		    	int noActiveCons = activeConstraints.size();
		    	double lambdaUpdate = 2.0;
		    	//Initialize direction
		    	direction = MatrixFactory.zeros(reducedDim,1);
		    	//Project active inequality constraints based on the dimension reduction from the equality constraints
		    	Matrix Aineqreduced = Aineq.selectRows(Calculation.Ret.NEW, activeConstraints).mtimes(projection);
		    	//Set the bounds on the hyperplanes
		    	
		    	Matrix smaller = MatrixFactory.dense(noActiveCons,1);
		    	for (int i=0;i<noActiveCons;i++) smaller.setAsDouble(-Math.abs(dimGenerator.nextDouble()), i,0);
		    	//Now apply the iterative relaxation method for linear inequalities
		    	int mostViolatedCons;
		    	int iterativeDirectionUpdates=0;
		    	
	    		//Compute row norms
	    		double[] norms = new double[noActiveCons];
	    		log.trace("Compute norms");
	    		for (long[] pos : Aineqreduced.availableCoordinates()) {
	    			norms[(int)pos[0]] += Math.pow(Aineqreduced.getAsDouble(pos),2.0);
	    		}
	    		
	    		for (int i=0;i<noActiveCons;i++) {
	    			norms[i] = Math.sqrt(norms[i]);
	    		}
	    		
		    	do {
		    		mostViolatedCons=-1;
		    		double violationMagnitude = 0.0;
		    		//Find most violated constraint
		    		log.trace("Compute violation matrix");
		    		Matrix violation = Aineqreduced.mtimes(Calculation.Ret.NEW,false,direction).minus(Calculation.Ret.ORIG,false,smaller);
		    		log.trace("Find most violated");
		    		for (int i=0;i<noActiveCons;i++) {
		    			double v = violation.getAsDouble(i,0)/norms[i];
		    			if (v >violationMagnitude) {
		    				mostViolatedCons=i;
		    				violationMagnitude=v;
		    			}
		    		}
		    		
		    		if (mostViolatedCons>=0) {
		    			log.trace("Most violated {}",mostViolatedCons);
		    			//Update current direction
		    			double factor = lambdaUpdate*(-violation.getAsDouble(mostViolatedCons,0))/Math.pow(norms[mostViolatedCons],2);
		    			direction = direction.plus(Calculation.Ret.ORIG,false,
		    						Aineqreduced.selectRows(Calculation.Ret.LINK, mostViolatedCons).transpose().times(Calculation.Ret.ORIG,false, factor));
		    		}
		    		iterativeDirectionUpdates++;
		    		//if (iterativeDirectionUpdates%100==0) log.debug("Number of iterative direction updates {}",iterativeDirectionUpdates);
		    	} while (mostViolatedCons>=0);
		    	log.debug("Iterative direction steps until convergence: {}",iterativeDirectionUpdates);
		    	assert Aineq.selectRows(Calculation.Ret.NEW, activeConstraints).mtimes(projection.mtimes(direction)).getMaxValue() < 0 :
		    		Aineq.selectRows(Calculation.Ret.NEW, activeConstraints).mtimes(projection.mtimes(direction)).getMaxValue();
		    	stats.outCorner();
		    } else {
			    //Sample direction u.a.r from ball
		    	direction = MatrixFactory.dense(reducedDim,1);
		    	for (int i=0;i<reducedDim;i++) direction.setAsDouble(dimGenerator.nextGaussian(), i,0);
		    }
		    double dnorm = 0.0;
    		for (int i=0;i<reducedDim;i++) dnorm += Math.pow(direction.getAsDouble(i,0),2.0);
    		dnorm = 1.0/Math.sqrt(dnorm);
			direction.times(Calculation.Ret.ORIG,false,dnorm);
	    	if (dimensions<maxDimension2Display) log.trace("Direction \n{}",direction);
		    direction = projection.mtimes(direction);
		    //log.trace("Direction projected \n{}",direction);

		    
		    Matrix AineqD = Aineq.mtimes(direction);
		    Matrix AineqP = Aineq.mtimes(currentPt);
		    activeConstraints.clear();
		    double alphaLow = Double.NEGATIVE_INFINITY;
		    double alphaHigh = Double.POSITIVE_INFINITY;
		    for (int i=0;i<noIneqConstraints+2*dimensions;i++) {
		    	double dval = AineqD.getAsDouble(i,0);
		    	double currentalpha;
		    	boolean isActive=false;
		    	if (dval>0) {
		    		currentalpha = (bsmaller.getAsDouble(i,0)-AineqP.getAsDouble(i,0))/dval;
		    		alphaHigh = Math.min(alphaHigh, currentalpha);
		    		if (currentalpha<errorEpsilon) {
		    			isActive=true;
		    			//log.debug("Active high at {} with\n{}"+bsmaller.getAsDouble(i,0),currentalpha,Aineq.selectRows(Calculation.Ret.LINK, i));
		    		}
		    	} else if (dval<0) {
		    		currentalpha = (bsmaller.getAsDouble(i,0)-AineqP.getAsDouble(i,0))/dval;
		    		alphaLow = Math.max(alphaLow, currentalpha);
		    		if (currentalpha>-errorEpsilon) {
		    			isActive=true;
		    			//log.debug("Active low at {} with\n{}"+bsmaller.getAsDouble(i,0),currentalpha,Aineq.selectRows(Calculation.Ret.LINK, i));
		    		}
		    	}
		    	if (isActive) activeConstraints.add(i);
		    }
		    assert alphaHigh>-errorEpsilon : alphaHigh + "\n" + currentPt.toString();
		    assert alphaLow<errorEpsilon : alphaLow + "\n" + currentPt.toString();
		    AineqD=null;
		    AineqP=null;
		    
		    //log.debug("Alpha Interval [ {} , {} ]",alphaLow, alphaHigh);
		    
		    //Check alpha interval and active constraints
		    if ((alphaHigh-alphaLow)<epsilon) {
		    	//skip this step and sample direction differently
		    	if (activeConstraints.size()>maxActiveConstraints) 
		    		inCorner = true;
		    	continue;
		    }
		    
		    double alphaPt = sampleAlpha(direction, Aobj, objConst, alphaLow, alphaHigh);
		    
		    currentPt = currentPt.plus(Calculation.Ret.ORIG,false,direction.times(Calculation.Ret.ORIG,false,alphaPt));
		    log.trace("New Point \n{}",currentPt);
		    
		    noSteps++;
		    //Add sample if we are beyond the burn in phase
		    if (noSteps>defaultBurnInStepsPercentage*maxNumberSteps) {
		    	for (Map.Entry<AtomFunctionVariable, Integer> entry  : atomIndex.entrySet()) {
		    		AtomFunctionVariable a = entry.getKey();
		    		double value = ((double)Math.round(currentPt.getAsDouble(getIndex(a),0)*roundingScheme)) / roundingScheme;
		    		a.setValue(value);
		    		
		    		//Activation
//		    		GroundAtom atom = a.getAtom();
//		    		if (atom.isRandomVariable() && !atom.isActive() && atom.hasNonDefaultValues()) {
//		    			activatedAtoms.add(atom);
//		    		}
		    	}
		    	
	    		processSampledPoint(evidences);
		    	noSamples++;
		    }
	    } while (noSteps<maxNumberSteps && activatedAtoms.size()<activatorThreshold);
	    stats.finish(noSamples);
//	    return activatedAtoms;
	}
	
	private double setMatrixRow(Matrix m, int row, FunctionTerm term,boolean negate) {
		double constant = 0.0;
		if (term instanceof FunctionSummand) constant = setMatrixCell(m,row,(FunctionSummand)term,negate);
		else {
			if (!(term instanceof FunctionSum)) throw new IllegalArgumentException("Expected sum but was given: " + term);
			FunctionSum sum = (FunctionSum)term;
			for (FunctionSummand summand : sum) {
				constant += setMatrixCell(m,row,summand,negate);
			}
		}
		return negate?-constant:constant;
	}
	
	private double setMatrixCell(Matrix m, int row, FunctionSummand summand,boolean negate) {
		if (summand.isConstant()) return summand.getValue();
		else {
			//Add entry to matrix
			if (!(summand.getTerm() instanceof AtomFunctionVariable)) throw new IllegalArgumentException("Expected sum of simple variables but got: " + summand);
			int col = getIndex((AtomFunctionVariable)summand.getTerm());
			double val = summand.getCoefficient();
			m.setAsDouble(negate?-val:val, row, col);
			return 0.0;
		}
	}
	
}
