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
package edu.umd.cs.psl.database.loading;

public interface Updater {
	
	public void set(Object... data);

	public void setValue(double value, Object... data);
	
	public void setValues(double[] values, Object... data);
	
	public void setValue(double value, double confidence, Object... data);
	
	public void setValues(double[] values, double[] confidences, Object... data);
	
	public void remove(Object... data);
	
}
