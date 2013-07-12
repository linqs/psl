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

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ujmp.core.Matrix;

import cern.colt.list.tlong.LongArrayList;
import cern.colt.list.tobject.ObjectArrayList;
import cern.colt.map.tobject.OpenLongObjectHashMap;

abstract public class LinearSampler extends AbstractHitAndRunSampler {

	private static final Logger log = LoggerFactory.getLogger(LinearSampler.class);

	private final double epsilon = 1e-4;
	
	private final int alphaPlusSignificantDigits = 3;
	
	private final long alphaRoundingScheme;
	
	public LinearSampler(int maxNoSteps, int significantDigits) {
		super(maxNoSteps, significantDigits);
		alphaRoundingScheme= (long)Math.pow(10, significantDigits+alphaPlusSignificantDigits);
	}
	
	@Override
	protected double sampleAlpha(Matrix direction, Matrix Aobj, Matrix objConst, double alphaLow, double alphaHigh) {
		//Construct distribution over the randomly sampled line given by the direction and the bounds [alphaLow, alphaHigh]
	    Matrix AobjD = Aobj.mtimes(direction);
	    Matrix AobjP = Aobj.mtimes(currentPt);
	    
	    OpenLongObjectHashMap alphaMap = new OpenLongObjectHashMap();
	    long alphaLowBin = Math.round(alphaLow*alphaRoundingScheme);
	    ObjFunHandle alphaLowHandle = ObjFunHandle.getHandle(alphaLowBin, alphaMap);

	    for (int i=0; i<Aobj.getRowCount(); i++) {
	    	double rate = AobjD.getAsDouble(i,0);
	    	double constant = AobjP.getAsDouble(i,0)+objConst.getAsDouble(i,0);
	    	double alpha = (-constant)/rate;
	    	long alphaBin = Math.round(alpha*alphaRoundingScheme);
	    	log.trace("Alpha {}",alpha);
	    	log.trace("Rate {} Constant {}",rate,constant);
	    	if (rate>epsilon && alpha<alphaHigh) {
		    	ObjFunHandle h;
	    		if (alpha<=alphaLow) {
	    			//This evidence is active over the entire interval
	    			h = alphaLowHandle;
	    		} else {
	    			//This evidence will get active once we reach alpha in the interval
	    			h = ObjFunHandle.getHandle(alphaBin, alphaMap);
	    		}
	    		h.increaseBy(rate, constant);
	    	} else if (rate<-epsilon && alpha>alphaLow) {
	    		alphaLowHandle.increaseBy(rate, constant);
	    		if (alpha>=alphaHigh) {
	    			//This evidence is active over the entire interval
	    		} else {
	    			//This evidence will get inactive once we reach alpha in the interval
	    			ObjFunHandle h = ObjFunHandle.getHandle(alphaBin, alphaMap);
	    			h.increaseBy(-rate, -constant);		    			
	    		}
	    	} else { //Considered 0
	    		alphaLowHandle.increaseBy(0.0, constant);
	    	}
	    }
	    ObjFunHandle.getHandle(Math.round(alphaHigh*alphaRoundingScheme), alphaMap);
	    AobjD=null;
	    AobjP=null;
	    
	    int length = alphaMap.size();
	    LongArrayList alphaBinsL = new LongArrayList(length);
	    ObjectArrayList handlesL = new ObjectArrayList(length);
	    alphaMap.pairsSortedByKey(alphaBinsL, handlesL);
	    assert alphaBinsL.size()==length && handlesL.size()==length;
	    
	    double[] logcumulative = new double[length];
	    double[] cumRate = new double[length-1];
	    double[] cumConst = new double[length-1];
	    long[] alphaBins = alphaBinsL.elements();
	    Object[] handles = handlesL.elements();
	    
	    log.trace("alphaBins: {}",Arrays.toString(alphaBins));
	    log.trace("handles: {}",Arrays.toString(handles));

	    
	    //Now compute the cumulative probability distribution
	    ObjFunHandle base = (ObjFunHandle)handles[0];
	    double baserate = base.getRate();
	    double baseconstant = base.getConstant();
	    double basealpha = alphaBins[0]/(double)alphaRoundingScheme;
	    double baselogdivisor = baserate*basealpha+baseconstant;
	    
	    logcumulative[0]=0.0;
	    double rate = 0.0;
	    double constant = 0.0;
	    for (int i=1;i<length;i++) {
	    	ObjFunHandle h = (ObjFunHandle)handles[i-1];
	    	rate += h.getRate();
	    	cumRate[i-1]=rate;
	    	constant += h.getConstant();
	    	cumConst[i-1]=constant;
	    	double aL = alphaBins[i-1]/(double)alphaRoundingScheme, aU = alphaBins[i]/(double)alphaRoundingScheme;
	    	double increment;
	    	if (rate<-epsilon || rate>epsilon)
	    		increment = 1.0/rate * ( Math.exp(baselogdivisor - rate*aL-constant) - Math.exp(baselogdivisor - rate*aU-constant) ); 
	    	else
	    		increment = (aU-aL) * Math.exp(baselogdivisor-constant);
	    	assert increment>-epsilon : increment;
	    	logcumulative[i] = logcumulative[i-1]+increment;
	    }
	    double total = logcumulative[length-1];
	    double randomPt = Math.random()*total;
	    log.trace("Random Pt {}",randomPt);
	    
	    log.trace("logcumulative: {}",Arrays.toString(logcumulative));
	    
	    int findIndex = Arrays.binarySearch(logcumulative, randomPt);
	    log.trace("Find Index {}",findIndex);
	    double alphaPt;
	    if (findIndex>=0) alphaPt = alphaBins[findIndex]/(double)alphaRoundingScheme;
	    else {
	    	findIndex = -findIndex - 1;
	    	assert findIndex>0 : findIndex;
	    	double aL = alphaBins[findIndex-1]/(double)alphaRoundingScheme;
	    	final double crate = cumRate[findIndex-1];
	    	final double cconstant = cumConst[findIndex-1];
	    	log.trace("R {}, c {} aL "+aL,crate,cconstant);
	    	if (crate<-epsilon || crate>epsilon) {
	    		double targetValue = - randomPt + logcumulative[findIndex-1] + 1.0/crate * Math.exp(-cconstant - crate*aL+baselogdivisor);
		    	log.trace("Target value {}",targetValue);
		    	log.trace("Log: {}, inside log {}",Math.log(crate*targetValue),crate*targetValue);
		    	alphaPt = -1.0/crate * ( Math.log(crate*targetValue)+ cconstant-baselogdivisor);
	    	} else {
	    		double kcons = Math.exp(baselogdivisor-cconstant);
	    		alphaPt = randomPt/kcons - logcumulative[findIndex-1]/kcons + aL;
	    	}
	    	assert !Double.isNaN(alphaPt) : "C: "+cconstant + " | R: " + crate + " Pt: " + randomPt + " aL: " + aL + "  C0:" + logcumulative[findIndex-1];
	    	
	    }
	    assert alphaPt>=alphaLow-epsilon && alphaPt<=alphaHigh+epsilon : alphaPt;
	    alphaPt = Math.max(alphaPt, alphaLow);
	    alphaPt = Math.min(alphaPt, alphaHigh);
	    log.trace("Sampled alpha {}",alphaPt);
	    
	    return alphaPt;
	}
	
	private static class ObjFunHandle {
		
		private final long alphaBinned;
		private double rate;
		private double constant;
		
		ObjFunHandle(long alphaBin) {
			alphaBinned = alphaBin;
			rate = 0.0;
			constant = 0.0;
		}
		
		double getRate() {
			return rate;
		}
		
		double getConstant() {
			return constant;
		}
		
		void increaseBy(double byrate, double byconstant) {
			rate += byrate;
			constant += byconstant;
		}
		
		public static final ObjFunHandle getHandle(long alphaBin, OpenLongObjectHashMap map) {
			Object h = map.get(alphaBin);
			if (h==null) {
				h = new ObjFunHandle(alphaBin);
				map.put(alphaBin, h);
			}
			return (ObjFunHandle)h;
		}
		
		public String toString() {
			return "{"+alphaBinned+"}: R: " + rate + " C: " + constant;
		}
		
	}
	
}
