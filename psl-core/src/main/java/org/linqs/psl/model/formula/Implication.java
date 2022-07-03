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
package org.linqs.psl.model.formula;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.util.HashCode;

import java.util.Set;

public class Implication implements Formula {
    /**
     * The fuzzy singletons that constitute the body of the rule
     */
    protected final Formula body;

    /**
     * The fuzzy singleton that is the head of the rule
     */
    protected final Formula head;

    private final int hash;

    public Implication(Formula body, Formula head) {
        assert(body != null);
        assert(head != null);

        this.body = body;
        this.head = head;

        hash = HashCode.build(HashCode.build(body), head);
    }

    public Formula getBody() {
        return body;
    }

    public Formula getHead() {
        return head;
    }

    @Override
    public Formula getDNF() {
        return new Disjunction(new Negation(body.flatten()), head.flatten()).getDNF();
    }

    @Override
    public VariableTypeMap collectVariables(VariableTypeMap varMap) {
        body.collectVariables(varMap);
        head.collectVariables(varMap);

        return varMap;
    }

    @Override
    public Set<Atom> getAtoms(Set<Atom> atoms) {
        body.getAtoms(atoms);
        head.getAtoms(atoms);

        return atoms;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object otherObject) {
        if (otherObject == this) {
            return true;
        }

        if (otherObject == null || !(getClass().isInstance(otherObject))) {
            return false;
        }

        Implication other = (Implication)otherObject;
        return hash == other.hash && body.equals(other.body) && head.equals(other.head);
    }

    @Override
    public String toString() {
        return body.toString() + " >> " + head.toString();
    }

    @Override
    public Formula flatten() {
        // Just flatten out the body and head.
        return new Implication(body.flatten(), head.flatten());
    }
}
