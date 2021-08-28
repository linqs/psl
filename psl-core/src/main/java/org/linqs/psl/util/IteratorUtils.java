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
package org.linqs.psl.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Various static iterator/iterable utilities.
 */
public final class IteratorUtils {
    // Static only.
    private IteratorUtils() {}

    /**
     * Given an Iterable, return a new Iterable that only returns values that are an instance of the provided class.
     */
    public static <T, S> Iterable<S> filterClass(Iterable<T> baseIterable, Class<S> targetClass) {
        final Class<S> finalTargetClass = targetClass;

        Iterable<S> classMatch = map(baseIterable, new MapFunction<T, S>() {
            @Override
            public S map(T obj) {
                if (finalTargetClass.isInstance(obj)) {
                    @SuppressWarnings("unchecked")
                    S ignoreException = (S)obj;
                    return ignoreException;
                }

                return null;
            }
        });

        return filter(classMatch, new FilterFunction<S>() {
            @Override
            public boolean keep(S obj) {
                return obj != null;
            }
        });
    }

    /**
     * Given an Iterable, return a new Iterable that invokes the function once on each item.
     */
    public static <T, S> Iterable<S> map(Iterable<T> baseIterable, MapFunction<T, S> mapFunction) {
        return new MapIterable<T, S>(baseIterable, mapFunction);
    }

    /**
     * Given an Iterable, return a new Iterable that filters baseed off of some function.
     */
    public static <T> Iterable<T> filter(Iterable<T> baseIterable, FilterFunction<T> filter) {
        return new FilterIterable<T>(baseIterable, filter);
    }

    /**
     * Make an Iterable from an Interator.
     * Note that the exact same iterator will be returned on each call to iterator().
     * This may be unexpected for callers that want to restart iteration from the beginning.
     */
    public static <T> Iterable<T> newIterable(Iterator<T> items) {
        final Iterator<T> finalItems = items;

        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return finalItems;
            }
        };
    }

    /**
     * Get an iterable over all the given iterables in whatever iteration order each provides.
     * It is up to the caller to make sure the underlying iterables are not changed during iteration.
     * The benefit of using this is that is does not perform variable allocations.
     */
    @SafeVarargs
    public static <T> Iterable<T> join(Iterable<? extends T>... collections) {
        return new ConcatenationIterable<T>(collections);
    }

    /**
     * Get an iterator over all the given iterators in whatever iteration order each provides.
     */
    @SafeVarargs
    public static <T> Iterator<T> join(Iterator<? extends T>... iterators) {
        @SuppressWarnings("unchecked")
        Iterable<? extends T>[] iterables = new Iterable[iterators.length];
        for (int i = 0; i < iterators.length; i++) {
            iterables[i] = newIterable(iterators[i]);
        }

        return new ConcatenationIterator<T>(iterables);
    }

    /**
     * Get an iterator that gives all the permutations of the numbers 0 - size.
     */
    public static Iterator<int[]> permutations(int size) {
        return new PermutationIterator(size);
    }

    /**
     * Get an iterator that will go through the powerset of the specified size.
     * A true in the array means membership in the subset.
     * The iterator retains ownership of the array and will pass back the same
     * (but modified) array each time.
     */
    public static Iterable<boolean[]> powerset(int size) {
        final int finalSize = size;
        return new Iterable<boolean[]>() {
            @Override
            public Iterator<boolean[]> iterator() {
                return new PowerSetIterator(finalSize);
            }
        };
    }

    /**
     * Get an iterator that will go through the numbers [0, amount).
     */
    public static Iterator<Integer> count(int amount) {
        return count(0, amount);
    }

    /**
     * Get an iterator that will go through the numbers [start, start + amount).
     */
    public static Iterator<Integer> count(int start, int amount) {
        assert(amount >= 0);
        return new CountingIterator(start, amount);
    }

    /**
     * Convert an iterable to a persisted list (LinkedList).
     */
    public static <T> List<T> toList(Iterable<T> elements) {
        return toList(elements.iterator());
    }

    /**
     * Convert an iterator to a persisted list (LinkedList).
     */
    public static <T> List<T> toList(Iterator<T> elements) {
        List<T> list = new LinkedList<T>();

        while (elements.hasNext()) {
            list.add(elements.next());
        }

        return list;
    }

    public static interface MapFunction<T, S> {
        public S map(T value);
    }

    public static interface FilterFunction<T> {
        public boolean keep(T value);
    }

    private static class MapIterable<T, S> implements Iterable<S> {
        private Iterable<T> baseIterable;
        private MapFunction<T, S> mapFunction;

        public MapIterable(Iterable<T> baseIterable, MapFunction<T, S> mapFunction) {
            this.baseIterable = baseIterable;
            this.mapFunction = mapFunction;
        }

        @Override
        public Iterator<S> iterator() {
            return new MapIterator<T, S>(baseIterable, mapFunction);
        }
    }

    private static class MapIterator<T, S> implements Iterator<S> {
        private Iterator<T> baseIterator;
        private MapFunction<T, S> mapFunction;
        private S nextValue;
        private boolean hasNextValue;

        public MapIterator(Iterable<T> baseIterable, MapFunction<T, S> mapFunction) {
            this.baseIterator = baseIterable.iterator();
            this.mapFunction = mapFunction;

            primeNext();
        }

        private void primeNext() {
            if (baseIterator.hasNext()) {
                nextValue = mapFunction.map(baseIterator.next());
                hasNextValue = true;
                return;
            }

            hasNextValue = false;
        }

        @Override
        public boolean hasNext() {
            return hasNextValue;
        }

        @Override
        public S next() {
            if (!hasNext()) {
                throw new IllegalStateException("Called next() when hasNext() == false.");
            }

            S rtn = nextValue;
            primeNext();
            return rtn;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class FilterIterable<T> implements Iterable<T> {
        private Iterable<T> baseIterable;
        private FilterFunction<T> filter;

        public FilterIterable(Iterable<T> baseIterable, FilterFunction<T> filter) {
            this.baseIterable = baseIterable;
            this.filter = filter;
        }

        @Override
        public Iterator<T> iterator() {
            return new FilterIterator<T>(baseIterable, filter);
        }
    }

    private static class FilterIterator<T> implements Iterator<T> {
        private Iterator<T> baseIterator;
        private FilterFunction<T> filter;
        private T nextValue;

        public FilterIterator(Iterable<T> baseIterable, FilterFunction<T> filter) {
            this.baseIterator = baseIterable.iterator();
            this.filter = filter;

            primeNext();
        }

        private void primeNext() {
            while (baseIterator.hasNext()) {
                nextValue = baseIterator.next();

                if (filter.keep(nextValue)) {
                    return;
                }
            }

            nextValue = null;
        }

        @Override
        public boolean hasNext() {
            return nextValue != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new IllegalStateException("Called next() when hasNext() == false.");
            }

            T rtn = nextValue;
            primeNext();
            return rtn;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class ConcatenationIterable<T> implements Iterable<T> {
        private Iterable<? extends T>[] collections;

        public ConcatenationIterable(Iterable<? extends T>[] collections) {
            this.collections = collections;
        }

        @Override
        public Iterator<T> iterator() {
            return new ConcatenationIterator<T>(collections);
        }
    }

    private static class ConcatenationIterator<T> implements Iterator<T> {
        private Iterable<? extends T>[] collections;
        private int collectionIndex;
        private Iterator<? extends T> currentIterator;

        public ConcatenationIterator(Iterable<? extends T>[] collections) {
            this.collections = collections;
            collectionIndex = -1;
            currentIterator = null;

            primeNext();
        }

        private void primeNext() {
            // If the current iterator is good to go, then leave it alone.
            if (currentIterator != null && currentIterator.hasNext()) {
                return;
            }

            // Move to the next collection.
            collectionIndex++;

            // If we are out of bounds, we are done.
            if (collectionIndex >= collections.length) {
                currentIterator = null;
                return;
            }

            currentIterator = collections[collectionIndex].iterator();

            // This iterator may be empty, so just try to prime again.
            primeNext();
        }

        @Override
        public boolean hasNext() {
            // If primeNext() does not set a null iterator, then we have a next.
            return currentIterator != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new IllegalStateException("Called next() when hasNext() == false.");
            }

            T rtn = currentIterator.next();
            primeNext();
            return rtn;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class PowerSetIterator implements Iterator<boolean[]> {
        private final int size;
        private long count;
        private boolean[] currentSubset;

        public PowerSetIterator(int size) {
            if (size < 1) {
                throw new IllegalArgumentException(
                        "Power sets require a positive int size, found: " + size);
            }

            if (size > 63) {
                throw new IllegalArgumentException(
                        "Powersets on sets larger than 63 (a long) are not supported, found: " + size);
            }

            this.size = size;
            count = 0;
            currentSubset = new boolean[size];
        }

        @Override
        public boolean[] next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }

            long mask = 1;
            for (int i = 0; i < size; i++) {
                currentSubset[i] = ((count & mask) != 0);
                mask = mask << 1;
            }

            count++;

            return currentSubset;
        }

        @Override
        public boolean hasNext() {
            return count < (int)Math.pow(2, size);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class CountingIterator implements Iterator<Integer> {
        private final int end;

        private int next;

        public CountingIterator(int start, int count) {
            next = start;
            end = start + count;
        }

        @Override
        public Integer next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }

            return Integer.valueOf(next++);
        }

        @Override
        public boolean hasNext() {
            return next < end;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    // Taken from: https://stackoverflow.com/a/11916946
    // TODO(eriq): This could use a lot of cleanup.
    private static class PermutationIterator implements Iterator<int[]> {
        private final int size;

        private int[] next = null;
        private int[] permutations;
        private int[] directions;

        public PermutationIterator(int size) {
            if (size < 1) {
                throw new IllegalArgumentException("Permutation size must be at least 1.");
            }

            this.size = size;

            permutations = new int[size];
            directions = new int[size];

            for (int i = 0; i < size; i++) {
                permutations[i] = i;
                directions[i] = -1;
            }
            directions[0] = 0;

            next = permutations;
        }

        @Override
        public int[] next() {
            int[] rtn = makeNext();
            next = null;

            return rtn;
        }

        @Override
        public boolean hasNext() {
            return makeNext() != null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private int[] makeNext() {
            if (next != null) {
                return next;
            }

            if (permutations == null) {
                return null;
            }

            // find the largest element with != 0 direction
            int i = -1;
            int e = -1;
            for (int j = 0; j < size; j++) {
                if ((directions[j] != 0) && (permutations[j] > e)) {
                    e = permutations[j];
                    i = j;
                }
            }

            // no such element -> no more premutations
            if (i == -1) {
                next = null;
                permutations = null;
                directions = null;

                return next;
            }

            // swap with the element in its direction
            int k = i + directions[i];
            swap(i, k, directions);
            swap(i, k, permutations);
            // if it's at the start/end or the next element in the direction
            // is greater, reset its direction.
            if ((k == 0) || (k == size-1) || (permutations[k + directions[k]] > e)) {
                directions[k] = 0;
            }

            // set directions to all greater elements
            for (int j = 0; j < size; j++) {
                if (permutations[j] > e) {
                    directions[j] = (j < k) ? +1 : -1;
                }
            }

            next = permutations;
            return next;
        }

        private static void swap(int i, int j, int[] data) {
            int temp = data[i];
            data[i] = data[j];
            data[j] = temp;
        }
    }
}
