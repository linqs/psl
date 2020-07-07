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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class UpdateObservation extends OnlineAction {
    private String predicateName;
    private Constant[] arguments;
    private float newValue;

    public UpdateObservation(String[] tokenized_command) {
        super(tokenized_command);
        parseCommand(tokenized_command);
    }

    public String getPredicateName() {
        return this.predicateName;
    }

    public float getValue() {
        return this.newValue;
    }

    public Constant[] getArguments() {
        return this.arguments;
    }

    public void parseCommand(String[] tokenized_command) throws IllegalArgumentException {
        Predicate registeredPredicate = null;
        for (int i = 1; i < tokenized_command.length; i++) {
            if (i == 1) {
                // Predicate Field: Ensure predicate is registered in data store
                registeredPredicate = resolvePredicate(tokenized_command[i]);
                predicateName = registeredPredicate.getName();
                if (tokenized_command.length < registeredPredicate.getArity() + 3) {
                    throw new IllegalArgumentException("Not enough arguments provided for updating Predicate: " +
                            tokenized_command[i] + " With arity: " + registeredPredicate.getArity());
                }
                arguments = new Constant[registeredPredicate.getArity()];
            } else if (i <= (1 + registeredPredicate.getArity())) {
                // Argument Field:
                // Resolve Arguments
                ConstantType type = registeredPredicate.getArgumentType(i - 2);
                arguments[i - 2] = resolveConstant(tokenized_command[i], type);
            } else if (i == (2 + registeredPredicate.getArity())) {
                // Value Field: Ensure value is valid
                newValue = resolveValue(tokenized_command[i]);
            } else {
                throw new IllegalArgumentException("Too many arguments provided for Predicate: " +
                        tokenized_command[i] + " With arity: " + registeredPredicate.getArity());
            }
        }
    }

    @Override
    public String toString() {
        return String.format("<OnlineAction: %s, Predicate: %s, Arguments: %s, NewValue: %f>",
                this.getClass().getName(), predicateName, Arrays.toString(arguments), newValue);
    }
}
