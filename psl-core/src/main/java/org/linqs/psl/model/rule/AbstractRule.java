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
package org.linqs.psl.model.rule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all (first order, i.e., not ground) rules.
 */
public abstract class AbstractRule implements Rule {
    private static final Map<Integer, Rule> rules = new HashMap<Integer, Rule>();

    protected String name;
    protected int hashcode;

    public static Rule getRule(int hashcode) {
        return rules.get(hashcode);
    }

    /**
     * This default constructor only initializes name and hashcode to their empty values.
     * The caller of this constructor is responsible for initializing the name and hashcode of the object
     * and ensuring that the rule is registered.
     */
    protected AbstractRule() {
        this.name = null;
        this.hashcode = 0;
    }

    protected AbstractRule(String name, int hashcode) {
        this.name = name;
        this.hashcode = hashcode;

        ensureRegistration();
    }

    public String getName() {
        return this.name;
    }

    private static void registerRule(Rule rule) {
        rules.put(rule.hashCode(), rule);
    }

    private static void unregisterRule(Rule rule) {
        rules.remove(rule.hashCode());
    }

    public static void unregisterAllRulesForTesting() {
        rules.clear();
    }

    @Override
    public boolean isRegistered() {
        return rules.containsKey(this.hashcode);
    }

    @Override
    public void ensureRegistration() {
        if (!isRegistered()) {
            registerRule(this);
        }
    }

    @Override
    public void unregister() {
        if (isRegistered()) {
            unregisterRule(this);
        }
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    public abstract boolean equals(Object other);

    @Override
    public boolean requiresSplit() {
        return false;
    }

    @Override
    public List<Rule> split() {
        throw new UnsupportedOperationException();
    }
}
