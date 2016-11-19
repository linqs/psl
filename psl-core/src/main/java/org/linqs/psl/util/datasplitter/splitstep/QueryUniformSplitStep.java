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
package org.linqs.psl.util.datasplitter.splitstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

public class QueryUniformSplitStep implements SplitStep {
	private Iterable<QueryAtom> targets;
	private int numFolds;
	private Variable groupBy;

	/**
	 * Constructs a SplitStep that defines all instances with a set of QueryAtoms and groups by variables
	 * @param targets Collection of queries defining the instances. Each returned atom from the query is an instance
	 * @param numFolds number of folds to split into
	 * @param groupBy variable that all instances should be grouped by
	 */
	public QueryUniformSplitStep(Iterable<QueryAtom> targets, int numFolds, Variable groupBy) {
		this.targets= targets;
		this.numFolds = numFolds;
		this.groupBy = groupBy;
	}

	@Override
	public List<Collection<Partition>> getSplits(Database inputDB, Random random) {
		Map<Constant, Set<GroundAtom>> groupMap = new HashMap<Constant, Set<GroundAtom>>();		
		Collection<Set<GroundAtom>> groups;

		List<Collection<Partition>> splits = new ArrayList<Collection<Partition>>();

		for (QueryAtom query : targets) {
			DatabaseQuery dbQuery = new DatabaseQuery(query);
			ResultList results = inputDB.executeQuery(dbQuery);
			Predicate predicate = query.getPredicate();
			int groupIndex = dbQuery.getVariableIndex(groupBy);

			for (int i = 0; i < results.size(); i++) {
				GroundAtom atom = inputDB.getAtom(predicate, results.get(i));

				// group atoms
				Constant key = atom.getArguments()[groupIndex];
				if (groupMap.get(key) == null) {
					groupMap.put(key, new TreeSet<GroundAtom>());
				}
				groupMap.get(key).add(atom);
			}
		}
		groups = groupMap.values();


		List<Partition> allPartitions = new ArrayList<Partition>();
		for (int i = 0; i < numFolds; i++) {
			Partition nextPartition = inputDB.getDataStore().getNewPartition(); 
			allPartitions.add(nextPartition);
		}

		Map<Predicate, List<Inserter>> inserters = new HashMap<Predicate, List<Inserter>>();
		for (QueryAtom query : targets) {
			if (inserters.containsKey(query.getPredicate()))
				continue;

			List<Inserter> predicateInserters = new ArrayList<Inserter>(numFolds);
			for (int i = 0; i < numFolds; i++) 
				predicateInserters.add(inputDB.getDataStore().getInserter((StandardPredicate) query.getPredicate(), allPartitions.get(i)));
			inserters.put(query.getPredicate(), predicateInserters);	
		}

		insertIntoPartitions(groups, inserters, random); 

		for (int i = 0; i < numFolds; i++) {
			Set<Partition> partitions = new TreeSet<Partition>();
			for (int j = 0; j < numFolds; j++) 
				if (j != i)
					partitions.add(allPartitions.get(j));
			splits.add(partitions);
		}

		return splits;
	}


	private void insertIntoPartitions(Collection<Set<GroundAtom>> groups, 
			Map<Predicate, List<Inserter>> inserters, Random random) {

		ArrayList<Set<GroundAtom>> groupList = new ArrayList<Set<GroundAtom>>(groups.size());
		groupList.addAll(groups);
		Collections.shuffle(groupList, random);

		int j = 0;
		for (Set<GroundAtom> group : groupList) {
			for (GroundAtom atom : group)
				inserters.get((StandardPredicate) atom.getPredicate()).get(j % numFolds).insertValue(
						atom.getValue(), (Object []) atom.getArguments());
			j++;
		}
	}

}
