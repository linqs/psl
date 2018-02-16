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
}
