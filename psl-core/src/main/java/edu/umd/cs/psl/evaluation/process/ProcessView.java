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

import edu.umd.cs.psl.evaluation.process.flag.*;

public interface ProcessView {
	
	public int getID();
	
	public boolean isTerminated();
	
	public long getTotalRuntimeMilis();
	
	public long getCurrentRuntimeMilis();

	public void setFlag(Flag flag, FlagType type);
	
	public FlagType getFlag(Flag flag);
	
	public void removeFlag(Flag flag);
	
	public long getLong(String key);
	
	public double getDouble(String key);
	
	public String getString(String key);
	
	public Object getObject(String key);
	
	
}
