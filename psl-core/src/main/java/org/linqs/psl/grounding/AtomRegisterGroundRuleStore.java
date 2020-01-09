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
package org.linqs.psl.grounding;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A GroundRuleStore that tracks every GroundRule every GroundAtom participates in.
 * This can buildup a non-trivial amount of memory, so perfer MemoryGroundRuleStore if
 * you don't need the mapping functionality.
 */
public class AtomRegisterGroundRuleStore extends MemoryGroundRuleStore {
    private Map<GroundAtom, Set<GroundRule>> atomMapping;

    public AtomRegisterGroundRuleStore() {
        super();

        atomMapping = new HashMap<GroundAtom, Set<GroundRule>>();
    }

    public Set<GroundRule> getRegisteredGroundRules(GroundAtom atom) {
        if (!atomMapping.containsKey(atom)) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(atomMapping.get(atom));
    }

    @Override
    public synchronized void addGroundRule(GroundRule groundRule) {
        super.addGroundRule(groundRule);

        // Register the ground rule with the atoms involved.
        for (GroundAtom atom : groundRule.getAtoms()) {
            if (!atomMapping.containsKey(atom)) {
                atomMapping.put(atom, new HashSet<GroundRule>());
            }

            atomMapping.get(atom).add(groundRule);
        }
    }

    @Override
    public void removeGroundRule(GroundRule groundRule) {
        super.removeGroundRule(groundRule);

        // Unregister the ground rule with all the atoms involved.
        for (GroundAtom atom : groundRule.getAtoms()) {
            if (!atomMapping.containsKey(atom)) {
                continue;
            }

            atomMapping.get(atom).remove(groundRule);
        }
    }

    @Override
    public void removeGroundRules(Rule rule) {
        // Unregister the atoms before we loose the mapping of rule to ground rules.
        for (GroundRule groundRule : getGroundRules(rule)) {
            for (GroundAtom atom : groundRule.getAtoms()) {
                if (!atomMapping.containsKey(atom)) {
                    continue;
                }

                atomMapping.get(atom).remove(groundRule);
            }
        }

        super.removeGroundRules(rule);
    }

    @Override
    public void close() {
        super.close();

        if (atomMapping != null) {
            atomMapping.clear();
            atomMapping = null;
        }
    }
}
