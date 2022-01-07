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
package org.linqs.psl.reasoner.function;

/**
 * A numeric constraint.
 * A ConstraintTerm encapsulates a {@link GeneralFunction}, a {@link FunctionComparator}, and a value.
 * Together these define an equality or inequality statement between the value of a function and constant value.
 */
public class ConstraintTerm {
    private final GeneralFunction function;
    private final FunctionComparator comparator;
    private final float value;

    public ConstraintTerm(GeneralFunction function, FunctionComparator comparator, float value) {
        this.function = function;
        this.comparator = comparator;
        this.value = value;
    }

    public GeneralFunction getFunction() {
        return function;
    }

    public FunctionComparator getComparator() {
        return comparator;
    }

    public float getValue() {
        return value;
    }

    @Override
    public String toString() {
        return function.toString() + " " + comparator.toString() + " " + value;
    }
}
