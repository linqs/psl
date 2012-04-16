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
package edu.umd.cs.psl.sampler;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import edu.umd.cs.psl.evaluation.process.ProcessView;
import edu.umd.cs.psl.evaluation.process.RunningProcess;

public class HitAndRunSamplerStatistics {

	public static final String descriptionKey = "description";
	public static final String setupTimeKey = "setupTime";
	public static final String timeInCornersKey = "timeInCorners";
	public static final String noTimesInCornerKey = "noTimesinCorner";
	public static final String noCallBacksKey = "noCallBacks";
	public static final String noEqConsKey = "noEqCons";
	public static final String noIneqConsKey = "noIneqCons";
	public static final String noObjFunKey = "noObj";
	public static final String noreducedDimKey = "ReducedDimensions";
	public static final String noDimensionsKey = "Dimensions";
	public static final String noSamplesKey = "noSamples";
	
	private long startTime;
	private long startInCorner=0;
	private boolean setupComplete=false;
	
	private final RunningProcess process;
	
	HitAndRunSamplerStatistics(AbstractHitAndRunSampler s, RunningProcess proc) {
		process=proc;
		proc.setString(descriptionKey, ToStringBuilder.reflectionToString(s,ToStringStyle.MULTI_LINE_STYLE));
		proc.setLong(timeInCornersKey, 0);
		proc.setLong(noCallBacksKey, 0);
		startTime = System.currentTimeMillis();
	}
	
	void finishedSetup(int eqs, int ineqs, int objfun, int dim, int reducedim) {
		process.setLong(noEqConsKey, eqs);
		process.setLong(noIneqConsKey, ineqs);
		process.setLong(noObjFunKey, objfun);
		process.setLong(noDimensionsKey, dim);
		process.setLong(noreducedDimKey, reducedim);
		if (!setupComplete) {
			process.setLong(setupTimeKey, System.currentTimeMillis()-startTime);
		} else { 
			process.incrementLong(noCallBacksKey, 1);
		}
	}
	
	void inCorner() {
		process.incrementLong(noTimesInCornerKey, 1);
		assert startInCorner == 0;
		startInCorner = System.currentTimeMillis();
	}
	
	void outCorner() {
		process.incrementLong(timeInCornersKey, System.currentTimeMillis()-startInCorner);
		startInCorner = 0;
	}
	
	void finish(int noSamples) {
		process.setLong(noSamplesKey, noSamples);
		
	}
	
	public static String print(ProcessView p) {
		StringBuilder s = new StringBuilder();
		s.append("Linear Sampler Statistics: \n");
		s.append("Dimensions: ").append(p.getLong(noDimensionsKey)).append(" [").append(p.getLong(noreducedDimKey)).append("] \n");
		s.append("EqCons: ").append(p.getLong(noEqConsKey)).append("| IneqCons: ").append(p.getLong(noIneqConsKey)).append("| ObjFun: ").append(p.getLong(noObjFunKey)).append("\n");
		s.append("Number of Samples: ").append(p.getLong(noSamplesKey)).append("\n");
		s.append("Total Time: ").append(p.getTotalRuntimeMilis()).append("\n");
		s.append("Setup Time: ").append(p.getLong(setupTimeKey)).append("\n");
		s.append("Time in Corners: ").append(p.getLong(timeInCornersKey)).append("\n");
		s.append("Number of times in Corners: ").append(p.getLong(noTimesInCornerKey)).append("\n");
		s.append("Number of call backs: ").append(p.getLong(noCallBacksKey)).append("\n");
		s.append("Linear Sampler Configuration: \n");
		s.append(p.getString(descriptionKey));
		
		return s.toString();
	}
	
}
