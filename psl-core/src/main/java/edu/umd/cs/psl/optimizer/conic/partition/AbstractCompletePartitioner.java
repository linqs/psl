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
package edu.umd.cs.psl.optimizer.conic.partition;

import java.util.Vector;

abstract public class AbstractCompletePartitioner implements CompletePartitioner {
	
	protected Vector<ConicProgramPartition> partitions;
	
	public AbstractCompletePartitioner() {
		partitions = new Vector<ConicProgramPartition>();
	}

	@Override
	abstract public void partition();

	@Override
	public ConicProgramPartition getPartition(int i) {
		return partitions.get(i);
	}

	@Override
	public int size() {
		return partitions.size();
	}

	@Override
	public void checkOutAllMatrices() {
		for (ConicProgramPartition p : partitions)
			p.checkOutMatrices();
	}

	@Override
	public void checkInAllMatrices() {
		for (ConicProgramPartition p : partitions)
			p.checkInMatrices();
	}

}
