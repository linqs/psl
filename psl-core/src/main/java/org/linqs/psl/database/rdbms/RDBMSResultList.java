/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.database.rdbms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

public class RDBMSResultList implements ResultList {

	private final Map<Variable,Integer> varMap;
	private final List<Constant[]> results;
	private final int arity;
	
	public RDBMSResultList(int arity) {
		varMap = new HashMap<Variable,Integer>();
		results = new ArrayList<Constant[]>();
		this.arity=arity;
	}
	
	public void addResult(Constant[] res) {
		assert res.length==arity;
		results.add(res);
	}
	
	public void setVariable(Variable var, int pos) {
		if (varMap.containsKey(var)) throw new IllegalArgumentException("Variable has already been set!");
		varMap.put(var, Integer.valueOf(pos));
	}

	@Override
	public Map<Variable, Integer> getVariableMap() {
		return Collections.unmodifiableMap(varMap);
	}
	
	public int getPos(Variable var) {
		return varMap.get(var);
	}

	@Override
	public Constant get(int resultNo, Variable var) {
		return results.get(resultNo)[getPos(var)];
	}
	
	@Override
	public Constant[] get(int resultNo) {
		return results.get(resultNo);
	}
	
	@Override
	public int getArity() {
		return arity;
	}
	
	@Override
	public int size() {
		return results.size();
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("Size: ").append(size()).append("\n");
		int len = getArity();
		for (Constant[] res : results) {
			for (int i=0;i<len;i++) {
				if (i>0) s.append(", ");
				s.append(res[i]);
			}
			s.append("\n");
		}
		s.append("-------");
		return s.toString();
	}

}
