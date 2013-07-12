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
package edu.umd.cs.psl.util.datasplitter.closurestep;

import java.util.Collection;
import java.util.List;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;

/**
 * The closure step follows the splitting step in the process of creating train/test splits.
 * This step retrieves all of the atoms relevant to a given split of the instances and
 * places them in the correct DB partition.
 * 
 * @author blondon
 *
 */
public interface ClosureStep {
	
	void doClosure(Database inputDB, List<Collection<Partition>> partitionList);
	
}
