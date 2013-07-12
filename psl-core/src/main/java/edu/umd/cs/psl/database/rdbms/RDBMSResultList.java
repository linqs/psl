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
package edu.umd.cs.psl.database.rdbms;

import java.util.*;

import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Variable;

public class RDBMSResultList implements ResultList {

	private final Map<Variable,Integer> varPos;
	private final List<GroundTerm[]> results;
	private final int arity;
	
	public RDBMSResultList(int arity) {
		varPos = new HashMap<Variable,Integer>();
		results = new ArrayList<GroundTerm[]>();
		this.arity=arity;
	}
	
	public void addResult(GroundTerm[] res) {
		assert res.length==arity;
		results.add(res);
	}
	
	public void setVariable(Variable var, int pos) {
		if (varPos.containsKey(var)) throw new IllegalArgumentException("Variable has already been set!");
		varPos.put(var, Integer.valueOf(pos));
	}
	
	public final int getPos(Variable var) {
		if (!varPos.containsKey(var)) throw new IllegalArgumentException("Variable ["+var+"] is unknown!");
		return varPos.get(var);
	}

	@Override
	public GroundTerm get(int resultNo, Variable var) {
		return results.get(resultNo)[getPos(var)];
	}
	
	@Override
	public GroundTerm[] get(int resultNo) {
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
		for (GroundTerm[] res : results) {
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
