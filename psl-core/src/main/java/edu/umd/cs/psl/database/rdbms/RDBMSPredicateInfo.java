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

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.model.predicate.Predicate;

class RDBMSPredicateInfo {
	
	final Predicate predicate;
	final String[] argCols;
	final String tableName;
	final String valueCol;
	final String confidenceCol;
	final String partitionCol;
	
	RDBMSPredicateInfo(Predicate def, String[] args, String table, String value, 
			String confidence, String partition) {
		predicate = def;
		argCols = args;
		tableName = table + "_predicate";
		valueCol = value;
		confidenceCol = confidence;
		partitionCol = partition;
		
		Preconditions.checkNotNull(valueCol);
		Preconditions.checkNotNull(confidenceCol);
		Preconditions.checkNotNull(predicate);
		Preconditions.checkNotNull(argCols);
		Preconditions.checkNotNull(tableName);
		Preconditions.checkNotNull(partitionCol);
		if (argCols.length!=predicate.getArity()) throw new IllegalArgumentException("Number of predicate argument names must match its arity!");
	}
	
	RDBMSPredicateHandle getPredicateHandle() {
		return new InternalRDBMSPredicateHandle();
	}
	
	private class InternalRDBMSPredicateHandle implements RDBMSPredicateHandle {
		
		private InternalRDBMSPredicateHandle() { }

		@Override
		public String[] argumentColumns() {
			return argCols;
		}

		@Override
		public String valueColumn() {
			return valueCol;
		}

		@Override
		public String confidenceColumn() {
			return confidenceCol;
		}

		@Override
		public String partitionColumn() {
			return partitionCol;
		}

		@Override
		public String tableName() {
			return tableName;
		}

		@Override
		public Predicate predicate() {
			return predicate;
		}
		
	}
	
}
