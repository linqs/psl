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
package org.linqs.psl.model.rule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all (first order, i.e., not ground) rules.
 *
 * Care should always be taken when creating multiple instance of the same rule (e.g. through deserialization) or checking rules for equality.
 * In general, there should only be one instance of each rule, but this is not tightly enforced.
 * Rule hashes are by default their identity hash code (object id).
 * This means that it may be possible to have rules that are equal(), but that do not have matching hashes.
 */
public abstract class AbstractRule implements Rule {
    private static Map<Integer, AbstractRule> rules = new HashMap<Integer, AbstractRule>();

    protected final String name;

    public static AbstractRule getRule(int hashcode) {
        return rules.get(hashcode);
    }

    public AbstractRule(String name) {
        this.name = name;
        rules.put(System.identityHashCode(this), this);
    }

    public String getName() {
        return this.name;
    }

    /**
     * Explicitly define the hash for rules to be the identity hash code (object id).
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean requiresSplit() {
        return false;
    }

    @Override
    public List<Rule> split() {
        throw new UnsupportedOperationException();
    }
}
