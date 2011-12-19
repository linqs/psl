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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.database.loading.DataLoader;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.loading.OpenInserter;
import edu.umd.cs.psl.database.partition.PartitionID;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.TruthValues;
import edu.umd.cs.psl.model.predicate.Predicate;

public class RDBMSDataLoader implements DataLoader {
	
	private static final Logger log = LoggerFactory.getLogger(RDBMSDataLoader.class);
	
	private final Connection database;
	
	private final Map<Predicate,RDBMSTableInserter> inserts;
	
	public RDBMSDataLoader(Connection c, Collection<RDBMSPredicateHandle> predicateHandles) {
		database = c;
		inserts = new HashMap<Predicate,RDBMSTableInserter>(predicateHandles.size());
		
		for ( RDBMSPredicateHandle entry : predicateHandles) {
			inserts.put(entry.predicate(), new RDBMSTableInserter(entry));

		}
	}
	
	@Override
	public RDBMSTableInserter getOpenInserter(Predicate p) {
		RDBMSTableInserter ins = inserts.get(p);
		if (ins==null) {
			throw new IllegalArgumentException("Predicate is unkown: "+ p);
		}
		return ins;
	}
	
	@Override
	public Inserter getInserter(Predicate p, Partition partitionID) {
		return new RDBMSInserter(getOpenInserter(p),partitionID);
	}
	
	private class RDBMSInserter implements Inserter {
		
		private final RDBMSTableInserter inserter;
		private final Partition partitionID;
		
		public RDBMSInserter(RDBMSTableInserter ins, Partition pid) {
			Preconditions.checkNotNull(pid);
			inserter = ins;
			partitionID = pid;
		}

		@Override
		public void insert(Object... data) {
			inserter.insert(partitionID, data);
		}

		@Override
		public void insertValue(double value, Object... data) {
			inserter.insertValue(partitionID, value, data);
		}
		
		@Override
		public void insertValueConfidence(double value, double confidence, Object... data) {
			inserter.insertValue(partitionID, value, confidence, data);
		}
		
	}
	
	private class RDBMSTableInserter implements OpenInserter {
		
		private final RDBMSPredicateHandle handle;
		private final int argSize;
		private final PreparedStatement insertStmt;
		private final double defaultEvidenceValue;
		private final double defaultConfidence;
		
		public RDBMSTableInserter(RDBMSPredicateHandle ph) {
			handle = ph;
			argSize = handle.predicate().getArity();
			int numCols = 0;
			StringBuilder sql = new StringBuilder();
			sql.append("INSERT INTO ").append(handle.tableName()).append(" (");
			sql.append(handle.partitionColumn());
			numCols++;
			
			for (int i=0;i<handle.argumentColumns().length;i++) {
				sql.append(", ").append(handle.argumentColumns()[i]);
				numCols++;
			}
			sql.append(", ").append(handle.valueColumn());
			numCols++;
			sql.append(", ").append(handle.confidenceColumn());
			numCols++;
			
			sql.append(") VALUES ( ");
			for (int i=0;i<numCols;i++) {
				if (i>0) sql.append(", ");
				sql.append("?");
			}
			sql.append(")");
			try {
				insertStmt = database.prepareStatement( sql.toString() );
			} catch (SQLException e) {
				throw new AssertionError(e);
			}
			
			defaultEvidenceValue = TruthValues.getDefaultEvidence();
			defaultConfidence = ConfidenceValues.getMax();
		}
		
		@Override
		public void insert(Partition partitionID, Object... data) {
			insertInternal(partitionID, defaultEvidenceValue, defaultConfidence, data);
		}
		
		@Override
		public void insertValue(Partition partitionID, double value, Object... data) {
			insertInternal(partitionID, value, defaultConfidence, data);
		}
		
		@Override
		public void insertValue(Partition partitionID, double value, double confidence, Object... data) {
			insertInternal(partitionID, value, confidence, data);
		}
		
		private void insertInternal(Partition partition, double value, double confidence, Object[] data) {
			if (!(partition instanceof PartitionID))
				throw new IllegalArgumentException("Expected PartitionID object: " + partition);
			int partitionID = partition.getID();
			if (partitionID<0)
				throw new IllegalArgumentException("Partition IDs must be non-negative.");
			if (data.length!=argSize)
				throw new IllegalArgumentException("Data length does not match: " + data.length + " " + argSize);
			if (!TruthValues.isValid(value))
				throw new IllegalArgumentException("Invalid truth value: " + value);
			if (!ConfidenceValues.isValid(confidence))
				throw new IllegalArgumentException("Invalid confidence value: " + confidence);
			
			try {
				insertStmt.setInt(1,partitionID);
				int noCol = 1;
				for (int i=0;i<argSize;i++) {
					noCol++;
					assert data[i]!=null;
					if (data[i] instanceof Integer) {
						insertStmt.setInt(noCol, (Integer)data[i]);
					} else if (data[i] instanceof Double) {
						insertStmt.setDouble(noCol, (Double)data[i]);
					} else if (data[i] instanceof String) {
						insertStmt.setString(noCol, cleanString((String)data[i]));
					} else throw new IllegalArgumentException("Unknown data type for :"+data[i]);
				}
				
				noCol++;
				insertStmt.setDouble(noCol, value);
				noCol++;
				insertStmt.setDouble(noCol, confidence);

			    insertStmt.executeUpdate();

			} catch (SQLException e) {
				log.error(e.getMessage() + "\n" + Arrays.toString(data));
				throw new AssertionError(e);
			}
		}
		
		private String cleanString(String s) {
			return s.replace("'", " ");
		}
		
	}

}
