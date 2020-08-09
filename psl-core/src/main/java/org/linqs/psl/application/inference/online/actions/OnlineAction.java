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

import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

import java.io.*;

/**
 * Base class for online actions.
 * All actions should be able to freely convert to and from strings.
 */
public abstract class OnlineAction implements Serializable {
    OutputStreamWriter outputStream;

    /**
     * Construct an OnlineAction given the name and necessary information.
     */
    public static OnlineAction parse(String clientCommand) {
        String[] parts = clientCommand.split("\t");

        parts[0] = parts[0].trim();
        if (parts[0].equalsIgnoreCase("add")) {
            return new AddAtom(parts);
        } else if (parts[0].equalsIgnoreCase("stop")) {
            return new Stop();
        } else if (parts[0].equalsIgnoreCase("exit")) {
            return new Exit();
        } else if (parts[0].equalsIgnoreCase("delete")) {
            return new DeleteAtom(parts);
        } else if (parts[0].equalsIgnoreCase("update")) {
            return new UpdateObservation(parts);
        } else if (parts[0].equalsIgnoreCase("query")) {
            return new QueryAtom(parts);
        } else if (parts[0].equalsIgnoreCase("write")) {
            return new WriteInferredPredicates(parts);
        } else {
            throw new IllegalArgumentException("Unknown online action: '" + parts[0] + "'.");
        }
    }

    /**
     * Parse an atom.
     * The given starting index should point to the predicate.
     */
    protected AtomInfo parseAtom(String[] parts, int startIndex) {
        StandardPredicate predicate = StandardPredicate.get(parts[startIndex]);
        if (predicate == null) {
            throw new IllegalArgumentException("Unknown predicate: " + parts[startIndex] + ".");
        }

        // The final +1 is for the optional value.
        if (parts.length > (startIndex + 1 + predicate.getArity() + 1)) {
            throw new IllegalArgumentException("Too many arguments.");
        }

        float value = 1.0f;
        if (parts.length == (startIndex + 1 + predicate.getArity() + 1)) {
            value = Float.valueOf(parts[parts.length - 1]);
        }

        Constant[] arguments = new Constant[predicate.getArity()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = ConstantType.getConstant(parts[startIndex + 1 + i], predicate.getArgumentType(i));
        }

        return new AtomInfo(predicate, arguments, value);
    }

    public void setOutputWriter(OutputStreamWriter outputStream) {
        this.outputStream = outputStream;
    }

    public OutputStreamWriter getOutputWriter() {
        return outputStream;
    }

    protected static class AtomInfo {
        public StandardPredicate predicate;
        public Constant[] arguments;
        public float value;

        public AtomInfo(StandardPredicate predicate, Constant[] arguments, float value) {
            this.predicate = predicate;
            this.arguments = arguments;
            this.value = value;
        }
    }
}
