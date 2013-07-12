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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.util.concurrent.AtomicDouble;

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
	
	private final LocalProcess process;
	
	HitAndRunSamplerStatistics(AbstractHitAndRunSampler s) {
		process= new LocalProcess(0, true);
		process.setString(descriptionKey, ToStringBuilder.reflectionToString(s,ToStringStyle.MULTI_LINE_STYLE));
		process.setLong(timeInCornersKey, 0);
		process.setLong(noCallBacksKey, 0);
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
	
	private class LocalProcess {

		private final int id;
		private long startTime;
		private long endTime;
		
		private final ConcurrentMap<String,Object> values;
		
		private LocalProcess(int id, boolean start) {
			this.id=id;
			startTime=-1;
			endTime=-1;
			values = new ConcurrentHashMap<String,Object>();
			if (start) start();
		}
		
		private LocalProcess(int id) {
			this(id,false);
		}
		
		public int getID() {
			return id;
		}


		public void start() {
			Preconditions.checkArgument(startTime==-1,"Process has already been started!");	
			startTime=System.currentTimeMillis();
		}
		
		public void terminate() {
			Preconditions.checkArgument(startTime>=0,"Process has not yet been started!");
			Preconditions.checkArgument(endTime==-1,"Process has alreayd been terminated!");	
			endTime=System.currentTimeMillis();	
		}

		public long getCurrentRuntimeMilis() {
			Preconditions.checkArgument(startTime>=0,"Process has not yet been started!");
			Preconditions.checkArgument(endTime==-1,"Process has alreayd been terminated!");	
			return System.currentTimeMillis()-startTime;	
		}

		public long getTotalRuntimeMilis() {
			return endTime-startTime;
		}
		
		public boolean isTerminated() {
			return endTime!=-1;
		}

		public double incrementDouble(String key, double inc) {
			Object o = values.get(key);
			AtomicDouble d = null;
			if (o==null) {
				d = (AtomicDouble)values.putIfAbsent(key, new AtomicDouble());
			} else d = (AtomicDouble)o;
			return d.increment(inc);
		}

		public long incrementLong(String key, long inc) {
			Object o = values.get(key);
			AtomicLong d = null;
			if (o==null) {
				values.putIfAbsent(key, new AtomicLong());
				d = (AtomicLong) values.get(key);
			} else d = (AtomicLong)o;
			return d.addAndGet(inc);	
		}


		public void setDouble(String key, double val) {
			values.put(key, new AtomicDouble(val));
		}

		public void setLong(String key, long val) {
			values.put(key, new AtomicLong(val));	
		}

		public void setObject(String key, Object val) {
			values.put(key, val);
		}

		public void setString(String key, String val) {
			values.put(key, val);
		}

		public double getDouble(String key) {
			Object o = values.get(key);
			if (o==null) return 0.0;
			else {
				return ((AtomicDouble)o).get();
			}
		}

		public long getLong(String key) {
			Object o = values.get(key);
			if (o==null) return 0;
			else {
				return ((AtomicLong)o).get();
			}
		}

		public Object getObject(String key) {
			return values.get(key);
		}


		public String getString(String key) {
			Object o = values.get(key);
			if (o==null) return null;
			else {
				return (String)o;
			}
		}
	}
	
}
