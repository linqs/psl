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
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class H2DatabaseDriver extends DatabaseDriver {
    public enum Type {
        Disk, Memory
    }

    private static final Logger log = Logger.getLogger(H2DatabaseDriver.class);

    public static final int LOCK_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * Constructor for the H2 database driver.
     * @param dbType Type of database, either Disk or Memory.
     * @param path Path to database on disk, or name if type is Memory.
     * @param clear Whether to perform a DROP ALL on the database after connecting.
     */
    public H2DatabaseDriver(Type dbType, String path, boolean clear) {
        super("org.h2.Driver", buildConnectionString(dbType, path), checkClearDatabase(dbType, path, clear));

        log.debug("Connected to H2 database: " + path);
    }

    private static String buildConnectionString(Type dbType, String path) {
        String connectionString = "jdbc:h2";

        switch (dbType) {
            case Disk:
                connectionString += ":" + path;
                break;
            case Memory:
                connectionString += ":mem:" + path;
                break;
            default:
                throw new IllegalArgumentException("Unknown database type: " + dbType);
        }

        connectionString += ";LOCK_TIMEOUT=" + LOCK_TIMEOUT_MS;

        return connectionString;
    }

    /**
     * H2 has some additional issues with clearing databases, it is easier just to remove the files.
     */
    private static boolean checkClearDatabase(Type dbType, String path, boolean clear) {
        if (!clear) {
            return false;
        }

        if (dbType == Type.Memory) {
            return true;
        }

        // Manually delete the main database and possible trace database.
        FileUtils.delete(path + ".mv.db");
        FileUtils.delete(path + ".trace.db");

        // The database has already been cleared, but we will still pass back a true.
        return true;
    }

    @Override
    protected void clearDatabase() {
        executeUpdate("DROP ALL OBJECTS");
    }

    @Override
    public String getTypeName(ConstantType type) {
        switch (type) {
            case Double:
                return "DOUBLE";
            case Integer:
                return "INT";
            case String:
                return "VARCHAR";
            case Long:
                return "BIGINT";
            case UniqueIntID:
                return "INT";
            case UniqueStringID:
                return "VARCHAR(255)";
            default:
                throw new IllegalStateException("Unknown ConstantType: " + type);
        }
    }

    @Override
    public String getSurrogateKeyColumnDefinition(String columnName) {
        return columnName + " BIGINT IDENTITY PRIMARY KEY";
    }

    @Override
    public String getDoubleTypeName() {
        return "DOUBLE";
    }

    @Override
    public String getUpsert(String tableName, String[] columns, String[] keyColumns) {
        // H2 uses a "MERGE" syntax and requires a specified key.
        List<String> sql = new ArrayList<String>();
        sql.add("MERGE INTO " + tableName + "");
        sql.add("    (" + StringUtils.join(", ", columns) + ")");
        sql.add("KEY");
        sql.add("    (" + StringUtils.join(", ", keyColumns) + ")");
        sql.add("VALUES");
        sql.add("    (" + StringUtils.repeat("?", ", ", columns.length) + ")");

        return ListUtils.join(System.lineSeparator(), sql);
    }

    @Override
    public void updateDBStats() {
        executeUpdate("ANALYZE");
    }
}
