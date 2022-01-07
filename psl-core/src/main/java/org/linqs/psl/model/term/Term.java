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
package org.linqs.psl.model.term;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;

import java.io.Serializable;

/**
 * An argument to a {@link Predicate}.
 * All terms are immutable.
 */
public interface Term extends Comparable<Term>, SummationVariableOrTerm, Serializable {
    public String toString();

    public int hashCode();

    public boolean equals(Object other);
}
