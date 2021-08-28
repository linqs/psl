/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.StringUtils;

import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An inserter that is aware of what predicate and partition it is inserting into.
 */
public class RDBMSInserter extends Inserter {
    /**
     * The number of inserts in each batch.
     */
    public static final int DEFAULT_PAGE_SIZE = 2500;
    public static final double DEFAULT_EVIDENCE_VALUE = 1.0;

    /**
     * The number of records in each multi-row insert.
     */
    public static final int DEFAULT_MULTIROW_COUNT = 25;

    private static final Logger log = LoggerFactory.getLogger(RDBMSInserter.class);

    private final RDBMSDataStore dataStore;
    private final PredicateInfo predicateInfo;
    private final Partition partition;

    // We will keep two pre-constructed sql statements:
    //  - one for inserting a single record
    //  - one for inserting DEFAULT_MULTIROW_COUNT records
    private final String singleInsertSQL;
    private final String multiInsertSQL;

    public RDBMSInserter(RDBMSDataStore dataStore, PredicateInfo predicateInfo, Partition partition) {
        super(predicateInfo.argumentColumns().size());

        this.dataStore = dataStore;
        this.predicateInfo = predicateInfo;
        this.partition = partition;

        singleInsertSQL = createSingleInsert();
        multiInsertSQL = createMultiInsert();
    }

    private String createSingleInsert() {
        InsertQuery sqlBuilder = new InsertQuery(predicateInfo.tableName());

        // Core columns (partition, value).
        sqlBuilder.addCustomPreparedColumns(new CustomSql(PredicateInfo.PARTITION_COLUMN_NAME));
        sqlBuilder.addCustomPreparedColumns(new CustomSql(PredicateInfo.VALUE_COLUMN_NAME));

        // Argument columns.
        for (String column : predicateInfo.argumentColumns()) {
            sqlBuilder.addCustomPreparedColumns(new CustomSql(column));
        }

        return sqlBuilder.validate().toString();
    }

    private String createMultiInsert() {
        List<String> columns = new ArrayList<String>();
        columns.add(PredicateInfo.PARTITION_COLUMN_NAME);
        columns.add(PredicateInfo.VALUE_COLUMN_NAME);
        columns.addAll(predicateInfo.argumentColumns());

        String placeholders = StringUtils.repeat("?", ", ", columns.size());

        List<String> multiInsert = new ArrayList<String>();
        multiInsert.add("INSERT INTO " + predicateInfo.tableName());
        multiInsert.add("    (" + ListUtils.join(", ", columns) + ")");
        multiInsert.add("VALUES");
        multiInsert.add("    " + StringUtils.repeat("(" + placeholders + ")", ", ", DEFAULT_MULTIROW_COUNT));

        return ListUtils.join(System.lineSeparator(), multiInsert);
    }

    @Override
    public void insertAll(List<List<Object>> data) {
        List<Double> truthValues = new ArrayList<Double>(data.size());
        for (int i = 0; i < data.size(); i++) {
            truthValues.add(DEFAULT_EVIDENCE_VALUE);
        }

        insertInternal(truthValues, data);
    }

    @Override
    public void insertAllValues(List<Double> values, List<List<Object>> data) {
        insertInternal(values, data);
    }

    @Override
    public boolean supportsBulkCopy() {
        return dataStore.getDriver().supportsBulkCopy();
    }

    @Override
    public void bulkCopy(String path, String delimiter, boolean hasTruth) {
        dataStore.getDriver().bulkCopy(path, delimiter, hasTruth, predicateInfo, partition);
    }

    private void insertInternal(List<Double> values, List<List<Object>> data) {
        assert(values.size() == data.size());

        int partitionID = partition.getID();
        if (partitionID < 0) {
            throw new IllegalArgumentException("Partition IDs must be non-negative.");
        }

        for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
            List<Object> row = data.get(rowIndex);

            assert(row != null);

            if (row.size() != predicateInfo.argumentColumns().size()) {
                throw new IllegalArgumentException(
                    String.format("Data on row %d length does not match for %s: Expecting: %d, Got: %d",
                    rowIndex, partition.getName(), predicateInfo.argumentColumns().size(), row.size()));
            }
        }

        try (
            Connection connection = dataStore.getConnection();
            PreparedStatement multiInsertStatement = connection.prepareStatement(multiInsertSQL);
            PreparedStatement singleInsertStatement = connection.prepareStatement(singleInsertSQL);
        ) {
            int batchSize = 0;

            // We will go from the multi-insert to the single-insert when we don't have enough data to fill the multi-insert.
            PreparedStatement activeStatement = multiInsertStatement;
            int insertSize = DEFAULT_MULTIROW_COUNT;

            int rowIndex = 0;
            while (rowIndex < data.size()) {
                // Index for the current index.
                int paramIndex = 1;

                if (activeStatement == multiInsertStatement && data.size() - rowIndex < DEFAULT_MULTIROW_COUNT) {
                    // Commit any records left in the multi-insert batch.
                    if (batchSize > 0) {
                        activeStatement.executeBatch();
                        activeStatement.clearBatch();
                        batchSize = 0;
                    }

                    activeStatement = singleInsertStatement;
                    insertSize = 1;
                }

                for (int i = 0; i < insertSize; i++) {
                    List<Object> row = data.get(rowIndex);
                    Double value = values.get(rowIndex);

                    // Partition
                    activeStatement.setInt(paramIndex++, partitionID);

                    // Value
                    if (value == null || value.isNaN()) {
                        activeStatement.setNull(paramIndex++, java.sql.Types.DOUBLE);
                    } else {
                        activeStatement.setDouble(paramIndex++, value);
                    }

                    for (int argIndex = 0; argIndex < predicateInfo.argumentColumns().size(); argIndex++) {
                        Object argValue = row.get(argIndex);

                        assert(argValue != null);

                        if (argValue instanceof Integer) {
                            activeStatement.setInt(paramIndex++, (Integer)argValue);
                        } else if (argValue instanceof Double) {
                            // The standard JDBC way to insert NaN is using setNull
                            if (Double.isNaN((Double)argValue)) {
                                activeStatement.setNull(paramIndex++, java.sql.Types.DOUBLE);
                            } else {
                                activeStatement.setDouble(paramIndex++, (Double)argValue);
                            }
                        } else if (argValue instanceof String) {
                            // This is the most common value we get when someone is using InsertUtils.
                            // The value may need to be convered from a string.
                            activeStatement.setObject(paramIndex++, convertString((String)argValue, argIndex));
                        } else if (argValue instanceof UniqueIntID) {
                            activeStatement.setInt(paramIndex++, ((UniqueIntID)argValue).getID());
                        } else if (argValue instanceof UniqueStringID) {
                            activeStatement.setString(paramIndex++, ((UniqueStringID)argValue).getID());
                        } else {
                            throw new IllegalArgumentException("Unknown data type for :" + argValue);
                        }
                    }

                    rowIndex++;
                }

                activeStatement.addBatch();
                batchSize++;

                if (batchSize >= DEFAULT_PAGE_SIZE) {
                    activeStatement.executeBatch();
                    activeStatement.clearBatch();
                    batchSize = 0;
                }
            }

            if (batchSize > 0) {
                activeStatement.executeBatch();
                activeStatement.clearBatch();
                batchSize = 0;
            }
            activeStatement.clearParameters();
            activeStatement = null;
        } catch (SQLException ex) {
            log.error(ex.getMessage());
            throw new RuntimeException("Error inserting into RDBMS.", ex);
        }
    }

    /**
     * Take in the value to be inserted as a string and convert it to the appropriate Java type
     * for PreparedStatement.setObject().
     */
    private Object convertString(String value, int argumentIndex) {
        switch (predicateInfo.predicate().getArgumentType(argumentIndex)) {
            case Double:
                return Double.valueOf(Double.parseDouble(value));
            case Integer:
            case UniqueIntID:
                return Integer.valueOf(Integer.parseInt(value));
            case String:
            case UniqueStringID:
                return value;
            case Long:
                return Long.valueOf(Long.parseLong(value));
            default:
                throw new IllegalArgumentException("Unknown argument type: " + predicateInfo.predicate().getArgumentType(argumentIndex));
        }
    }
}
