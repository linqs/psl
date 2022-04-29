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
package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.term.ReasonerTerm;

import java.util.Iterator;

/**
 * Iterate over all the terms from grounding/cache.
 *
 * Note that the order of events in these iterators is very precise.
 * This stems from needing the write some values every iteration.
 * We cannot prefetch terms too early, because this may flush the cache.
 * For example if we prefetch the next term in next(),
 * then the term we are about to return may be the last of its page.
 * This means that (pre)fetching the next term will flush the page.
 * So we will have written a stale value and
 * the returned term will be converted into another one.
 * To avoid this, we will never prefetch (have two terms at a time)
 * and we will fetch in hasNext().
 */
public interface StreamingIterator<T extends ReasonerTerm> extends Iterator<T> {
    public void close();
}
