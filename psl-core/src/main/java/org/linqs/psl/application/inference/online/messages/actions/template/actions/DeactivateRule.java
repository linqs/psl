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
package org.linqs.psl.application.inference.online.messages.actions.template.actions;

import org.linqs.psl.model.rule.Rule;

/**
 * Add a new rule to the model.
 * String format: DeactivateRule <predicate> <args> ... <value>
 */
public class DeactivateRule extends TemplateAction {
    public DeactivateRule(Rule rule) {
        super(rule);
    }

    @Override
    public String toString() {
        return String.format(
                "DEACTIVATERULE\t%s",
                rule.toString());
    }
}
