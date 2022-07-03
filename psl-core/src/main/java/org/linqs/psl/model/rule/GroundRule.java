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

import java.util.List;
import java.util.Set;

import org.linqs.psl.model.atom.GroundAtom;

/**
 * A function that either constrains or measures the compatibility of the
 * values of {@link GroundAtom GroundAtoms}.
 * <p>
 * GroundRules are templated by a parent {@link Rule}.
 */
public interface GroundRule {
    /**
     * @return this GroundRule's parent {@link Rule}
     */
    public Rule getRule();

    /**
     * @return set of {@link GroundAtom GroundAtoms} which determine this
     *  GroundRule's incompatibility or infeasibility
     */
    public Set<GroundAtom> getAtoms();

    /**
     * Negate this ground rule and get the corresponding ground rule(s).
     */
    public List<GroundRule> negate();

    /**
     * Get a to string for the base of the rule without weight or square.
     */
    public String baseToString();
}
