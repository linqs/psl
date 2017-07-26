/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.DataLoader;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.loading.OpenInserter;
import org.linqs.psl.model.predicate.Predicate;

import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RDBMSDataLoader implements DataLoader {
	public static final double DEFAULT_EVIDENCE_VALUE = 1.0;

	private static final Logger log = LoggerFactory.getLogger(RDBMSDataLoader.class);

	private final Connection connection;
	private final Map<Predicate, RDBMSTableInserter> inserts;

	public RDBMSDataLoader(Connection connection) {
		this.connection = connection;
		inserts = new HashMap<Predicate, RDBMSTableInserter>();
	}

	void registerPredicate(PredicateInfo predicateHandle) {
		inserts.put(predicateHandle.predicate(), new RDBMSTableInserter(predicateHandle));
	}

	@Override
	public RDBMSTableInserter getOpenInserter(Predicate predicate) {
		RDBMSTableInserter inserter = inserts.get(predicate);
		if (inserter == null) {
			throw new IllegalArgumentException("Predicate is unknown: " + predicate);
		}

		return inserter;
	}

	@Override
	public Inserter getInserter(Predicate predicate, Partition partitionID) {
		return new RDBMSInserter(getOpenInserter(predicate),partitionID);
	}

	private class RDBMSInserter implements Inserter {
		private final RDBMSTableInserter inserter;
		private final Partition partitionID;

		public RDBMSInserter(RDBMSTableInserter inserter, Partition pid) {
			assert(pid != null);
			this.inserter = inserter;
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
	}

	private class RDBMSTableInserter implements OpenInserter {
		private final PredicateInfo predicateHandle;
		private final int argSize;
		private final PreparedStatement insertStmt;

		public RDBMSTableInserter(PredicateInfo predicateHandle) {
			this.predicateHandle = predicateHandle;
			this.argSize = predicateHandle.predicate().getArity();

			InsertQuery sqlBuilder = new InsertQuery(predicateHandle.tableName());

			// Core columns (partition, value).
			sqlBuilder.addCustomPreparedColumns(new CustomSql(PredicateInfo.PARTITION_COLUMN_NAME));
			sqlBuilder.addCustomPreparedColumns(new CustomSql(PredicateInfo.VALUE_COLUMN_NAME));

			// Argument columns.
			for (String column : predicateHandle.argumentColumns()) {
				sqlBuilder.addCustomPreparedColumns(new CustomSql(column));
			}

			try {
				insertStmt = connection.prepareStatement(sqlBuilder.validate().toString());
			} catch (SQLException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void insert(Partition partition, Object... data) {
			insertInternal(partition, DEFAULT_EVIDENCE_VALUE, data);
		}

		@Override
		public void insertValue(Partition partition, double value, Object... data) {
			insertInternal(partition, value, data);
		}

		private void insertInternal(Partition partition, double value, Object[] data) {
			int partitionID = partition.getID();
			if (partitionID < 0) {
				throw new IllegalArgumentException("Partition IDs must be non-negative.");
			}

			if (data.length != argSize) {
				throw new IllegalArgumentException(
					String.format("Data length does not match for %s: Expecting: %d, Got: %d",
					partition.getName(), argSize, data.length));
			}

			try {
				// Partition
				insertStmt.setInt(1, partitionID);

				// Value
				if (Double.isNaN(value)) {
					insertStmt.setNull(2, java.sql.Types.DOUBLE);
				} else {
					insertStmt.setDouble(2, value);
				}

				// Prepared startments index by 1, and offset by the partition and value.
				int dataOffset = 3;
				for (int i = 0; i < argSize; i++) {
					assert data[i] != null;

					if (data[i] instanceof Integer) {
						insertStmt.setInt(i + dataOffset, (Integer)data[i]);
					} else if (data[i] instanceof Double) {
						// The standard JDBC way to insert NaN is using setNull
						// if not, mysql will complain about any NaNs.
						if (Double.isNaN((Double)data[i])) {
							insertStmt.setNull(i + dataOffset, java.sql.Types.DOUBLE);
						} else {
							insertStmt.setDouble(i + dataOffset, (Double)data[i]);
						}
					} else if (data[i] instanceof String) {
						insertStmt.setString(i + dataOffset, (String)data[i]);
					} else if (data[i] instanceof RDBMSUniqueIntID) {
						insertStmt.setInt(i + dataOffset, ((RDBMSUniqueIntID)data[i]).getID());
					} else if (data[i] instanceof RDBMSUniqueStringID) {
						insertStmt.setString(i + dataOffset, ((RDBMSUniqueStringID)data[i]).getID());
					} else {
						throw new IllegalArgumentException("Unknown data type for :" + data[i]);
					}
				}

				insertStmt.executeUpdate();
				insertStmt.clearParameters();
			} catch (SQLException ex) {
				log.error(ex.getMessage() + "\n" + Arrays.toString(data));
				throw new RuntimeException(ex);
			}
		}
	}
}
