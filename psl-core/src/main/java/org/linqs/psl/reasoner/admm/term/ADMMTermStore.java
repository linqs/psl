/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.reasoner.term.MemoryConsensusTermStore;

/**
 * A TermStore specifically for ADMM terms.
 */
public class ADMMTermStore extends MemoryConsensusTermStore<ADMMObjectiveTerm, LocalVariable> {
    protected LocalVariable createLocalVariableInternal(int consensusIndex, float value) {
        return new LocalVariable(consensusIndex, value);
    }

    protected void resetLocalVariables() {
        for (int i = 0; i < store.getNumVariables(); i++) {
            float value = store.getVariableValue(i);
            for (LocalVariable local : localVariables.get(i)) {
                local.setValue(value);
                local.setLagrange(0.0f);
            }
        }
    }

    @Override
    public void variablesExternallyUpdated() {
        super.variablesExternallyUpdated();
        resetLocalVariables();
    }
}
