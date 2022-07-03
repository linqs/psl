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

import org.linqs.psl.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A histrogram that represents the distribution of values in a column.
 * The DBMS may return this information as either buckets or specific values (if there are not that many unique values).
 * This class will handle both and estimate join sizes between other histograms.
 *
 * If the conlumn type is an int, then a bucket will be assumed to be uniformly distributed.
 * This will typically result in more accurate estimates.
 *
 * Internally, this class will use BigInteger.
 * However, all APIs will use primitive or wrapper types to make it easy on the user.
 */
public class SelectivityHistogram<T extends Comparable<? super T>> {
    /**
     * A guess about how much of a bucket is in use when there is some overlap.
     * This is only used if we have no information about the actual width of a bucket.
     */
    public static final double BUCKET_USAGE_GUESS = 0.5;

    /**
     * The values we see must all be the same exact class.
     */
    private Class columnType;

    /**
     * Valid histograms may be no entries in them (especially after joins).
     */
    private boolean isEmpty;

    /**
     * The boundaries in a historgram of values for each column.
     * Although start/end boundaries are specified,
     * any values past those extremes will be moved into the first/last bucket.
     * Assume that the bounds are [inclusive, exclusive).
     * Except for the very last bound, which will be included in the last bucket.
     * This find distinction will only actually apply when working with int bounds.
     */
    private List<T> histogramBounds;
    private List<BigInteger> histogramCounts;

    /**
     * If the cardinality of a column is low, then the database may just report those unique values.
     * In this case, we can build a exact distribution of the column.
     */
    private Map<T, BigInteger> exactHistogram;
    private List<T> sortedExactHistogramKeys;

    public SelectivityHistogram() {
        this(false);
    }

    private SelectivityHistogram(boolean isEmpty) {
        columnType = null;
        this.isEmpty = isEmpty;

        histogramBounds = null;
        histogramCounts = null;

        exactHistogram = null;
        sortedExactHistogramKeys = null;
    }

    public void addHistogramBounds(List<T> bounds, List<? extends Number> counts) {
        if (bounds.size() == 0 || counts.size() == 0) {
            isEmpty = true;
            return;
        }

        assert(bounds.size() == counts.size() + 1);
        assert(bounds.size() >= 2);

        histogramBounds = new ArrayList<T>(bounds);
        histogramCounts = new ArrayList<BigInteger>(counts.size());

        for (Number count : counts) {
            if (count instanceof BigInteger) {
                histogramCounts.add((BigInteger)count);
            } else {
                histogramCounts.add(BigInteger.valueOf(count.longValue()));
            }
        }

        checkTypes(histogramBounds);
    }

    public void addHistogramExact(Map<T, ? extends Number> histogram) {
        if (histogram.size() == 0) {
            isEmpty = true;
            return;
        }

        assert(histogram.size() > 0);

        exactHistogram = new HashMap<T, BigInteger>();

        for (Map.Entry<T, ? extends Number> entry : histogram.entrySet()) {
            T key = entry.getKey();
            Number count = entry.getValue();

            if (count instanceof BigInteger) {
                exactHistogram.put(key, (BigInteger)count);
            } else {
                exactHistogram.put(key, BigInteger.valueOf(count.longValue()));
            }
        }

        sortedExactHistogramKeys = new ArrayList<T>(histogram.keySet());
        Collections.sort(sortedExactHistogramKeys);

        checkTypes(sortedExactHistogramKeys);
    }

    private void checkTypes(Iterable<T> values) {
        for (T value : values) {
            if (columnType == null) {
                columnType = value.getClass();
            }

            if (value.getClass() != columnType) {
                throw new IllegalArgumentException(String.format(
                        "Inconsistent types. Expected %s, found %s.",
                        columnType.getName(), value.getClass().getName()));
            }
        }
    }

    /**
     * Get the number of rows represented by this histogram.
     * For histograms created directly from database tables, the size should be equal to the table count.
     * Note that this method is O(n).
     * Overflows will return Long.MAX_VALUE.
     */
    public long size() {
        if (!isValid() || isEmpty) {
            return 0;
        }

        BigInteger count = BigInteger.ZERO;

        if (exactHistogram != null) {
            for (BigInteger bucketCount : exactHistogram.values()) {
                count = count.add(bucketCount);
            }
        } else {
            for (BigInteger bucketCount : histogramCounts) {
                count = count.add(bucketCount);
            }
        }

        return count.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    /**
     * A histogram is not valid until either addHistogramBounds() or addHistogramExact() is called.
     * Unless it is empty, empty histograms are always valid.
     */
    public boolean isValid() {
        return isEmpty || (columnType != null);
    }

    /**
     * Get a new histogram that represents the join of this histogram with another.
     */
    public SelectivityHistogram<T> join(SelectivityHistogram<T> other) {
        if (!isValid() || !other.isValid()) {
            throw new IllegalArgumentException("Connot compute join on invalid histograms.");
        }

        if (isEmpty || other.isEmpty) {
            return new SelectivityHistogram<T>(true);
        }

        // Make sure the classes match exactly (referential equality works on Class).
        if (columnType != other.columnType) {
            throw new IllegalArgumentException(String.format(
                    "Both histograms must match column type exactly. Got %s and %s.",
                    columnType.getName(),
                    other.columnType.getName()));
        }

        if (exactHistogram != null && other.exactHistogram != null) {
            return computeExactJoin(other);
        }

        if (exactHistogram != null) {
            return computeExactBucketJoin(other);
        }

        if (other.exactHistogram != null) {
            return other.computeExactBucketJoin(this);
        }

        return computeBucketJoin(other);
    }

    public String toString() {
        if (isEmpty) {
            return "Empty Histogram";
        }

        if (!isValid()) {
            return "Invalid Histogram";
        }

        StringBuilder builder = new StringBuilder();

        if (exactHistogram != null) {
            for (int i = 0; i < sortedExactHistogramKeys.size(); i++) {
                T exactValue = sortedExactHistogramKeys.get(i);

                if (i != 0) {
                    builder.append(", ");
                }

                builder.append(exactValue);
                builder.append(" (" + exactHistogram.get(exactValue) + ")");
            }

            if (histogramBounds != null) {
                builder.append(System.lineSeparator());
            }
        }

        if (histogramBounds != null) {
            for (int i = 0; i < histogramCounts.size(); i++) {
                if (i != 0) {
                    builder.append(", ");
                }

                T bucketStart = histogramBounds.get(i + 0);
                T bucketEnd = histogramBounds.get(i + 1);

                builder.append("[" + bucketStart + ", " + bucketEnd + "): " + histogramCounts.get(i));
            }
        }

        return builder.toString();
    }

    /**
     * Estimate the join size where both histograms are exact ones.
     */
    private SelectivityHistogram<T> computeExactJoin(SelectivityHistogram<T> other) {
        Map<T, BigInteger> result = new HashMap<T, BigInteger>();

        for (Map.Entry<T, BigInteger> entry : exactHistogram.entrySet()) {
            T columnValue = entry.getKey();
            BigInteger count = entry.getValue();

            if (other.exactHistogram.containsKey(columnValue)) {
                result.put(columnValue, count.multiply(other.exactHistogram.get(columnValue)));
            }
        }

        SelectivityHistogram<T> histogram = new SelectivityHistogram<T>();
        histogram.addHistogramExact(result);
        return histogram;
    }

    /**
     * Estimate the join size where the context histogram (this) is an exact one
     * and the other histogram is a bucket histogram.
     */
    private SelectivityHistogram<T> computeExactBucketJoin(SelectivityHistogram<T> other) {
        Map<T, BigInteger> result = new HashMap<T, BigInteger>();

        int currentExactIndex = 0;
        int bucketIndex = 0;

        while (true) {
            // If we examined all the exact values, then we are done.
            if (currentExactIndex == sortedExactHistogramKeys.size()) {
                break;
            }
            T currentExactValue = sortedExactHistogramKeys.get(currentExactIndex);

            // If there are no more buckets, then the exact value must be in the last bucket.
            if (bucketIndex == other.histogramCounts.size() - 1) {
                currentExactIndex++;
                BigInteger bucketCount = other.bucketOverlap(
                        currentExactValue, currentExactValue,
                        other.histogramBounds.get(bucketIndex + 0), other.histogramBounds.get(bucketIndex + 1),
                        other.histogramCounts.get(bucketIndex));

                result.put(currentExactValue, bucketCount.multiply(exactHistogram.get(currentExactValue)));

                continue;
            }

            T bucketStartValue = other.histogramBounds.get(bucketIndex + 0);
            T bucketEndValue = other.histogramBounds.get(bucketIndex + 1);

            // If the current value is past this bucket, then move the bucket forward.
            if (currentExactValue.compareTo(bucketEndValue) > 0) {
                bucketIndex++;
                continue;
            }

            // Now the exact value must be either before or in this bucket.
            // It is only possible to be before this bucket if this is the first bucket.
            // Either way, put the exact value in this bucekt.

            currentExactIndex++;
            BigInteger bucketCount = other.bucketOverlap(
                    currentExactValue, currentExactValue,
                    bucketStartValue, bucketEndValue,
                    other.histogramCounts.get(bucketIndex));

            result.put(currentExactValue, bucketCount.multiply(exactHistogram.get(currentExactValue)));
        }

        SelectivityHistogram<T> histogram = new SelectivityHistogram<T>();
        histogram.addHistogramExact(result);
        return histogram;
    }

    /**
     * Estimate the join size where both histograms are bucket ones.
     */
    private SelectivityHistogram<T> computeBucketJoin(SelectivityHistogram<T> other) {
        // The buckets are required to be contiguous, so we may have some buckets with zero counts.
        List<T> bounds = new ArrayList<T>();
        List<BigInteger> counts = new ArrayList<BigInteger>();
        boolean emptyBucket = false;

        int contextBucketIndex = 0;
        int otherBucketIndex = 0;

        // Because we have no guarentees on the size or overlap of the buckets,
        // we cannot just loop over the buckets.
        // Instead, we have to move along and advance each bucket indivudually.
        T currentRangeStart = null;
        T currentRangeEnd = null;

        // In each loop, we will move one bucket forward.
        // If there is an overlap, we may also add to our row count.
        while (true) {
            // Stop if either bucket is out of range.
            if (contextBucketIndex == histogramCounts.size() || otherBucketIndex == other.histogramCounts.size()) {
                break;
            }

            T contextBucketStart = histogramBounds.get(contextBucketIndex + 0);
            T contextBucketEnd = histogramBounds.get(contextBucketIndex + 1);
            BigInteger contextCount = histogramCounts.get(contextBucketIndex);

            T otherBucketStart = other.histogramBounds.get(otherBucketIndex + 0);
            T otherBucketEnd = other.histogramBounds.get(otherBucketIndex + 1);
            BigInteger otherCount = other.histogramCounts.get(otherBucketIndex);

            // Start at the further forward of the bucket starts.
            int startComparison = contextBucketStart.compareTo(otherBucketStart);
            if (startComparison < 0) {
                currentRangeStart = otherBucketStart;
            } else {
                currentRangeStart = contextBucketStart;
            }

            // End at the earlier of the bucket ends.
            int endComparison = contextBucketEnd.compareTo(otherBucketEnd);
            if (endComparison < 0) {
                currentRangeEnd = contextBucketEnd;
            } else {
                currentRangeEnd = otherBucketEnd;
            }

            // Now move the bucket that ends first forward.
            if (endComparison <= 0) {
                // Move the context bucket.
                contextBucketIndex++;
            }

            if (endComparison >= 0) {
                // Move the other bucket.
                otherBucketIndex++;
            }

            // If there is no overlap, just move to the next range.
            if (currentRangeStart.compareTo(currentRangeEnd) > 0) {
                emptyBucket = true;
                continue;
            }

            // Compute how much of each bucket the range is overlapping.
            BigInteger contextBucketCount = bucketOverlap(
                    currentRangeStart, currentRangeEnd,
                    contextBucketStart, contextBucketEnd,
                    contextCount);

            BigInteger otherBucketCount = other.bucketOverlap(
                    currentRangeStart, currentRangeEnd,
                    otherBucketStart, otherBucketEnd,
                    otherCount);

            // Make sure to add in the first bound.
            // We also want to make sure that we explicitly add in buckets that are empty,
            // so we can make out non-empty buckets as small as possible.
            if (bounds.size() == 0 || emptyBucket) {
                emptyBucket = false;
                bounds.add(currentRangeStart);
            }

            bounds.add(currentRangeEnd);
            counts.add(contextBucketCount.multiply(otherBucketCount));
        }

        SelectivityHistogram<T> histogram = new SelectivityHistogram<T>();
        histogram.addHistogramBounds(bounds, counts);
        return histogram;
    }

    /**
     * Estimate how much a bucket overlaps with some range.
     */
    private BigInteger bucketOverlap(T rangeStart, T rangeEnd, T bucketStart, T bucketEnd, BigInteger bucketCount) {
        // We have two general cases: the entire bucket is used or a portion of the bucket is being used.
        BigDecimal floatBucketCount = new BigDecimal(bucketCount);

        // All the bucket is being used.
        if (rangeStart.compareTo(bucketStart) < 0 && rangeEnd.compareTo(bucketEnd) > 0) {
            return bucketCount;
        }

        // A portion of the bucket is being used.

        // If we are dealing with ints, then we can compute the portion of the bucket being used.
        // Just assume a uniform distribution over the bucket.
        if (columnType == Integer.class) {
            int bucketSize = ((Integer)bucketEnd).intValue() - ((Integer)bucketStart).intValue() + 1;

            int overlapStart = Math.max(((Integer)rangeStart).intValue(), ((Integer)bucketStart).intValue());
            int overlapEnd = Math.min(((Integer)rangeEnd).intValue(), ((Integer)bucketEnd).intValue());
            int overlapSize = overlapEnd - overlapStart;

            // If we are comparing with an exact value, we will pass the same values for the range start/end.
            // In this case, we will want to add one tot he overlap.
            if (overlapSize == 0) {
                overlapSize = 1;
            }

            BigDecimal count = floatBucketCount.multiply(BigDecimal.valueOf((double)overlapSize / bucketSize));
            return count.setScale(0, RoundingMode.CEILING).toBigInteger();
        }

        // If we are using strings, then we cannot make any assumptions about the width
        // of the bucket and we will just use our standard load factor.

        BigDecimal count = floatBucketCount.multiply(BigDecimal.valueOf(BUCKET_USAGE_GUESS));
        return count.setScale(0, RoundingMode.CEILING).toBigInteger();
    }
}
