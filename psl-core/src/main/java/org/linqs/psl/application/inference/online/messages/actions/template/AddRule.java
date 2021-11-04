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
package org.linqs.psl.application.inference.online.messages.actions.template;

import org.linqs.psl.model.rule.Rule;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Add a new rule to the model.
 * String format: AddRule <rule>
 */
public class AddRule extends TemplateAction {
    public AddRule(Rule rule) {
        super(rule);
    }

    @Override
    public String toString() {
        return String.format(
                "ADDRULE\t%s",
                rule.toString());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        newRule = !rule.isRegistered();
        rule.ensureRegistration();
    }
}
