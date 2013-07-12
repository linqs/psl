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
package edu.umd.cs.psl.groovy;
import edu.umd.cs.psl.model.set.aggregator.AggregatorFunction;
import edu.umd.cs.psl.ui.aggregators.AggregateSetAverage;
import edu.umd.cs.psl.ui.aggregators.AggregateSetCrossEquality;
import edu.umd.cs.psl.ui.aggregators.AggregateSetEquality;
import edu.umd.cs.psl.ui.aggregators.AggregateSetInverseAverage;

public enum SetComparison {

	Equality {

		@Override
		public AggregatorFunction getAggregator() {
			return new AggregateSetEquality();
		}
		
	},
	
	CrossEquality {
		@Override
		public AggregatorFunction getAggregator() {
			return new AggregateSetCrossEquality();
		}
	},
	
	/** Average over first argument */
	Average {
		@Override
		public AggregatorFunction getAggregator() {
			return new AggregateSetAverage();
		}
	},
	
	/** Average over second argument */
	InverseAverage {
		@Override
		public AggregatorFunction getAggregator() {
			return new AggregateSetInverseAverage();
		}
	};
	
	public abstract AggregatorFunction getAggregator();
	
}
