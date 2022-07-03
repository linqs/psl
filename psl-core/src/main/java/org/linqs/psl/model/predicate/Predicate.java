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
package org.linqs.psl.model.predicate;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Term;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A relation that can be applied to {@link Term Terms} to form {@link Atom Atoms}.
 * A Predicate is uniquely identified by its name (and all predicate names are converted to upper case).
 * Predicates cannot be constructed directly.
 * Instead, they are constructed via the appropriate gegetthod in each subclass.
 */
public abstract class Predicate implements Serializable {
    private static final Map<String, Predicate> predicates = new HashMap<String, Predicate>();

    private final String name;
    private final ConstantType[] types;
    private final int hashcode;

    protected Predicate(String name, ConstantType[] types) {
        this(name, types, true);
    }

    protected Predicate(String name, ConstantType[] types, boolean checkName) {
        if (checkName && !name.matches("\\w+")) {
            throw new IllegalArgumentException("Predicate name must match: /\\w+/.");
        }

        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("All predicates must have a non-zero length name.");
        }

        if (types == null || types.length == 0) {
            throw new IllegalArgumentException("All predicates must have at least one argument.");
        }

        this.name = name.toUpperCase();
        this.types = types;
        hashcode = this.name.hashCode();

        if (predicates.containsKey(this.name)) {
            throw new RuntimeException("Predicate with name '" + name + "' already exists.");
        }
        predicates.put(this.name, this);
    }

    /**
     * Returns the name of this Predicate.
     *
     * @return a string identifier for this Predicate
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the number of {@link Term Terms} that are related when using this Predicate.
     * In other words, the arity of a Predicate is the number of arguments it accepts.
     * For example, the Predicate Related(A, B) has an arity of 2.
     */
    public int getArity() {
        return types.length;
    }

    /**
     * Returns the ArgumentType which a {@link Term} must have to be a valid
     * argument for a particular argument position of this Predicate.
     */
    public ConstantType getArgumentType(int position) {
        return types[position];
    }

    /**
     * Close the predicate and free related resrouces.
     * It will be very rare to call this method.
     * Most predicates stay alive for the duration of PSL's run.
     */
    public void close() {
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getName()).append("(");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(types[i]);
        }
        builder.append(")");

        return builder.toString();
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public boolean equals(Object oth) {
        if (oth == this) {
            return true;
        }

        if (!(oth instanceof Predicate)) {
            return false;
        }

        Predicate other = (Predicate)oth;

        return hashCode() == other.hashCode() && name.equals(other.name) && Arrays.deepEquals(types, other.types);
    }

    public static void registerPredicate(Predicate predicate) {
        predicates.put(predicate.getName(), predicate);
    }

    public static Predicate get(String name)  {
        return predicates.get(name.toUpperCase());
    }

    public static Collection<Predicate> getAll() {
        return predicates.values();
    }

    /**
     * Clear out all active predicates.
     * This should ONLY be used in testing to set up for subsequent tests.
     */
    public static void clearForTesting() {
        predicates.clear();
    }
}
