/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.config.ConfigBundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class MemoryTermStore<E extends Term> implements TermStore<E> {
	public static final String CONFIG_PREFIX = "memorytermstore";

	/**
	 * Initial size for the memory store.
	 */
	public static final String INITIAL_SIZE_KEY = CONFIG_PREFIX + ".initialsize";
	public static final int INITIAL_SIZE_DEFAULT = 5000;

	private List<E> store;

	public MemoryTermStore() {
		this(INITIAL_SIZE_DEFAULT);
	}

	public MemoryTermStore(ConfigBundle config) {
		this(config.getInt(INITIAL_SIZE_KEY, INITIAL_SIZE_DEFAULT));
	}

	public MemoryTermStore(int initialSize) {
		store = new ArrayList<E>(initialSize);
	}

	@Override
	public void add(E term) {
		store.add(term);
	}

	@Override
	public void clear() {
		store.clear();
	}

	@Override
	public void close() {
		clear();
		store = null;
	}

	@Override
	public E get(int index) {
		return store.get(index);
	}

	@Override
	public int size() {
		return store.size();
	}

	@Override
	public Iterator<E> iterator() {
		return store.iterator();
	}
}
