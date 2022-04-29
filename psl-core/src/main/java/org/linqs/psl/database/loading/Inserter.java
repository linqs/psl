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
package org.linqs.psl.database.loading;

import org.linqs.psl.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public abstract class Inserter {
    private static final Logger log = LoggerFactory.getLogger(Inserter.class);

    public static final String DEFAULT_DELIMITER = "\t";

    private final int arity;

    public Inserter(int arity) {
        this.arity = arity;
    }

    /**
     * Insert a single object using the default truth value.
     */
    public void insert(Object... data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Attempted to insert empty data.");
        }

        List<List<Object>> newData = new ArrayList<List<Object>>(1);
        newData.add(Arrays.asList(data));
        insertAll(newData);
    }

    /**
     * Insert a single object using the specified truth value.
     */
    public void insertValue(double value, Object... data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Attempted to insert empty data.");
        }

        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("Invalid truth value: " + value + ". Must be between 0 and 1 inclusive.");
        }

        List<List<Object>> newData = new ArrayList<List<Object>>(1);
        newData.add(Arrays.asList(data));

        List<Double> newValue = new ArrayList<Double>(1);
        newValue.add(value);

        insertAllValues(newValue, newData);
    }

    /**
     * Load data without a truth value from a file.
     */
    public void loadDelimitedData(String path) {
        loadDelimitedData(path, DEFAULT_DELIMITER);
    }

    public void loadDelimitedData(String path, String delimiter) {
        if (supportsBulkCopy()) {
            bulkCopy(path, delimiter, false);
            return;
        }

        List<List<Object>> data = loadDelimitedDataInternal(path, delimiter);
        insertAll(data);
    }

    /**
     * Load data with a truth value from a file.
     */
    public void loadDelimitedDataTruth(String path) {
        loadDelimitedDataTruth(path, DEFAULT_DELIMITER);
    }

    public void loadDelimitedDataTruth(String path, String delimiter) {
        if (supportsBulkCopy()) {
            bulkCopy(path, delimiter, true);
            return;
        }

        List<List<Object>> data = loadDelimitedDataInternal(path, delimiter);
        List<Double> values = new ArrayList<Double>(data.size());

        for (int i = 0; i < data.size(); i++) {
            List<Object> row = data.get(i);

            double truth;
            try {
                truth = Double.parseDouble((String)row.get(row.size() - 1));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Could not read truth value for row " + (i + 1) + ": " + row.get(row.size() - 1) + " -- " + path, ex);
            }

            if (truth < 0.0 || truth > 1.0) {
                throw new IllegalArgumentException("Illegal truth value encountered on row " + (i + 1) + ": " + truth + " -- " + path);
            }

            // Remove the truth value from the list by taking a sublist (should not cause any additional allocation).
            data.set(i, row.subList(0, row.size() - 1));
            values.add(truth);
        }

        insertAllValues(values, data);
    }

    /**
     * Peek at the first line and then choose loadDelimitedData() or loadDelimitedDataTruth().
     * Because of the need to open the file and peek, this will always be slower than either direct option.
     */
    public void loadDelimitedDataAutomatic(String path) {
        loadDelimitedDataAutomatic(path, DEFAULT_DELIMITER);
    }

    public void loadDelimitedDataAutomatic(String path, String delimiter) {
        boolean hasTruth = false;
        try (BufferedReader reader = FileUtils.getBufferedReader(path)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(delimiter);
                hasTruth = (parts.length > arity);
                break;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse delimited file: " + path, ex);
        }

        if (hasTruth) {
            loadDelimitedDataTruth(path, delimiter);
        } else {
            loadDelimitedData(path, delimiter);
        }
    }

    /**
     * Parse a file and get the parts of each row.
     * Each object returned will be a string, but we are returning Objects so the inserter can take it in directly.
     */
    private static List<List<Object>> loadDelimitedDataInternal(String path, String delimiter) {
        List<List<Object>> rows = new ArrayList<List<Object>>();

        try (BufferedReader reader = FileUtils.getBufferedReader(path)) {
            String line = null;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                Object[] data = (Object[])line.split(delimiter);
                rows.add(Arrays.asList(data));
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse delimited file: " + path, ex);
        }

        return rows;
    }

    /**
     * Some inserters backed with specific databases can do bulk copy operations.
     */
    public abstract boolean supportsBulkCopy();

    /**
     * Import the file directly into the database.
     */
    public abstract void bulkCopy(String path, String delimiter, boolean hasTruth);

    /**
     * Insert several objects using the default truth value.
     */
    public abstract void insertAll(List<List<Object>> data);

    /**
     * Insert several objects using the specified truth values.
     */
    public abstract void insertAllValues(List<Double> values, List<List<Object>> data);
}
