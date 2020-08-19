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
package org.linqs.psl.application.inference.online.messages.responses;

import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModelInformation extends OnlineResponse {
    public Map<String, StandardPredicate> predicates;

    public ModelInformation(StandardPredicate[] predicates) {
        super(UUID.randomUUID(), String.format(
                "ModelInfo\t%s",
                StringUtils.join("\t", predicates)));
    }

    public ModelInformation(UUID identifier, String serverResponse) {
        super(identifier, serverResponse);
    }


    public Collection<StandardPredicate> getPredicates() {
        return predicates.values();
    }

    @Override
    public void setMessage(String newMessage) {
        parse(newMessage.split("\t"));

        message = String.format(
                "ModelInfo\t%s",
                StringUtils.join("\t", predicates.values().toArray(new StandardPredicate[]{})));
    }

    private void parse(String[] parts) {
        assert(parts[0].equalsIgnoreCase("ModelInfo"));

        String predicateName = null;
        String[] typeStrings = null;
        ConstantType[] types = null;
        predicates = new HashMap<String, StandardPredicate>();

        for (int i = 1; i < parts.length; i++) {
            predicateName = parts[i].split("\\(")[0];
            typeStrings = parts[i].split("\\(")[1].split(",");
            types = new ConstantType[typeStrings.length];

            for (int j = 0; j < typeStrings.length; j++) {
                types[j] = ConstantType.valueOf(typeStrings[j].replace(")", "").trim());
            }

            predicates.put(predicateName, StandardPredicate.get(predicateName, types));
        }

    }
}
