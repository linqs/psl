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
package edu.umd.cs.psl.evaluation.process;

import edu.umd.cs.psl.evaluation.process.flag.Flag;

public interface RunningProcess extends ProcessView {

	public boolean hasFlag(Flag flag);
	
	public boolean observeFlag(Flag flag);
	
	public void setLong(String key, long val);
	
	public long incrementLong(String key, long inc);
	
	public void setDouble(String key, double val);
	
	public double incrementDouble(String key, double inc);
	
	public void setString(String key, String val);
	
	public void setObject(String key, Object val);
	
	public void start();
	
	public void terminate();
	
}
