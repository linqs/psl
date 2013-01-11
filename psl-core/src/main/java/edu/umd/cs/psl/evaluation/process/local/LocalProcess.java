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
package edu.umd.cs.psl.evaluation.process.local;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.evaluation.process.RunningProcess;
import edu.umd.cs.psl.evaluation.process.flag.Flag;
import edu.umd.cs.psl.evaluation.process.flag.FlagType;
import edu.umd.cs.psl.util.concurrent.AtomicDouble;

public class LocalProcess implements RunningProcess {

	private final int id;
	private long startTime;
	private long endTime;
	
	private final ConcurrentMap<String,Object> values;
	private final ConcurrentMap<Flag,FlagType> flags;
	
	public LocalProcess(int id, boolean start) {
		this.id=id;
		startTime=-1;
		endTime=-1;
		values = new ConcurrentHashMap<String,Object>();
		flags = new ConcurrentHashMap<Flag,FlagType>();
		if (start) start();
	}
	
	public LocalProcess(int id) {
		this(id,false);
	}
	
	@Override
	public int getID() {
		return id;
	}


	@Override
	public void start() {
		Preconditions.checkArgument(startTime==-1,"Process has already been started!");	
		startTime=System.currentTimeMillis();
	}
	
	@Override
	public void terminate() {
		Preconditions.checkArgument(startTime>=0,"Process has not yet been started!");
		Preconditions.checkArgument(endTime==-1,"Process has alreayd been terminated!");	
		endTime=System.currentTimeMillis();	
	}

	@Override
	public long getCurrentRuntimeMilis() {
		Preconditions.checkArgument(startTime>=0,"Process has not yet been started!");
		Preconditions.checkArgument(endTime==-1,"Process has alreayd been terminated!");	
		return System.currentTimeMillis()-startTime;	
	}

	@Override
	public long getTotalRuntimeMilis() {
		return endTime-startTime;
	}
	
	@Override
	public boolean isTerminated() {
		return endTime!=-1;
	}

	
	@Override
	public boolean hasFlag(Flag flag) {
		return flags.containsKey(flag);
	}

	@Override
	public boolean observeFlag(Flag flag) {
		FlagType t = flags.get(flag);
		if (t!=null) {
			switch(t) {
			case Permanent:
				return true;
			case Single:
				flags.remove(flag);
				return true;
			default: throw new IllegalStateException("Unexpected flag type: " + t);
			}
		} else return false;
	}
	

	@Override
	public FlagType getFlag(Flag flag) {
		FlagType t = flags.get(flag);
		if (t!=null) {
			return t;
		} else return FlagType.None;
	}


	@Override
	public void removeFlag(Flag flag) {
		flags.remove(flag);
	}

	@Override
	public void setFlag(Flag flag, FlagType type) {
		flags.putIfAbsent(flag, type);
	}

	

	@Override
	public double incrementDouble(String key, double inc) {
		Object o = values.get(key);
		AtomicDouble d = null;
		if (o==null) {
			d = (AtomicDouble)values.putIfAbsent(key, new AtomicDouble());
		} else d = (AtomicDouble)o;
		return d.increment(inc);
	}

	@Override
	public long incrementLong(String key, long inc) {
		Object o = values.get(key);
		AtomicLong d = null;
		if (o==null) {
			values.putIfAbsent(key, new AtomicLong());
			d = (AtomicLong) values.get(key);
		} else d = (AtomicLong)o;
		return d.addAndGet(inc);	
	}


	@Override
	public void setDouble(String key, double val) {
		values.put(key, new AtomicDouble(val));
	}

	@Override
	public void setLong(String key, long val) {
		values.put(key, new AtomicLong(val));	
	}

	@Override
	public void setObject(String key, Object val) {
		values.put(key, val);
	}

	@Override
	public void setString(String key, String val) {
		values.put(key, val);
	}

	@Override
	public double getDouble(String key) {
		Object o = values.get(key);
		if (o==null) return 0.0;
		else {
			return ((AtomicDouble)o).get();
		}
	}

	@Override
	public long getLong(String key) {
		Object o = values.get(key);
		if (o==null) return 0;
		else {
			return ((AtomicLong)o).get();
		}
	}

	@Override
	public Object getObject(String key) {
		return values.get(key);
	}


	@Override
	public String getString(String key) {
		Object o = values.get(key);
		if (o==null) return null;
		else {
			return (String)o;
		}
	}

}
