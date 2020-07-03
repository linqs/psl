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
package org.linqs.psl.application.inference.online.actions;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

public class AddAtom extends OnlineAction{
    private String predicateName;
    private String partitionName;
    private Constant[] arguments;
    private float newValue;

    public AddAtom() {
        this.newValue = (float)0.5;
    }

    public String getPredicateName() {
        return this.predicateName;
    }

    public String getPartitionName() {
        return this.partitionName;
    }

    public float getValue() {
        return this.newValue;
    }

    public Constant[] getArguments() {
        return this.arguments;
    }

    @Override
    public void initAction(String[] tokenized_command) throws IllegalArgumentException {
        // Format: AddAtom PartitionName PredicateName Arguments Value(Optional)
        Predicate registeredPredicate = null;
        int argumentLength = 0;
        for (int i = 1; i < tokenized_command.length; i++) {
            if (i == 1) {
                // Partition Name Field:
                partitionName = tokenized_command[i].toUpperCase();
                switch (partitionName) {
                    case "READ":
                        argumentLength = 4;
                        break;
                    case "WRITE":
                        argumentLength = 3;
                        break;
                    default:
                        throw new IllegalArgumentException("Illegal Partition Name: " + tokenized_command[i]);
                }
            } else if (i == 2) {
                // Predicate Field: Ensure predicate is registered in data store
                registeredPredicate = resolvePredicate(tokenized_command[i]);
                predicateName = registeredPredicate.getName();
                if (tokenized_command.length < registeredPredicate.getArity() + argumentLength) {
                    throw new IllegalArgumentException("Not enough arguments provided for updating Predicate: " +
                            tokenized_command[i] + " With arity: " + registeredPredicate.getArity());
                }
                arguments = new Constant[registeredPredicate.getArity()];
            } else if (i <= (2 + registeredPredicate.getArity())) {
                // Argument Field:
                // Resolve Arguments
                ConstantType type = registeredPredicate.getArgumentType(i - 3);
                arguments[i - 3] = resolveConstant(tokenized_command[i], type);
            } else if (i == (3 + registeredPredicate.getArity())) {
                // Value Field: Ensure value is valid
                // Block only reached if value provided
                newValue = resolveValue(tokenized_command[i]);
            } else {
                throw new IllegalArgumentException("Too many arguments provided for Predicate: " +
                        tokenized_command[i] + " With arity: " + registeredPredicate.getArity());
            }
        }
    }
}