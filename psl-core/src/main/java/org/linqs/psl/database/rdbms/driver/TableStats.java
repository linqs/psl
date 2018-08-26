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
package org.linqs.psl.database.rdbms.driver;

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

	/**
	 * The boundaries in a historgram of values for each column.
	 * Although start/end boundaries are specified,
	 * any values past those extremes will be moved into the first/last bucket.
	 */
	private Map<String, List<Comparable>> histogramBounds;

	/**
	 * The counts for each histogram bucket.
	 */
	private Map<String, List<Integer>> histogramCounts;

	public TableStats(int count) {
		this.count = count;
		this.selectivity = new HashMap<String, Double>();
		this.histogramBounds = new HashMap<String, List<Comparable>>();
		this.histogramCounts = new HashMap<String, List<Integer>>();
	}

	public void addColumnSelectivity(String column, double columnSelectivity) {
		column = column.toUpperCase();
		this.selectivity.put(column, new Double(columnSelectivity));
	}

	public void addColumnHistogram(String column, List<Comparable> bounds, List<Integer> counts) {
		column = column.toUpperCase();
		histogramBounds.put(column, bounds);
		histogramCounts.put(column, counts);
	}

	public int getCount() {
		return count;
	}

	/**
	 * Get the selectivity of a range of values for a column.
	 * For this, we will look up the matching bucket(s) in the histogram.
	 * If either bound is null or past the extremes, assume that it is the extreme.
	 * Will fallback to normal selectivity if the histogram is not available.
	 */
	public double getHistogramSelectivity(String column, Comparable lowerValue, Comparable upperValue) {
		if (lowerValue.getClass() != upperValue.getClass()) {
			throw new IllegalArgumentException(String.format(
					"Lower and upper value types must match. Got %s and %s.",
					lowerValue.getClass().getName(),
					upperValue.getClass().getName()));
		}

		column = column.toUpperCase();

		List<Comparable> bounds = histogramBounds.get(column);
		List<Integer> counts = histogramCounts.get(column);

		if (bounds == null || bounds.size() == 0) {
			return getSelectivity(column);
		}

		if (lowerValue == null || uncheckedCompare(lowerValue, bounds.get(0)) < 0) {
			lowerValue = bounds.get(0);
		}

		if (upperValue == null || uncheckedCompare(upperValue, bounds.get(bounds.size() - 1)) > 0) {
			upperValue = bounds.get(bounds.size() - 1);
		}

		if (lowerValue instanceof Integer) {
			return getHistogramSelectivity(column, ((Integer)lowerValue).intValue(), ((Integer)upperValue).intValue());
		}

		int totalCount = 0;
		Comparable currentValue = lowerValue;

		for (int bucket = 1; bucket < bounds.size(); bucket++) {
			// If the current value is in this bucket, than add the count to the total.
			// Note that we started at index 1 and are only looking at the end.
			if (uncheckedCompare(currentValue, bounds.get(bucket)) < 0) {
				totalCount += counts.get(bucket).intValue();
			}
		}

		return (double)totalCount / count;
	}

	private double getHistogramSelectivity(String column, int lowerValue, int upperValue) {
		column = column.toUpperCase();

		List<Comparable> bounds = histogramBounds.get(column);
		List<Integer> counts = histogramCounts.get(column);

		int totalCount = 0;
		int currentValue = lowerValue;

		for (int bucket = 0; bucket < bounds.size() - 1; bucket++) {
			int bucketStart = ((Integer)bounds.get(bucket)).intValue();
			int bucketEnd = ((Integer)bounds.get(bucket + 1)).intValue();

			if (currentValue > bucketEnd) {
				break;
			}

			// Assume a uniform distribution within the bucket.
			totalCount += (double)(currentValue - bucketStart) / (bucketEnd - bucket) * counts.get(bucket);
		}

		return (double)totalCount / count;
	}

	@SuppressWarnings("unchecked")
	private int uncheckedCompare(Comparable a, Object b) {
		return a.compareTo(b);
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
