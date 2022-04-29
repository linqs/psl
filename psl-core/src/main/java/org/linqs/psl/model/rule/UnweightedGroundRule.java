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

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.reasoner.function.ConstraintTerm;

public interface UnweightedGroundRule extends GroundRule {

    @Override
    public UnweightedRule getRule();

    /**
     * Get a GeneralFunction representation of this ground rule.
     * If mergeConstants is true, then don't merge together constant terms.
     * Merging terms is generally encouraged, but certain inference methods
     * may need direct access to these terms.
     */
    public ConstraintTerm getConstraintDefinition(boolean mergeConstants);

    /**
     * Returns the infeasibility of the truth values of this GroundRule's
     * {@link GroundAtom GroundAtoms}.
     * <p>
     * Specifically, returns the distance between the value of the constraint's
     * functional definition and that function's nearest feasible value.
     * <p>
     * Infeasibility is always non-negative.
     *
     * @return the infeasibility of the current truth values
     */
    public float getInfeasibility();
}
