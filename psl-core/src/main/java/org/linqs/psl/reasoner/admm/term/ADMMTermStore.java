/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.reasoner.duallcqp.term.DualLCQPAtom;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.SimpleTermStore;

import java.util.ArrayList;
import java.util.List;

/**
 * A term store that handles the consensus variables for ADMM.
 * This term store assumes that no more random variables (RVAs) are added after the first call to add() is made.
 */
public class ADMMTermStore extends SimpleTermStore<ADMMObjectiveTerm> {
    // Entries are List<LocalRecord>.
    private List[] localRecords;

    private int numLocalVariables;

    public ADMMTermStore(Database database) {
        super(database, new ADMMTermGenerator());

        numLocalVariables = 0;
        localRecords = null;
    }

    @SuppressWarnings("unchecked")
    public List<LocalRecord> getLocalRecords(int variableIndex) {
        if (localRecords == null || variableIndex >= localRecords.length) {
            return null;
        }

        return (List<LocalRecord>)localRecords[variableIndex];
    }

    public int getNumLocalVariables() {
        return numLocalVariables;
    }

    @Override
    protected synchronized int add(GroundRule groundRule, ADMMObjectiveTerm term, Hyperplane hyperplane) {
        init();
        ensureLocalRecordsCapacity();

        long termIndex = size();
        super.add(groundRule, term, hyperplane);

        // Add records of local variables.
        for (int i = 0; i < hyperplane.size(); i++) {
            int atomIndex = hyperplane.getVariable(i).getIndex();

            if (localRecords[atomIndex] == null) {
                localRecords[atomIndex] = new ArrayList();
            }

            @SuppressWarnings("unchecked")
            List<LocalRecord> records = (List<LocalRecord>)localRecords[atomIndex];
            records.add(new LocalRecord(termIndex, (short)i));
            numLocalVariables++;
        }

        return 1;
    }

    private synchronized void ensureLocalRecordsCapacity() {
        if (localRecords.length < database.getAtomStore().size()) {
            List[] newLocalRecords = new List[database.getAtomStore().size()];
            System.arraycopy(localRecords, 0, newLocalRecords, 0, localRecords.length);
            localRecords = newLocalRecords;
        }
    }

    @Override
    public void clear() {
        super.clear();

        if (localRecords != null) {
            for (int i = 0; i < localRecords.length; i++) {
                if (localRecords[i] != null) {
                    localRecords[i].clear();
                    localRecords[i] = null;
                }
            }
        }

        numLocalVariables = 0;
    }

    @Override
    public void close() {
        super.close();

        localRecords = null;
    }

    @Override
    public void reset() {
        super.reset();

        if (localRecords != null) {
            float[] consensusValues = database.getAtomStore().getAtomValues();
            for (int i = 0; i < localRecords.length; i++) {
                if (localRecords[i] == null) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<LocalRecord> records = (List<LocalRecord>)localRecords[i];
                for (LocalRecord local : records) {
                    get(local.termIndex).setLocalValue(local.variableIndex, consensusValues[i], 0.0f);
                }
            }
        }
    }

    private synchronized void init() {
        if (localRecords == null) {
            localRecords = new List[database.getAtomStore().size()];
        }
    }

    public static final class LocalRecord {
        public long termIndex;
        public short variableIndex;

        public LocalRecord(long termIndex, short variableIndex) {
            this.termIndex = termIndex;
            this.variableIndex = variableIndex;
        }
    }
}
