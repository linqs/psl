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
package org.linqs.psl.reasoner.term;

public interface ReasonerTerm {
    /**
     * The number of variables this term uses.
     */
    public int size();

    /**
     * Adjust the term's internal constant by removing the old value and inserting the new value.
     * This is typically because an observed variable's value has changed.
     */
    public void adjustConstant(float oldValue, float newValue);

    /*
     * Whether this term is convex.
     * Reasoners may treat non-convex terms differently.
     */
    public boolean isConvex();
}
