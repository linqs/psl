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

import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.GroundRule;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A straightforward term store.
 */
public abstract class SimpleTermStore<T extends ReasonerTerm> extends TermStore<T> {
    protected ArrayList<T> terms;

    public SimpleTermStore(Database database, TermGenerator<T> termGenerator) {
        super(database, termGenerator);
        terms = new ArrayList<T>();
    }

    /**
     * An internal add that will always be called to add new terms.
     * User will use add(GroundRule) which will generate terms and call this method.
     * This may be called in parallel, it is up to implementing classes to guarantee thread safety.
     */
    @Override
    protected synchronized int add(GroundRule groundRule, T term, Hyperplane hyperplane) {
        terms.add(term);
        return 1;
    }

    /**
     * Remove any existing terms and prepare for a new set.
     */
    @Override
    public void clear() {
        terms.clear();
    }

    @Override
    public void close() {
        super.close();
        terms = null;
    }

    @Override
    public void ensureCapacity(long capacity) {
        terms.ensureCapacity((int)capacity);
    }

    @Override
    public T get(long index) {
        assert(index <= Integer.MAX_VALUE);

        return terms.get((int)index);
    }

    @Override
    public Iterator<T> iterator() {
        return terms.iterator();
    }

    @Override
    public long size() {
        return (long)terms.size();
    }
}
