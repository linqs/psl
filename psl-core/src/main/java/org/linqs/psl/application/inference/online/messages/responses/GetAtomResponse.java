/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.application.inference.online.messages.responses;

import org.linqs.psl.application.inference.online.messages.actions.model.GetAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.StringUtils;

public class GetAtomResponse extends OnlineResponse {
    private double atomValue;
    private StandardPredicate predicate;
    private Constant[] arguments;

    public GetAtomResponse(GetAtom getAtomAction, double atomValue) {
        super(getAtomAction.getIdentifier());
        this.atomValue = atomValue;
        this.predicate = getAtomAction.getPredicate();
        this.arguments = getAtomAction.getArguments();
    }

    public double getAtomValue() {
        return atomValue;
    }

    public StandardPredicate getPredicate() {
        return predicate;
    }

    public Constant[] getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return String.format(
                "GetAtom\t%s\t%s\t%s\t%f",
                onlineActionID,
                predicate.getName(),
                StringUtils.join("\t", arguments),
                atomValue);
    }
}
