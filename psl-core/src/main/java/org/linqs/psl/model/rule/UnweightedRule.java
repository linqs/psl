/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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

/**
 * A template for {@link UnweightedGroundRule UnweightedGroundRules},
 * which constrain the values that {@link GroundAtom GroundAtoms} can take.
 */
public interface UnweightedRule extends Rule {
    /**
     * Relax the unweighted rule instance by creating a weighted rule that instantiates
     * potentials which act as penalty terms in the inference objective.
     * This method must unregister the unweighted rule before constructing the new weighted rule.
     */
    public WeightedRule relax(float weight, boolean squared);
}
