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
import org.linqs.psl.util.StringUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Add a new atom to the model.
 * String format: ADD <READ/WRITE> <predicate> <args> ... [value]
 */
public class AddAtom extends OnlineAction {
    private StandardPredicate predicate;
    private String partition;
    private Constant[] arguments;
    private float value;

    public AddAtom(String[] parts) {
        this.outputStream = null;
        parse(parts);
    }

    public StandardPredicate getPredicate() {
        return predicate;
    }

    public String getPartitionName() {
        return partition;
    }

    public float getValue() {
        return value;
    }

    public Constant[] getArguments() {
        return arguments;
    }

    private void writeObject(java.io.ObjectOutputStream outputStream) throws IOException {
        outputStream.writeUTF(predicate.getName());
        outputStream.writeUTF(partition);
        outputStream.writeObject(arguments);
        outputStream.writeFloat(value);
    }

    private void readObject(ObjectInputStream inputStream) throws ClassNotFoundException, IOException {
        predicate = StandardPredicate.get(inputStream.readUTF());
        partition = inputStream.readUTF();
        arguments = (Constant[])inputStream.readObject();
        value = inputStream.readFloat();
    }

    @Override
    public String toString() {
        return String.format(
                "ADD\t%s\t%s\t%s\t%f",
                partition, predicate.getName(), StringUtils.join("\t", arguments), value);
    }

    private void parse(String[] parts) {
        assert(parts[0].equalsIgnoreCase("add"));

        if (parts.length < 4) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        partition = parts[1].toUpperCase();
        if (!(partition.equals("READ") || partition.equals("WRITE"))) {
            throw new IllegalArgumentException("Expecting 'READ' or 'WRITE' for partition, got '" + parts[1] + "'.");
        }

        OnlineAction.AtomInfo atomInfo = parseAtom(parts, 2);
        predicate = atomInfo.predicate;
        arguments = atomInfo.arguments;
        value = atomInfo.value;
    }
}
