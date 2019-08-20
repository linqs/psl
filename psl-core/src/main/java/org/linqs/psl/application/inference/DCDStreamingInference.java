/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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
import org.linqs.psl.model.Model;
import org.linqs.psl.reasoner.dcd.DCDReasoner;
import org.linqs.psl.reasoner.dcd.term.DCDStreamingTermStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DCDStreamingInference extends InferenceApplication {
    private static final Logger log = LoggerFactory.getLogger(DCDStreamingInference.class);

    public DCDStreamingInference(Model model, Database db) {
        super(model, db);
    }

    @Override
    protected void initialize() {
        reasoner = new DCDReasoner();

        log.debug("Creating persisted atom mannager.");
        atomManager = new PersistedAtomManager(db);
        log.trace("Atom manager initialization complete.");

        termStore = new DCDStreamingTermStore(model.getRules(), atomManager);
        termStore.ensureVariableCapacity(atomManager.getCachedRVACount());
    }

    @Override
    protected void completeInitialize() {
        // Handled in initialize() override.
    }

    @Override
    public void inference() {
        log.info("Beginning inference.");
        reasoner.optimize(termStore);
        log.info("Inference complete. Writing results to Database.");

        // Commits the RandomVariableAtoms back to the Database,
        ((PersistedAtomManager)atomManager).commitPersistedAtoms();
        log.info("Results committed to database.");
    }

    @Override
    public void close() {
        termStore.close();
        reasoner.close();

        termStore = null;
        reasoner = null;

        model=null;
        db = null;
    }
}
