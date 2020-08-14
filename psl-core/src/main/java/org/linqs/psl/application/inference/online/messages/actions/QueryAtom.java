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
package org.linqs.psl.application.inference.online.messages.actions;

import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.StringUtils;

import java.util.UUID;

/**
 * Query an existing observation from the model.
 * String format: UUID Query <predicate> <args> ...
 */
public class QueryAtom extends OnlineAction {
    private StandardPredicate predicate;
    private Constant[] arguments;

    public QueryAtom(UUID actionID, String clientCommand) {
        super(actionID, clientCommand);
    }

    public StandardPredicate getPredicate() {
        return predicate;
    }

    public Constant[] getArguments() {
        return arguments;
    }

    @Override
    public void setMessage(String newMessage) {
        parse(newMessage.split("\t"));

        message = String.format(
                "Query\t%s\t%s",
                predicate.getName(),
                StringUtils.join("\t", arguments).replace("'", ""));
    }

    @Override
    protected void parse(String[] parts) {
        assert(parts[0].equalsIgnoreCase("query"));

        if (parts.length < 2) {
            throw new IllegalArgumentException("Not enough arguments.");
        }

        AtomInfo atomInfo = parseAtom(parts, 1);
        predicate = atomInfo.predicate;
        arguments = atomInfo.arguments;
    }
}
