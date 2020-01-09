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
package org.linqs.psl.application.inference;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.GroundRules;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.Model;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Infers the most-probable explanation (MPE) state of the
 * RandomVariableAtoms persisted in a Database,
 * according to a {@link Model}, given the Database's ObservedAtoms.
 *
 * The set of RandomVariableAtoms is those persisted in the Database when inference() is called.
 * This set must contain all RandomVariableAtoms the Model might access.
 */
public class MPEInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(MPEInference.class);

    public MPEInference(Model model, Database db) {
        super(model, db);
    }

    @Override
    protected void completeInitialize() {
        log.info("Grounding out model.");
        int groundCount = Grounding.groundAll(model, atomManager, groundRuleStore);
        log.info("Grounding complete.");

        log.debug("Initializing objective terms for {} ground rules.", groundCount);
        @SuppressWarnings("unchecked")
        int termCount = termGenerator.generateTerms(groundRuleStore, termStore);
        log.debug("Generated {} objective terms from {} ground rules.", termCount, groundCount);
    }
}
