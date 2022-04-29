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
package org.linqs.psl.database.rdbms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A container for statistics about a table.
 * This container is NOT case sensitive, do not rely on case for anything.
 */
public class TableStats {
    private int count;

    /**
     * Selectivity by column.
     * [0, 1].
     */
    private Map<String, Double> selectivity;

    private Map<String, SelectivityHistogram> histograms;

    public TableStats(int count) {
        this.count = count;
        this.selectivity = new HashMap<String, Double>();
        this.histograms = new HashMap<String, SelectivityHistogram>();
    }

    public void addColumnSelectivity(String column, double columnSelectivity) {
        column = column.toUpperCase();
        selectivity.put(column, Double.valueOf(columnSelectivity));
    }

    public void addColumnHistogram(String column, SelectivityHistogram histogram) {
        column = column.toUpperCase();
        histograms.put(column, histogram);
    }

    public int getCount() {
        return count;
    }

    public SelectivityHistogram getHistogram(String column) {
        column = column.toUpperCase();
        return histograms.get(column);
    }

    /**
     * Get the selectivity (cardinality / total rows) of a specific column.
     */
    public double getSelectivity(String column) {
        return selectivity.get(column.toUpperCase()).doubleValue();
    }

    /**
     * Get the cardinality (number of unique rows) of a specific column.
     */
    public int getCardinality(String column) {
        return (int)(selectivity.get(column.toUpperCase()).doubleValue() * count);
    }
}
