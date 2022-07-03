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
package org.linqs.psl.model.term;

/**
 * An {@link Attribute} that encapsulates a Double.
 */
public class DoubleAttribute extends Attribute {
    private final Double value;

    /**
     * Constructs a Double attribute from a Double
     *
     * @param value  Double to encapsulate
     */
    public DoubleAttribute(Double value) {
        this.value = value;
    }

    @Override
    public String rawToString() {
        return value.toString();
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * A DoubleAttribute is equal to another Object if that Object is a DoubleAttribute
     * and their values are equal.
     */
    @Override
    public boolean equals(Object oth) {
        if (oth == this) {
            return true;
        }

        if (oth == null || !(oth instanceof DoubleAttribute)) {
            return false;
        }

        return value.equals(((DoubleAttribute)oth).getValue());
    }

    @Override
    public int compareTo(Term other) {
        if (other == null) {
            return -1;
        }

        if (!(other instanceof DoubleAttribute)) {
            return this.getClass().getName().compareTo(other.getClass().getName());
        }

        return value.compareTo(((DoubleAttribute)other).value);
    }
}
