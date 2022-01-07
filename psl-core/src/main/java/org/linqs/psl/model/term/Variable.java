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

/**
 * A variable {@link Term}.
 * Variables are immutable.
 * Variables are wildcards used to match {@link Constant GroundTerms}.
 */
public class Variable implements Term {
    private final String name;
    private final int hashcode;

    /**
     * Constructs a Variable, given a name.
     *
     * @param name A string ID
     */
    public Variable(String name) {
        if (name == null || !name.matches("^[a-zA-Z]\\w*")) {
            throw new IllegalArgumentException("Variable name must begin with a-z or A-Z and contain only [a-zA-Z0-9_]. Invalid name: " + name);
        }

        this.name = name;
        hashcode = this.name.hashCode() * 1163;
    }

    /**
     * @return the Variable's name
     */
    public String getName() {
        return name;
    }

    /**
     * @return {@link #getName()}
     */
    @Override
    public String toString() {
        return getName();
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other == null || !(other instanceof Variable)) {
            return false;
        }

        if (this.hashCode() != other.hashCode()) {
            return false;
        }

        return getName().equals(((Variable)other).getName());
    }

    /**
     * Just use the name for comparison.
     */
     @Override
    public int compareTo(Term other) {
        if (other == null) {
            return -1;
        }

        if (!(other instanceof Variable)) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        }

        return name.compareTo(((Variable)other).name);
    }
}
