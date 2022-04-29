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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * A hashed storage class for arguments, keyed on their associated variables.
 *
 * This class extends the functionality of its parent class, {@link HashMap},
 * adding functionality specific to predicate arguments.
 *
 * Any variable that changes type will throw an exception,
 * except for DeferredFunctionalUniqueID.
 * DeferredFunctionalUniqueID can be replaced with UniqueIntID or UniqueStringID.
 * After all variables in a rule are added, there should be no DeferredFunctionalUniqueID left.
 */
public class VariableTypeMap extends HashMap<Variable, ConstantType> {
    private static final long serialVersionUID = -6590175777602710989L;

    public void addVariable(Variable var, ConstantType type) {
        addVariable(var, type, false);
    }

    /**
     * Adds a variable-type pair to the hashmap.
     *
     * @param var A variable
     * @param type An argument type
     * @param force ignore any consistency checks. This is not suggested.
     */
    public void addVariable(Variable var, ConstantType type, boolean force) {
        ConstantType oldType = get(var);
        if (oldType == null) {
            put(var, type);
            return;
        }

        // No need to do anything on a type match.
        if (oldType == type) {
            return;
        }

        if (force) {
            put(var, type);
            return;
        }

        // Type mismatch. Check for DeferredFunctionalUniqueID.

        // Only DeferredFunctionalUniqueID is allowed to have a different type than other instance of the variable.
        if (oldType == ConstantType.DeferredFunctionalUniqueID) {
            // Any other unique id can replace a deferred one.
            if (!(type == ConstantType.UniqueIntID || type == ConstantType.UniqueStringID)) {
                throw new IllegalStateException("Variable, " + var + ", is DeferredFunctionalUniqueID and connot be replaced by " + type);
            }

            put(var, type);
            return;
        }

        if (type == ConstantType.DeferredFunctionalUniqueID) {
            // Deferred types do not replace unique ones.
            if (!(oldType == ConstantType.UniqueIntID || oldType == ConstantType.UniqueStringID)) {
                throw new IllegalStateException("Variable, " + var + ", is " + oldType + " and cannot also be a DeferredFunctionalUniqueID");
            }

            return;
        }

        throw new IllegalStateException("Variable, " + var + ", has inconsistent type. First: " + oldType + ", Now: " + type);
    }

    /**
     * Returns all variables in the hashmap.
     *
     * @return A set of variables
     */
    public Set<Variable> getVariables() {
        return keySet();
    }

    /**
     * Returns the type of a given variable.
     *
     * @param var A variable
     * @return The argument type of the given variable
     */
    public ConstantType getType(Variable var) {
        ConstantType type = get(var);
        if (type == null) {
            throw new IllegalArgumentException("Specified variable is unknown: " + var);
        }

        return type;
    }

    /**
     * Returns whether the given variable exists in the hashmap.
     *
     * @param var A variable
     * @return TRUE if exists; FALSE otherwise
     */
    public boolean hasVariable(Variable var) {
        return containsKey(var);
    }

    /**
     * Performs a shallow copy of all variable-type pairs from another VariableTypeMap to this one.
     *
     * @param other Another VariableTypeMap
     */
    public void addAll(VariableTypeMap other) {
        for (Map.Entry<Variable, ConstantType> entry : other.entrySet()) {
            addVariable(entry.getKey(), entry.getValue());
        }
    }
}
