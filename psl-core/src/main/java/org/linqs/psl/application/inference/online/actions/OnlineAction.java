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
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.LongAttribute;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.util.Reflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public abstract class OnlineAction implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(UpdateObservation.class);

    public OnlineAction(String[] tokenized_command) { }

    /**
     * Construct an OnlineAction given the name and necessary information.
     */
    public static OnlineAction getOnlineAction(String clientCommand) throws OnlineActionException {
        // tokenize
        String[] tokenizedCommand = clientCommand.split("\t");

        // Construct OnlineAction
        String className = Reflection.resolveClassName(tokenizedCommand[0]);
        log.trace("ClassName: " + className);
        if(className == null) {
            throw new OnlineActionException("Could not find class: " + tokenizedCommand[0]);
        }
        Class<? extends OnlineAction> classObject = null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends OnlineAction> uncheckedClassObject = (Class<? extends OnlineAction>)Class.forName(className);
            classObject = uncheckedClassObject;
        } catch (ClassNotFoundException ex) {
            throw new OnlineActionException("Could not find class: " + className, ex);
        }

        Constructor<? extends OnlineAction> constructor = null;
        try {
            constructor = classObject.getConstructor(String[].class);
        } catch (NoSuchMethodException ex) {
            throw new OnlineActionException("No suitable constructor () found for Online Action: " + className + ".", ex);
        }

        try {
            return constructor.newInstance(new Object[]{tokenizedCommand});
        } catch (InstantiationException ex) {
            throw new OnlineActionException("Unable to instantiate Online Action (" + className + ")", ex);
        } catch (IllegalAccessException ex) {
            throw new OnlineActionException("Insufficient access to constructor for " + className, ex);
        } catch (InvocationTargetException ex) {
            throw new OnlineActionException("Error thrown while constructing " + className, ex);
        }
    }

    protected static Constant resolveConstant(String name, ConstantType type) {
        switch (type) {
            case Double:
                return new DoubleAttribute(Double.parseDouble(name));
            case Integer:
                return new IntegerAttribute(Integer.parseInt(name));
            case String:
                return new StringAttribute(name);
            case Long:
                return new LongAttribute(Long.parseLong(name));
            case UniqueIntID:
                return new UniqueIntID(Integer.parseInt(name));
            case UniqueStringID:
                return new UniqueStringID(name);
            default:
                throw new IllegalArgumentException("Unknown argument type: " + type);
        }
    }

    protected static Predicate resolvePredicate(String predicateName) throws IllegalArgumentException {
        Predicate registeredPredicate = Predicate.get(predicateName);
        if (registeredPredicate == null) {
            throw new IllegalArgumentException("Unregistered Predicate: " + predicateName);
        }
        return registeredPredicate;
    }

    protected static float resolveValue(String value) throws IllegalArgumentException {
        float resolvedValue;
        try {
            resolvedValue = Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Value provided cannot be parsed as a float: " + value);
        }
        return resolvedValue;
    }

    @Override
    public String toString() {
        return String.format("<OnlineAction: %s>", this.getClass().getName());
    }
}

