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
package org.linqs.psl.model.formula;

import org.linqs.psl.util.HashCode;

public class Disjunction extends AbstractBranchFormula<Disjunction> {
    public Disjunction(Formula... f) {
        super(f);

        hashcode = HashCode.build(hashcode, "|");
    }

    @Override
    public Formula getDNF() {
        Formula[] components = new Formula[length()];
        for (int i = 0; i < components.length; i++) {
            components[i] = get(i).getDNF();
        }

        return new Disjunction(components).flatten();
    }

    @Override
    protected String separatorString() {
        return "|";
    }
}
