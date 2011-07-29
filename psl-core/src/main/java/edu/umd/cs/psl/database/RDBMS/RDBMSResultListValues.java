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
package edu.umd.cs.psl.database.RDBMS;

import java.util.*;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.ResultListValues;
import edu.umd.cs.psl.model.argument.GroundTerm;

public class RDBMSResultListValues extends RDBMSResultList implements ResultListValues {

	private final List<double[]> values;
	
	public RDBMSResultListValues(int arity) {
		super(arity);
		values = new ArrayList<double[]>();
	}
	
	@Override
	public void addResult(GroundTerm[] res) {
		throw new UnsupportedOperationException("This method cannot be cold for valued result lists!");
	}
	
	public void addResult(GroundTerm[] res, double[] vals) {
		super.addResult(res);
		values.add(vals);
		assert super.size()==values.size();
	}
	
	@Override
	public double getValue(int resultNo) {
		double[] vals = values.get(resultNo);
		Preconditions.checkArgument(vals.length==1);
		return vals[0];
	}

	@Override
	public double[] getValues(int resultNo) {
		return values.get(resultNo);
	}

}
