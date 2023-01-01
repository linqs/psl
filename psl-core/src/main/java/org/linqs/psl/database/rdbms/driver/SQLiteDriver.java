/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
package org.linqs.psl.database.rdbms.driver;

import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;

import com.healthmarketscience.sqlbuilder.SelectQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite Connection Wrapper.
 */
public class SQLiteDriver extends DatabaseDriver {
    private static final Logger log = Logger.getLogger(SQLiteDriver.class);

    private boolean inMemory;

    /**
     * Construct a SQLite connection.
     * The database name is not used in the connection or maintenance of the database, only to identify it in logging.
     */
    public SQLiteDriver(boolean inMemory, String path, boolean clearDatabase) {
        this(formatConnectionString(inMemory, path), clearDatabase);

        if (inMemory) {
            log.debug("Using in-memory database.");
        } else {
            log.debug("Using on-disk database: " + path);
        }
    }

    public SQLiteDriver(String connectionString, boolean clearDatabase) {
        super("org.sqlite.JDBC", connectionString, clearDatabase);
        log.debug("Connected to SQLite database.");

        if (connectionString.contains(":memory:")) {
            inMemory = true;
        }
    }

    @Override
    protected void clearDatabase() {
        executeUpdate("PRAGMA writable_schema = ON");
        executeUpdate("DELETE FROM sqlite_master");
        executeUpdate("PRAGMA writable_schema = OFF");
        executeUpdate("VACUUM");
        executeUpdate("PRAGMA integrity_check");
    }

    @Override
    public String getTypeName(ConstantType type) {
        switch (type) {
            case Double:
                return "REAL";
            case Integer:
                return "INTEGER";
            case String:
                return "TEXT";
            case Long:
                return "INTEGER";
            case UniqueIntID:
                return "INTEGER";
            case UniqueStringID:
                return "TEXT";
            default:
                throw new IllegalStateException("Unknown ConstantType: " + type);
        }
    }

    @Override
    public String getSurrogateKeyColumnDefinition(String columnName) {
        return columnName + " INTEGER PRIMARY KEY";
    }

    @Override
    public String getDoubleTypeName() {
        return "REAL";
    }

    @Override
    public String getUpsert(String tableName, String[] columns, String[] keyColumns) {
        List<String> updateValues = new ArrayList<String>();
        for (String column : columns) {
            updateValues.add(String.format("%s = EXCLUDED.%s", column, column));
        }

        // SQLite uses the PostgreSQL syntax of "INSERT ... ON CONFLICT".
        List<String> sql = new ArrayList<String>();
        sql.add("INSERT INTO " + tableName + "");
        sql.add("    (" + StringUtils.join(", ", columns) + ")");
        sql.add("VALUES");
        sql.add("    (" + StringUtils.repeat("?", ", ", columns.length) + ")");
        sql.add("ON CONFLICT");
        sql.add("    (" + StringUtils.join(", ", keyColumns) + ")");
        sql.add("DO UPDATE SET");
        sql.add("    " + ListUtils.join(", ", updateValues));

        return ListUtils.join(System.lineSeparator(), sql);
    }

    private static String formatConnectionString(boolean inMemory, String path) {
        if (inMemory) {
            return "jdbc:sqlite:file::memory:?cache=shared&read_uncommitted=true";
        }

        return String.format("jdbc:sqlite:%s?read_uncommitted=true", path);
    }

    @Override
    public void updateDBStats() {
        executeUpdate("VACUUM");
    }

    @Override
    public String setLimit(SelectQuery query, int count) {
        String queryString = query.validate().toString();
        return queryString + " LIMIT " + count;
    }

    @Override
    public boolean canConcurrentWrite() {
        return inMemory;
    }
}
