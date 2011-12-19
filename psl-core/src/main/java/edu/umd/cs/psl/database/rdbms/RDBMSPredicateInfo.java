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
package edu.umd.cs.psl.database.rdbms;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.DataFormat;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

class RDBMSPredicateInfo {
	
	final Predicate predicate;
	final String[] argCols;
	final String tableName;
	final String pslCol;
	final String valueCol;
	final String confidenceCol;
	final DataFormat[] columnTypes;
	final String partitionCol;
	
	RDBMSPredicateInfo(Predicate def, String[] args, String table, String psl, String value, 
			String confidence, String partition, DataFormat[] coltypes) {
		predicate = def;
		argCols = args;
		tableName = table;
		pslCol = psl;
		valueCol = value;
		confidenceCol = confidence;
		partitionCol = partition;
		columnTypes = coltypes;
		
		Preconditions.checkArgument(predicate instanceof StandardPredicate);
		Preconditions.checkNotNull(valueCol);
		Preconditions.checkNotNull(confidenceCol);
		Preconditions.checkNotNull(predicate);
		Preconditions.checkNotNull(argCols);
		Preconditions.checkNotNull(tableName);
		Preconditions.checkNotNull(partitionCol);
		Preconditions.checkNotNull(columnTypes);
		if (argCols.length!=predicate.getArity()) throw new IllegalArgumentException("Number of predicate argument names must match its arity!");
		if (columnTypes.length!=predicate.getArity())  throw new IllegalArgumentException("Number of predicate argument names must match its arity!");
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
		public String pslColumn() {
			return pslCol;
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
