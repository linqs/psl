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
package edu.umd.cs.psl.factorgraph.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class FactorGraphHelper {

	@SuppressWarnings("unchecked")
	public static<T,U,W> Collection<Set<T>> filterClusters(Collection<Set<U>> clusters, Class<T> type, Map<U,W> map) {
		List<Set<T>> result = new ArrayList<Set<T>>(clusters.size());
		for (Set<U> cluster : clusters) {
			Set<T> newset = new HashSet<T>();
			for (U v : cluster) {
				W obj = map.get(v);
				if (obj==null) continue;
				if (type.isInstance(obj)) newset.add((T)obj);
			}
			if (!newset.isEmpty()) result.add(newset);
		}
		return result;
	}
	
}
