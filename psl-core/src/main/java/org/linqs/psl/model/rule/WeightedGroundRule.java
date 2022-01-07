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
import org.linqs.psl.reasoner.function.GeneralFunction;

public interface WeightedGroundRule extends GroundRule {
    @Override
    public WeightedRule getRule();

    public boolean isSquared();

    /**
     * Returns the Weight of this WeightedGroundRule.
     * Until setWeight() is called, this GroundRule's weight is the current weight of its parent Rule.
     * After it is called, it remains the most recent Weight set by setWeight().
     */
    public float getWeight();

    /**
     * Sets a weight for this WeightedGroundRule.
     */
    public void setWeight(float weight);

    /**
     * Get a GeneralFunction representation of this ground rule.
     * If mergeConstants is true, then don't merge together constant terms.
     * Merging terms is generally encouraged, but certain inference methods
     * may need direct access to these terms.
     */
    public GeneralFunction getFunctionDefinition(boolean mergeConstants);

    /**
     * Returns the incompatibility of the truth values of this GroundRule's GroundAtoms.
     * Incompatibility is always non-negative.
     */
    public float getIncompatibility();

    /**
     * Returns the incompatibility of the truth values of this GroundRule's GroundAtoms given
     * the replacment of a single atom's value with another value.
     * This method should only be used by callers that really know what they are doing.
     * Incompatibility is always non-negative.
     */
    public float getIncompatibility(GroundAtom replacementAtom, float replacementValue);
}
