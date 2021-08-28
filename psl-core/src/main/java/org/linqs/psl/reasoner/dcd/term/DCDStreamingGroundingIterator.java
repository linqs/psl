/**
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.streaming.StreamingGroundingIterator;
import org.linqs.psl.util.RuntimeStats;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Iterate over all the terms that come up from grounding.
 * On this first iteration, we will build the term cache up from ground rules
 * and flush the terms to disk.
 */
public class DCDStreamingGroundingIterator extends StreamingGroundingIterator<DCDObjectiveTerm> {
    public DCDStreamingGroundingIterator(
            DCDStreamingTermStore parentStore, List<Rule> rules,
            AtomManager atomManager, HyperplaneTermGenerator<DCDObjectiveTerm, GroundAtom> termGenerator,
            List<DCDObjectiveTerm> termCache, List<DCDObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            int pageSize, int numPages) {
        super(parentStore, rules, atomManager, termGenerator, termCache, termPool, termBuffer, volatileBuffer,
                pageSize, numPages);
    }

    @Override
    protected void flushVolatileCache(String volatilePagePath) {
        int volatileBufferSize = (Float.SIZE / 8) * termCache.size();

        if (volatileBuffer == null || volatileBuffer.capacity() < volatileBufferSize) {
            volatileBuffer = ByteBuffer.allocate((int)(volatileBufferSize * OVERALLOCATION_RATIO));
        }
        volatileBuffer.clear();

        // Put in all the volatile values.
        for (DCDObjectiveTerm term : termCache) {
            volatileBuffer.putFloat(term.getLagrange());
        }

        try (FileOutputStream stream = new FileOutputStream(volatilePagePath)) {
            stream.write(volatileBuffer.array(), 0, volatileBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write volatile cache page: " + volatilePagePath, ex);
        }

        // Log io.
        RuntimeStats.logDiskWrite(volatileBufferSize);
    }
}
