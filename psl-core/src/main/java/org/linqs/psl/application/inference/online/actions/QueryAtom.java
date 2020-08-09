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

/**
 * Update an existing observation from the model.
 * String format: Query <predicate> <args> ... [value]
 */
public class QueryAtom extends OnlineAction {
    private StandardPredicate predicate;
    private Constant[] arguments;

    public QueryAtom(String[] parts) {
        this.outputStream = null;
        parse(parts);
    }

    public StandardPredicate getPredicate() {
        return predicate;
    }

    public Constant[] getArguments() {
        return arguments;
    }

    private void writeObject(java.io.ObjectOutputStream outputStream) throws IOException {
        outputStream.writeUTF(predicate.getName());
        outputStream.writeObject(arguments);
    }

    private void readObject(ObjectInputStream inputStream) throws ClassNotFoundException, IOException {
        predicate = StandardPredicate.get(inputStream.readUTF());
        arguments = (Constant[])inputStream.readObject();
    }

    @Override
    public String toString() {
        return String.format(
                "Query\t%s\t%s",
                predicate.getName(), StringUtils.join("\t", arguments));
    }

    private void parse(String[] parts) {
        assert(parts[0].equalsIgnoreCase("query"));

        if (parts.length < 2) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        AtomInfo atomInfo = parseAtom(parts, 1);
        predicate = atomInfo.predicate;
        arguments = atomInfo.arguments;
    }
}
