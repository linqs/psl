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
package edu.umd.cs.psl.ui.data.file.util;

import java.util.*;

public class ListIntegerConstructor implements DelimitedObjectConstructor<List<Integer>> {

	private final int length;
	
	public ListIntegerConstructor(int len) {
		length = len;
	}
	
	@Override
	public List<Integer> create(String[] data) {
		List<Integer> res = new ArrayList<Integer>(3);
		for (int i=0;i<length;i++) res.add(Integer.parseInt(data[i]));
		return res;
	}

	@Override
	public int length() {
		return length;
	}

	
	
}
