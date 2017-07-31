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

import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.ConstantType;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CreateIndexQuery;
import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.DeleteQuery;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import com.healthmarketscience.sqlbuilder.QueryPreparer;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UpdateQuery;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PredicateInfo {
	public static final String PREDICATE_TABLE_SUFFIX = "_PREDICATE";
	public static final String SURROGATE_KEY_COLUMN_NAME = "id";
	public static final String PARTITION_COLUMN_NAME = "partition";
	public static final String VALUE_COLUMN_NAME = "value";

	private final Predicate predicate;
	private final List<String> argCols;
	private final String tableName;

	public PredicateInfo(Predicate predicate, String[] argCols, String tableName) {
		assert(predicate != null);
		assert(argCols != null);
		assert(tableName != null);

		this.predicate = predicate;
		this.argCols = Arrays.asList(argCols);
		this.tableName = tableName + PREDICATE_TABLE_SUFFIX;

		if (this.argCols.size() != predicate.getArity()) {
			throw new IllegalArgumentException(String.format(
					"Number of predicate argument names (%d) must match its arity (%d)!",
					this.argCols.size(), predicate.getArity()));
		}
	}

	public List<String> argumentColumns() {
		return Collections.unmodifiableList(argCols);
	}

	public String tableName() {
		return tableName;
	}

	public Predicate predicate() {
		return predicate;
	}

	public void setupTable(Connection connection, DatabaseDriver dbDriver) {
		createTable(connection, dbDriver);
		index(connection, dbDriver);
	}

	/**
	 * Create a prepared statement to query all the atoms for this predicate (within the specified partitions).
	 * You can specify no partitions with null or an empty list.
	 */
	public PreparedStatement createQueryAllStatement(Connection connection, List<Integer> partitions) {
		SelectQuery query = new SelectQuery();

		// Seelct *
		query.addAllColumns();
		query.addCustomFromTable(tableName);

		// If there is only 1 partition, just do equality, otherwise use IN.
		// All DBMSs should optimize a single IN the same as equality, but just in case.
		if (partitions != null && partitions.size() > 0) {
			if (partitions.size() == 1) {
				query.addCondition(BinaryCondition.equalTo(new CustomSql(PARTITION_COLUMN_NAME), partitions.get(0)));
			} else {
				query.addCondition(new InCondition(new CustomSql(PARTITION_COLUMN_NAME), partitions));
			}
		}

		try {
			return connection.prepareStatement(query.validate().toString());
		} catch (SQLException ex) {
			throw new RuntimeException("Could not create prepared statement.", ex);
		}
	}

	/**
	 * Create a prepared statement that queries for all random variable atoms
	 * (atoms in the write partition) of this predicate.
	 * No query parameters need filling.
	 */
	public PreparedStatement createQueryAllWriteStatement(Connection connection, int writePartition) {
		List<Integer> partitions = new ArrayList<Integer>(1);
		partitions.add(writePartition);
		return createQueryAllStatement(connection, partitions);
	}

	/**
	 * Create a prepared statement that queries for one specific atom.
	 * The variables left to set in the query are the predciate arguments.
	 */
	public PreparedStatement createQueryStatement(Connection connection, List<Integer> readPartitions) {
		SelectQuery query = new SelectQuery();
		QueryPreparer.MultiPlaceHolder placeHolder = (new QueryPreparer()).getNewMultiPlaceHolder();

		// Seelct *
		query.addAllColumns();
		query.addCustomFromTable(tableName);

		// We only want to query from the read partitions.
		query.addCondition(new InCondition(new CustomSql(PARTITION_COLUMN_NAME), readPartitions));

		for (String colName : argCols) {
			query.addCondition(BinaryCondition.equalTo(new CustomSql(colName), placeHolder));
		}

		try {
			return connection.prepareStatement(query.validate().toString());
		} catch (SQLException ex) {
			throw new RuntimeException("Could not create prepared statement.", ex);
		}
	}

	/**
	 * Create a prepared statement that upserts.
	 * The variables left to set in the query are the partition, value, and predciate arguments.
	 */
	public PreparedStatement createUpsertStatement(Connection connection, DatabaseDriver dbDriver) {
		// Columns with data in them: partition, value, argument.
		String[] columns = new String[2 + argCols.size()];

		// Columns to treat as a key: partition, arguments.
		String[] keyColumns = new String[1 + argCols.size()];

		columns[0] = PredicateInfo.PARTITION_COLUMN_NAME;
		columns[1] = PredicateInfo.VALUE_COLUMN_NAME;

		keyColumns[0] = PredicateInfo.PARTITION_COLUMN_NAME;

		for (int i = 0; i < argCols.size(); i++) {
			columns[2 + i] = argCols.get(i);
			keyColumns[1 + i] = argCols.get(i);
		}

		return dbDriver.getUpsert(connection, tableName, columns, keyColumns);
	}

	/**
	 * Create a prepared statement that deletes ground atoms that match all the arguments.
	 * Note that we will only delete from the write partition.
	 */
	public PreparedStatement createDeleteStatement(Connection connection, int writePartition) {
		DeleteQuery delete = new DeleteQuery(tableName);
		QueryPreparer.MultiPlaceHolder placeHolder = (new QueryPreparer()).getNewMultiPlaceHolder();

		// Only delete in the write partition.
		delete.addCondition(BinaryCondition.equalTo(new CustomSql(PARTITION_COLUMN_NAME), writePartition));

		// Set placeholders for the arguments.
		for (String colName : argCols) {
			delete.addCondition(BinaryCondition.equalTo(new CustomSql(colName), placeHolder));
		}

		try {
			return connection.prepareStatement(delete.toString());
		} catch (SQLException ex) {
			throw new RuntimeException("Could not prepare delete for " + tableName, ex);
		}
	}

	private void createTable(Connection connection, DatabaseDriver dbDriver) {
		CreateTableQuery createTable = new CreateTableQuery(tableName);

		// First add non-variable columns: suggogate key, partition, value.
		createTable.addCustomColumns(dbDriver.getSurrogateKeyColumnDefinition(SURROGATE_KEY_COLUMN_NAME));
		createTable.addCustomColumns(PARTITION_COLUMN_NAME + " INT NOT NULL");
		createTable.addCustomColumns(VALUE_COLUMN_NAME + " " + dbDriver.getDoubleTypeName() + " NOT NULL");

		// Now add the variable columns.
		List<String> uniqueColumns = new ArrayList<String>();

		// Add columns for each predicate argument
		for (int i = 0; i < argCols.size(); i++) {
			String colName = argCols.get(i);

			ConstantType type = predicate.getArgumentType(i);
			String typeName = dbDriver.getTypeName(type);

			// All unique columns get added to a unique constraint.
			if (type == ConstantType.UniqueIntID || type == ConstantType.UniqueStringID) {
				uniqueColumns.add(colName);
			}

			createTable.addCustomColumns(colName + " " + typeName + " NOT NULL");
		}

		// Add a unique constraint for all the unique ids (and partition).
		uniqueColumns.add(0, PARTITION_COLUMN_NAME);
		if (uniqueColumns.size() > 1) {
			createTable.addCustomConstraints("UNIQUE(" + StringUtils.join(uniqueColumns, ", ") + ")");
		}

		// We have an additional constraint that all atoms in a partition must be unique.
		// If all columns are UniqueIDs, when we are already done.
		// Add 1 since we already put the partition in |uniqueColumns|.
		if (uniqueColumns.size() < (argCols.size() + 1)) {
			for (String colName : argCols) {
				if (!uniqueColumns.contains(colName)) {
					uniqueColumns.add(colName);
				}
			}

			createTable.addCustomConstraints("UNIQUE(" + StringUtils.join(uniqueColumns, ", ") + ")");
		}

		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate(createTable.validate().toString());
		} catch(SQLException ex) {
			throw new RuntimeException("Error creating table for predicate: " + predicate.getName(), ex);
		}
	}

	private void index(Connection connection, DatabaseDriver dbDriver) {
		// The primary index used for grounding.
		CreateIndexQuery createIndex = new CreateIndexQuery(tableName(), "IX_" + tableName() + "_GROUNDING");

		// First add the partition.
		createIndex.addCustomColumns(PARTITION_COLUMN_NAME);

		// Now add the variable columns.
		for (String colName : argCols) {
			createIndex.addCustomColumns(colName);
		}

		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate(createIndex.validate().toString());
		} catch(SQLException ex) {
			throw new RuntimeException("Error creating index on table for predicate: " + predicate.getName(), ex);
		}
	}
}
