/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.database;

import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.util.RandUtils;

import java.io.File;
import java.nio.file.Paths;

public class DatabaseTestUtil {
    private static final String PERSISTED_DB_SUFFIX = String.format("%012d", RandUtils.nextInt());
    private static final String DB_NAME = "psltest";
    private static final String DB_BASE_PATH = Paths.get(System.getProperty("java.io.tmpdir"), DB_NAME + "_" + PERSISTED_DB_SUFFIX).toString();

    public static DatabaseDriver getH2Driver() {
        return getH2Driver(true, false);
    }

    public static DatabaseDriver getH2Driver(boolean clear, boolean persisted) {
        if (persisted) {
            return new H2DatabaseDriver(H2DatabaseDriver.Type.Disk, DB_BASE_PATH, clear);
        } else {
            return new H2DatabaseDriver(H2DatabaseDriver.Type.Memory, DB_NAME, clear);
        }
    }

    public static DatabaseDriver getPostgresDriver() {
        return getPostgresDriver(true);
    }

    /**
     * Get a driver for the default test Postgres database.
     * Will return null if the postrges database could not be connected to.
     * This means that the test should be skipped.
     */
    public static DatabaseDriver getPostgresDriver(boolean clear) {
        try {
            return new PostgreSQLDriver(DB_NAME, clear);
        } catch (RuntimeException ex) {
            // Check to see if we failed to connect because the server is down.
            Throwable currentException = ex;
            while (currentException != null) {
                if (currentException instanceof java.net.ConnectException &&
                        currentException.getMessage().contains("Connection refused")) {
                    System.out.println("Skipping Postgres test... cannot connect to database.");
                    return null;
                }
                currentException = currentException.getCause();
            }

            // We failed to connect, but not because the server is down.
            // Rethrown the exception.
            throw ex;
        }
    }

    public static void cleanH2Driver() {
        (new File(DB_BASE_PATH + ".h2.db")).delete();
        (new File(DB_BASE_PATH + ".trace.db")).delete();
    }

    public static void cleanPostgresDriver() {
    }
}
