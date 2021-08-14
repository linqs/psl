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
package org.linqs.psl.application.inference.online.messages.actions.model;

import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.StringUtils;

/**
 * Add a new atom to the model.
 * If the atom already exists as READ or WRITE, then the atom is first deleted.
 * Note that a value must be provided for atoms being added to the READ partition.
 * String format: AddAtom <READ/WRITE> <predicate> <arg>... [value]
 */
public class AddAtom extends AtomAction {
    private float value;
    private String partition;

    public AddAtom(String partition, StandardPredicate predicate, Constant[] arguments) {
        super(predicate, arguments);
        init(partition, -1.0f);
    }

    public AddAtom(String partition, StandardPredicate predicate, Constant[] arguments, float value) {
        super(predicate, arguments);
        init(partition, value);
    }

    private void init(String partition, float value) {
        this.partition = partition.toUpperCase();
        this.value = value;
    }

    public float getValue() {
        if (MathUtils.equals(value, -1.0f)) {
            // Default value when a value is not provided.
            return RandUtils.nextFloat();
        } else {
            return value;
        }
    }

    public String getPartitionName() {
        return partition;
    }

    @Override
    public String toString() {
        if (value == -1.0f) {
            return String.format(
                    "ADDATOM\t%s\t%s\t%s",
                    partition,
                    predicate.getName(),
                    StringUtils.join("\t", arguments));
        } else {
            return String.format(
                    "ADDATOM\t%s\t%s\t%s\t%.2f",
                    partition,
                    predicate.getName(),
                    StringUtils.join("\t", arguments),
                    value);
        }
    }
}
