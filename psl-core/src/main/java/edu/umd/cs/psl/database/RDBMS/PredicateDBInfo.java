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
package edu.umd.cs.psl.database.RDBMS;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.DataFormat;
import edu.umd.cs.psl.database.PredicateDBType;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

class PredicateDBInfo {
	
	final Predicate predicate;
	final String[] argColumns;
	final PredicateDBType type;
	final String tableName;
	final String pslcol;
	final String[] valuecols;
	final String[] confidencecols;
	final DataFormat[] columnTypes;
	final String partitioncol;
	
	PredicateDBInfo(Predicate def, String[] args, PredicateDBType t, String table, String psl, String[] values, 
			String[] confidences, String partition, DataFormat[] coltypes) {
		predicate = def;
		argColumns = args;
		type = t;
		tableName = table;
		pslcol = psl;
		valuecols = values;
		confidencecols = confidences;
		partitioncol = partition;
		columnTypes = coltypes;
		
		Preconditions.checkArgument(predicate instanceof StandardPredicate);
		Preconditions.checkNotNull(valuecols);
		Preconditions.checkNotNull(confidencecols);
		Preconditions.checkNotNull(predicate);
		Preconditions.checkNotNull(argColumns);
		Preconditions.checkNotNull(type);
		Preconditions.checkNotNull(tableName);
		Preconditions.checkNotNull(partitioncol);
		Preconditions.checkNotNull(columnTypes);
		if (argColumns.length!=predicate.getArity()) throw new IllegalArgumentException("Number of predicate argument names must match its arity!");
		if (columnTypes.length!=predicate.getArity())  throw new IllegalArgumentException("Number of predicate argument names must match its arity!");
	}
	
	RDBMSPredicateHandle getPredicateHandle(boolean toclose) {
		boolean isclosed;
		switch (type) {
			case Open:
				if (toclose) isclosed=true;
				else isclosed = false;
				break;
			case Closed:
				isclosed=true;
				if (toclose) throw new IllegalArgumentException("Cannot close already closed predicate: " + predicate);
				break;
			case Aggregate:
				isclosed=false;
				if (toclose) throw new IllegalArgumentException("Cannot close auxiliary predicate: " + predicate);
				break;
			default: throw new AssertionError("Unknown case encountered!");
		}
		return new InternalRDBMSPredicateHandle(isclosed);
//		return new RDBMSPredicateHandle(predinfo.predicate,predinfo.tableName,predinfo.argColumns,isclosed,predinfo.partitioncol,predinfo.valuecols,predinfo.confidencecols,predinfo.pslcol);
	}
	
	private class InternalRDBMSPredicateHandle implements RDBMSPredicateHandle {
		
		private final boolean isClosed;
		
		private InternalRDBMSPredicateHandle(boolean isClosed) {
			this.isClosed=isClosed;
			if (!isClosed) {
				Preconditions.checkNotNull(pslcol);
			}
		}
		
		@Override
		public boolean hasSoftValues() {
			return valuecols.length>0;
		}
		
		@Override
		public boolean hasConfidenceValues() {
			return confidencecols.length>0;
		}

		@Override
		public String[] argumentColumns() {
			return argColumns;
		}

		@Override
		public boolean isClosed() {
			return isClosed;
		}

		@Override
		public String[] confidenceColumns() {
			return confidencecols;
		}

		@Override
		public String partitionColumn() {
			return partitioncol;
		}

		@Override
		public String pslColumn() {
			return pslcol;
		}

		@Override
		public String tableName() {
			return tableName;
		}

		@Override
		public String[] valueColumns() {
			return valuecols;
		}

		@Override
		public Predicate predicate() {
			return predicate;
		}
		
	}
	
}
