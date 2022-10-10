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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.SimpleTermStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ADMMTermStore extends SimpleTermStore<ADMMObjectiveTerm> {
    private Map<Integer, List<LocalRecord>> localRecords;
    private int numLocalVariables;

    public ADMMTermStore(Database database) {
        super(database, new ADMMTermGenerator());

        numLocalVariables = 0;
        localRecords = new HashMap<Integer, List<LocalRecord>>(database.getAtomStore().size());
    }

    public List<LocalRecord> getLocalRecords(int variableIndex) {
        return localRecords.get(Integer.valueOf(variableIndex));
    }

    public int getNumLocalVariables() {
        return numLocalVariables;
    }

    @Override
    protected synchronized int add(GroundRule groundRule, ADMMObjectiveTerm term, Hyperplane hyperplane) {
        long termIndex = size();
        super.add(groundRule, term, hyperplane);

        // Add records of local variables.
        for (int i = 0; i < hyperplane.size(); i++) {
            int atomIndex = hyperplane.getVariable(i).getIndex();

            // All atoms should be unobserved here (obs should have been merged).
            if (!localRecords.containsKey(Integer.valueOf(atomIndex))) {
                localRecords.put(Integer.valueOf(atomIndex), new ArrayList<LocalRecord>());
            }

            localRecords.get(Integer.valueOf(atomIndex)).add(new LocalRecord(termIndex, (short)i));
            numLocalVariables++;
        }

        return 1;
    }

    @Override
    public void clear() {
        super.clear();

        if (localRecords != null) {
            localRecords.clear();
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

        float[] consensusValues = database.getAtomStore().getAtomValues();
        for (Map.Entry<Integer, List<LocalRecord>> entry : localRecords.entrySet()) {
            for (LocalRecord local : entry.getValue()) {
                get(local.termIndex).setLocalValue(local.variableIndex, consensusValues[entry.getKey().intValue()], 0.0f);
            }
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
