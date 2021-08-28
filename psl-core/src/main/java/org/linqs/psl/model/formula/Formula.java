/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.model.formula;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;

import java.io.Serializable;
import java.util.Set;

/**
 * A logical formula composed of {@link Atom Atoms} and logical operators.
 */
public interface Formula extends Serializable {
    /**
     * @return a logically equivalent Formula in disjunctive normal form
     */
    public Formula getDNF();

    /**
     * @return Atoms in the Formula
     */
    public Set<Atom> getAtoms(Set<Atom> atoms);

    /**
     * Adds the {@link Variable Variables}
     *
     * @param varMap
     * @return the passed in VariableTypeMap filled with the variables this formula uses.
     */
    public VariableTypeMap collectVariables(VariableTypeMap varMap);

    /**
     * Collapses nested formulas of the same type and remove duplicates at the top level.
     * Does not change the context object.
     * Order is not guarenteed.
     * Ex: (A ^ B) ^ !!C ^ (D v E) becomes A ^ B ^ C ^ (D v E).
     *
     * Note that most formulas will return an object of the same type (eg a Conjunction will
     * always return a Conjunction).
     * However, it is possible for some types (like Negation) the return a different type.
     *
     * @return the flattened Formula.
     */
    public Formula flatten();
}
