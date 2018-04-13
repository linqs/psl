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
package org.linqs.psl.util;

import java.util.Iterator;

/**
 * Various static iterator/iterable utilities.
 */
public final class IteratorUtils {
	// Static only.
	private IteratorUtils() {}

	/**
	 * Get an iterator that iterates over all the given iterables in whatever iteration order each provides.
	 * It is up to the caller to make sure the underlying iterables are not changed during iteration.
	 * The benefit of using this is that is does not perform variable allocations.
	 */
	@SafeVarargs
	public static <T> Iterable<T> join(Iterable<? extends T>... collections) {
		return new ConcatenationIterable<T>(collections);
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
			// If primeNext() does not set a null iteraotr, then we have a next.
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
