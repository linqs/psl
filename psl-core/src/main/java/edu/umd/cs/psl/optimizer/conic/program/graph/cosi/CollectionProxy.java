/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.optimizer.conic.program.graph.cosi;

import java.util.Collection;
import java.util.Iterator;

import edu.umd.cs.psl.optimizer.conic.program.graph.Node;

class CollectionProxy<TO extends Node> implements Collection<TO> {
	protected COSIGraph graph;
	protected Collection<? extends edu.umd.umiacs.dogma.diskgraph.core.Node> collection;
	
	protected CollectionProxy(COSIGraph g, Collection<? extends edu.umd.umiacs.dogma.diskgraph.core.Node> c) {
		graph = g;
		collection = c;
	}
	
	@Override
	public boolean add(TO e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection<? extends TO> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		collection.clear();
	}

	@Override
	public boolean contains(Object o) {
		if (o instanceof COSINode)
			return collection.contains(((COSINode) o).node);
		else
			return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		boolean toReturn = true;
		for (Object o : c)
			toReturn = contains(o) && toReturn;
		return toReturn;
	}

	@Override
	public boolean isEmpty() {
		return isEmpty();
	}

	@Override
	public Iterator<TO> iterator() {
		return new IteratorProxy<TO>(graph, collection.iterator());
	}

	@Override
	public boolean remove(Object o) {
		if (o instanceof COSINode)
			return collection.remove(((COSINode) o).node);
		else
			return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean toReturn = false;
		for (Object o : c)
			toReturn = remove(o) || toReturn;
		return toReturn;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return collection.size();
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new UnsupportedOperationException();
	}
}
