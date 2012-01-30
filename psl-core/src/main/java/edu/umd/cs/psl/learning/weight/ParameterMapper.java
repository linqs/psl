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
package edu.umd.cs.psl.learning.weight;

import java.util.*;

import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.NegativeWeight;
import edu.umd.cs.psl.model.parameters.Parameters;
import edu.umd.cs.psl.model.parameters.Weight;

public class ParameterMapper {

	private final Map<Kernel,Integer> offset;
	private int numParameters;
	
	public ParameterMapper() {
		offset = new HashMap<Kernel,Integer>();
		numParameters=0;
	}
	
	public int getNumParameters() {
		return numParameters;
	}
	
	public void add(Kernel et) {
		if (!offset.containsKey(et)) {
			offset.put(et, Integer.valueOf(numParameters));
			numParameters += et.getParameters().numParameters();
		}
	}
	
	public double[] getBounds(int parameterNo) {
		for (Map.Entry<Kernel, Integer> entry : offset.entrySet()) {
			int off = entry.getValue();
			Kernel k = entry.getKey();
			if (off<=parameterNo && off+k.getParameters().numParameters()>parameterNo) {
				return k.getParameters().bounds(parameterNo-off);
			}
		}
		throw new IllegalArgumentException("Parameter number is out of bounds!");
	}
	
	public boolean contains(Kernel et) {
		return offset.containsKey(et);
	}
	
	public void setAllParameters(double val) {
		for (Kernel et : offset.keySet()) {
			Parameters para = et.getParameters();
			assert para instanceof Weight;
			double actualval = val;
			if (para instanceof NegativeWeight) actualval = -val;
			for (int i=0;i<para.numParameters();i++) para.setParameter(i, actualval);
			et.setParameters(para);
		}
	}
	
	public void setAllParameters(double[] vals) {
		if (vals.length!=numParameters) throw new IllegalArgumentException("Invalid vector length!");
		for (Kernel et : offset.keySet()) {
			int off = offset.get(et);
			Parameters para = et.getParameters();
			for (int i=0;i<para.numParameters();i++) {
				para.setParameter(i, vals[off+i]);
			}
			et.setParameters(para);
		}
	}
	// CHANGED: This is the third version
	public void setAllParameters2(final double[] vals) {
		double min = 1e100;
		for (int i = 1; i < vals.length; i++)
			if (vals[i] > 1e-4 && vals[i] < min)
				min = vals[i];

		if (vals.length != numParameters + 1)
			throw new IllegalArgumentException("Invalid vector length!");
		for (Kernel et : offset.keySet()) {
			int off = offset.get(et);
			Parameters para = et.getParameters();
			for (int i = 0; i < para.numParameters(); i++) {
				para.setParameter(i, vals[off + i + 1] / min);
				//System.out.println("param " + off + "," + i + " = "
				//		+ vals[off + i + 1] / min);
			}
			et.setParameters(para);
		}
	}
	
	public void setAllParametersWithOffset(double[] values) {
		if (values.length!=numParameters+1) throw new IllegalArgumentException("Invalid vector length!");
		for (Kernel et : offset.keySet()) {
			int off = offset.get(et);
			Parameters para = et.getParameters();
			for (int i=0;i<para.numParameters();i++) {
				para.setParameter(i, values[off+i+1]);
			}
			et.setParameters(para);
		}
	}
	
	public Map<Kernel, Integer> getOffsets() {
		return Collections.unmodifiableMap(offset);
	}

	public double[] getAllParameterValues() {
		double vals[] = new double[numParameters];
		for (Kernel et : offset.keySet()) {
			int off = offset.get(et);
			Parameters para = et.getParameters();
			for (int i=0;i<para.numParameters();i++) {
				vals[off+i]=para.getParameter(i);
			}
		}
		return vals;
	}
	
	public void add2ArrayValue(Kernel et, int paraNo, double[] arr, double val) {
		assert paraNo>=0 && paraNo<et.getParameters().numParameters();
		assert offset.containsKey(et);
		arr[offset.get(et)+paraNo]+=val;
	}
	
	public void add2ArrayValue(Kernel et, int paraNo1, int paraNo2, double[][] arr, double val) {
		assert paraNo1>=0 && paraNo1<et.getParameters().numParameters();
		assert paraNo2>=0 && paraNo2<et.getParameters().numParameters();
		assert offset.containsKey(et);
		int off = offset.get(et);
		arr[off+paraNo1][off+paraNo2]+=val;
	}
}
