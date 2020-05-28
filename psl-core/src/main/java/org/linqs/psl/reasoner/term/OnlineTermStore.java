/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;

/**
 * An interface for term stores that can handle some atom operations.
 */
public interface OnlineTermStore<T extends ReasonerTerm, V extends ReasonerLocalAtom> extends TermStore<T, V> {

    public void addObservedAtom(Predicate predicate, Constant[] arguments, float newValue);

    public void addRandomVariableAtom(Predicate predicate, Constant[] arguments);

    public void deleteAtom(Predicate predicate, Constant[] arguments);

    public void updateAtom(Predicate predicate, Constant[] arguments, float newValue);

    public void addTerm(T term);

    public boolean deletedTerm(T term);

    public boolean updateTerm(T term);

}
