/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CreateIndexQuery;
import com.healthmarketscience.sqlbuilder.CreateTableQuery;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.DeleteQuery;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.QueryPreparer;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UpdateQuery;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PredicateInfo {
	private static final Logger log = LoggerFactory.getLogger(PredicateInfo.class);

	public static final String PREDICATE_TABLE_SUFFIX = "_PREDICATE";
	public static final String PARTITION_COLUMN_NAME = "partition_id";
	public static final String VALUE_COLUMN_NAME = "value";

	private final Predicate predicate;
	private final List<String> argCols;
	private final String tableName;

	private Map<String, String> cachedSQL;

	public PredicateInfo(Predicate predicate) {
		assert(predicate != null);

		this.predicate = predicate;
		this.tableName = predicate.getName() + PREDICATE_TABLE_SUFFIX;

		argCols = new ArrayList<String>(predicate.getArity());
		for (int i = 0; i < predicate.getArity(); i++) {
			argCols.add(predicate.getArgumentType(i).getName() + "_" + i);
		}

		cachedSQL = new HashMap<String, String>();
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
	 * Create a prepared statement to count all the ground atoms for this predicate (within the specified partitions).
	 * You can specify no partitions with null or an empty list.
	 */
	public PreparedStatement createCountAllStatement(Connection connection, List<Integer> partitions) {
		return prepareSQL(connection, buildCountAllStatement(partitions));
	}

	/**
	 * Create a prepared statement to query all the atoms for this predicate (within the specified partitions).
	 * You can specify no partitions with null or an empty list.
	 * The columns will ALWAYS be in the following order: partition, value, data columns (determined by getArgumentColumns()).
	 */
	public PreparedStatement createQueryAllStatement(Connection connection, List<Integer> partitions) {
		return prepareSQL(connection, buildQueryAllStatement(partitions));
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
		return prepareSQL(connection, buildQueryStatement(readPartitions));
	}

	/**
	 * Create a prepared statement that upserts.
	 * The variables left to set in the query are the partition, value, and predciate arguments.
	 */
	public PreparedStatement createUpsertStatement(Connection connection, DatabaseDriver dbDriver) {
		return prepareSQL(connection, buildUpsertStatement(dbDriver));
	}

	/**
	 * Create a prepared statement that deletes ground atoms that match all the arguments.
	 * Note that we will only delete from the write partition.
	 */
	public PreparedStatement createDeleteStatement(Connection connection, int writePartition) {
		return prepareSQL(connection, buildDeleteStatement(writePartition));
	}

	/**
	 * Create a prepared statement that changes moves atoms from one partition to another.
	 */
	public PreparedStatement createPartitionMoveStatement(Connection connection, int oldPartition, int newPartition) {
		return prepareSQL(connection, buildPartitionMoveStatement(oldPartition, newPartition));
	}

	private void createTable(Connection connection, DatabaseDriver dbDriver) {
		CreateTableQuery createTable = new CreateTableQuery(tableName);

		// First add non-variable columns: suggogate key, partition, value.
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
		// The partition column MUST be the last one (since H2 is super picky).
		uniqueColumns.add(PARTITION_COLUMN_NAME);
		if (uniqueColumns.size() > 1) {
			createTable.addCustomConstraints("UNIQUE(" + StringUtils.join(uniqueColumns, ", ") + ")");
		}

		// We have an additional constraint that all atoms in a partition must be unique.
		// If all columns are UniqueIDs, when we are already done.
		// Add 1 since we already put the partition in |uniqueColumns|.
		if (uniqueColumns.size() < (argCols.size() + 1)) {
			// We want the partition to be last.
			uniqueColumns.remove(PARTITION_COLUMN_NAME);
			for (String colName : argCols) {
				if (!uniqueColumns.contains(colName)) {
					uniqueColumns.add(colName);
				}
			}
			uniqueColumns.add(0, PARTITION_COLUMN_NAME);

			createTable.addCustomConstraints("UNIQUE(" + StringUtils.join(uniqueColumns, ", ") + ")");
		}

		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate(dbDriver.finalizeCreateTable(createTable));
		} catch(SQLException ex) {
			throw new RuntimeException("Error creating table for predicate: " + predicate.getName(), ex);
		}
	}

	private void index(Connection connection, DatabaseDriver dbDriver) {
		List<String> indexes = new ArrayList<String>();

		// The primary index used for grounding.
		CreateIndexQuery createIndex = new CreateIndexQuery(tableName(), "IX_" + tableName() + "_GROUNDING");

		// The column order is very important: data columns, then index.
		for (String colName : argCols) {
			createIndex.addCustomColumns(colName);
		}
		createIndex.addCustomColumns(PARTITION_COLUMN_NAME);
		indexes.add(createIndex.validate().toString());

		// Create simple index on each column.
		// Often the query planner will choose a small index over the full one for specific parts of the query.
		for (String colName : argCols) {
			createIndex = new CreateIndexQuery(tableName(), "IX_" + tableName() + "_" + colName);
			createIndex.addCustomColumns(colName);
			indexes.add(createIndex.validate().toString());
		}

		// Include the partition.
		createIndex = new CreateIndexQuery(tableName(), "IX_" + tableName() + "_" + PARTITION_COLUMN_NAME);
		createIndex.addCustomColumns(PARTITION_COLUMN_NAME);
		indexes.add(createIndex.validate().toString());

		try (Statement statement = connection.createStatement()) {
			for (String index : indexes) {
				statement.executeUpdate(index);
			}
		} catch(SQLException ex) {
			throw new RuntimeException("Error creating index on table for predicate: " + predicate.getName(), ex);
		}
	}

	/**
	 * Look through the database's tables and columns and construct predicates tables that look like predicate tables.
	 */
	public static List<StandardPredicate> deserializePredicates(Connection connection) {
		List<StandardPredicate> predicates = new ArrayList<StandardPredicate>();

		try (ResultSet resultSet = connection.getMetaData().getTables(null, null, null, null)) {
			while (resultSet.next()) {
				String tableName = resultSet.getString("TABLE_NAME");
				String tableSchema = resultSet.getString("TABLE_SCHEM");

				// We always create predicate tables in the public schema.
				if (!tableSchema.equalsIgnoreCase("public")) {
					continue;
				}

				// Extract the predicate name from a matching table name
				if (tableName.toLowerCase().endsWith(PredicateInfo.PREDICATE_TABLE_SUFFIX.toLowerCase())) {
					String predicateName = tableName.toLowerCase().replaceFirst(PredicateInfo.PREDICATE_TABLE_SUFFIX.toLowerCase() + "$", "");

					StandardPredicate predicate = createPredicateFromTable(connection, tableName, predicateName);
					if (predicate != null) {
						predicates.add(predicate);
					}
				}
			}
		} catch (SQLException ex) {
			throw new RuntimeException("Error reading database metadata.", ex);
		}

		log.debug("Registered {} pre-existing predicates from RDBMS.", predicates.size());
		return predicates;
	}

	private String buildCountAllStatement(List<Integer> partitions) {
		String key = "countAll_" + partitions.toString();
		if (cachedSQL.containsKey(key)) {
			return cachedSQL.get(key);
		}

		SelectQuery query = new SelectQuery();

		query.addCustomColumns(new CustomSql("COUNT(*)"));
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

		String sql = query.validate().toString();
		cachedSQL.put(key, sql);
		return sql;
	}

	private String buildQueryAllStatement(List<Integer> partitions) {
		String key = "queryAll_" + partitions.toString();
		if (cachedSQL.containsKey(key)) {
			return cachedSQL.get(key);
		}

		SelectQuery query = new SelectQuery();

		// Select everything in a predictable order.
		query.addCustomColumns(new CustomSql(PARTITION_COLUMN_NAME));
		query.addCustomColumns(new CustomSql(VALUE_COLUMN_NAME));
		for (String colName : argCols) {
			query.addCustomColumns(new CustomSql(colName));
		}

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

		String sql = query.validate().toString();
		cachedSQL.put(key, sql);
		return sql;
	}

	private String buildQueryStatement(List<Integer> readPartitions) {
		String key = "query_" + readPartitions.toString();
		if (cachedSQL.containsKey(key)) {
			return cachedSQL.get(key);
		}

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

		String sql = query.validate().toString();
		cachedSQL.put(key, sql);
		return sql;
	}

	private String buildUpsertStatement(DatabaseDriver dbDriver) {
		String key = "upsert";
		if (cachedSQL.containsKey(key)) {
			return cachedSQL.get(key);
		}

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

		String sql = dbDriver.getUpsert(tableName, columns, keyColumns);
		cachedSQL.put(key, sql);
		return sql;
	}

	private String buildDeleteStatement(int writePartition) {
		String key = "delete_" + writePartition;
		if (cachedSQL.containsKey(key)) {
			return cachedSQL.get(key);
		}

		DeleteQuery delete = new DeleteQuery(tableName);
		QueryPreparer.MultiPlaceHolder placeHolder = (new QueryPreparer()).getNewMultiPlaceHolder();

		// Only delete in the write partition.
		delete.addCondition(BinaryCondition.equalTo(new CustomSql(PARTITION_COLUMN_NAME), writePartition));

		// Set placeholders for the arguments.
		for (String colName : argCols) {
			delete.addCondition(BinaryCondition.equalTo(new CustomSql(colName), placeHolder));
		}

		String sql = delete.validate().toString();
		cachedSQL.put(key, sql);
		return sql;
	}

	private String buildPartitionMoveStatement(int oldPartition, int newPartition) {
		String key = "movePartition_" + oldPartition + "_" + newPartition;
		if (cachedSQL.containsKey(key)) {
			return cachedSQL.get(key);
		}

		UpdateQuery update = new UpdateQuery(tableName);

		update.addCondition(BinaryCondition.equalTo(new CustomSql(PARTITION_COLUMN_NAME), oldPartition));
		update.addCustomSetClause(new CustomSql(PARTITION_COLUMN_NAME), newPartition);

		String sql = update.validate().toString();
		cachedSQL.put(key, sql);
		return sql;
	}

	private PreparedStatement prepareSQL(Connection connection, String sql) {
		try {
			return connection.prepareStatement(sql);
		} catch (SQLException ex) {
			throw new RuntimeException("Could not create prepared statement from (" + sql + ").", ex);
		}
	}

	/**
	 * Construct a predicate given a specific table.
	 * If this table is not actually a predicate table, the null will be returned.
	 */
	private static StandardPredicate createPredicateFromTable(Connection connection, String tableName, String name) {
		Pattern argumentPattern = Pattern.compile("(\\w+)_(\\d)");

		try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, null)) {
			ArrayList<ConstantType> args = new ArrayList<ConstantType>();

			boolean hasPartitionColumn = false;
			boolean hasValueColumn = false;

			while (resultSet.next()) {
				String columnName = resultSet.getString("COLUMN_NAME");

				Matcher match = argumentPattern.matcher(columnName);
				if (columnName.equalsIgnoreCase(PredicateInfo.PARTITION_COLUMN_NAME)) {
					hasPartitionColumn = true;
				} else if (columnName.equalsIgnoreCase(PredicateInfo.VALUE_COLUMN_NAME)) {
					hasValueColumn = true;
				} else if (match.find()) {
					String argumentName = match.group(1).toLowerCase();
					int argumentLocation = Integer.parseInt(match.group(2));

					if (argumentName.equals("string")) {
						args.add(argumentLocation, ConstantType.String);
					} else if (argumentName.equals("integer")) {
						args.add(argumentLocation, ConstantType.Integer);
					} else if (argumentName.equals("double")) {
						args.add(argumentLocation, ConstantType.Double);
					} else if (argumentName.equals("uniqueintid")) {
						args.add(argumentLocation, ConstantType.UniqueIntID);
					} else if (argumentName.equals("uniquestringid")) {
						args.add(argumentLocation, ConstantType.UniqueStringID);
					}
				}
			}

			// Check if we found all the required columns and some argument columns were found.
			if (!hasPartitionColumn || !hasValueColumn || args.size() == 0) {
				return null;
			}

			PredicateFactory factory = PredicateFactory.getFactory();
			return factory.createStandardPredicate(name, args.toArray(new ConstantType[args.size()]));
		} catch (SQLException ex) {
			throw new RuntimeException("Failed to create predicate (" + name + ") from table (" + tableName + ").", ex);
		}
	}
}
