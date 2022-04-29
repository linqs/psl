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
 * A {@link Constant} that is a value, as opposed to a unique identifier.
 * For example, people in a social network should probably be represented as
 * unique identifiers, but their properties, such as names or ages, should probably
 * be Attributes.
 */
public abstract class Attribute extends Constant {
    /**
     * @return Java representation of the Attribute's value
     */
    public abstract Object getValue();
}
