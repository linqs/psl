/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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

import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.rule.Rule;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class TemplateAction extends OnlineMessage {
    protected Rule rule;
    protected boolean newRule;

    public TemplateAction(Rule rule) {
        super();

        Set<Atom> atomSet = rule.getRewritableGroundingFormula().getAtoms(new HashSet<Atom>());
        for (Atom atom: atomSet) {
            if (atom.getPredicate() instanceof ExternalFunctionalPredicate) {
                throw new UnsupportedOperationException(
                        String.format("ExternalFunctionalPredicates are not serializable. Caused by: %s, in rule: %s",
                                atom.getPredicate(), rule));
            }
        }
        this.rule = rule;
        this.newRule = false;
    }

    public Rule getRule() {
        return rule;
    }

    public void setRule(Rule rule) {
        this.rule = rule;
    }

    public boolean isNewRule() {
        return newRule;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        newRule = !rule.isRegistered();
    }
}
