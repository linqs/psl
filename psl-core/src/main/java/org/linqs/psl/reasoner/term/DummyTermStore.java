/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.rule.GroundRule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A term store that does not actually store terms.
 * This is intended for testing.
 * The number of ground rules seen will be tracked (and returned via size()).
 */
public class DummyTermStore<T extends ReasonerTerm> extends TermStore<T> {
    private long count;

    public DummyTermStore(AtomStore atomStore) {
        super(atomStore, null);
        count = 0l;
    }

    @Override
    public synchronized int add(ReasonerTerm term) {
        count++;
        return 1;
    }

    @Override
    public void clear() {
        count = 0l;
    }

    @Override
    public void close() {
        super.close();
        clear();
    }

    @Override
    public void ensureCapacity(long capacity) {
    }

    @Override
    public T get(long index) {
        return null;
    }

    @Override
    public Iterator<T> iterator() {
        List<T> empty = new ArrayList<T>();
        return empty.iterator();
    }

    @Override
    public long size() {
        return count;
    }

    @Override
    public void setTermGenerator(TermGenerator<T> termGenerator) {
    }

    @Override
    public int add(GroundRule groundRule) {
        return add((ReasonerTerm) null);
    }
}
