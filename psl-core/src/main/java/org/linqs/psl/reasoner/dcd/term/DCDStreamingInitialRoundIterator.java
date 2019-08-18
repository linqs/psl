/**
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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.streaming.StreamingInitialRoundIterator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

/**
 * Iterate over all the terms that come up from grounding.
 * On this first iteration, we will build the term cache up from ground rules
 * and flush the terms to disk.
 */
public class DCDStreamingInitialRoundIterator extends StreamingInitialRoundIterator<DCDObjectiveTerm> {
    public DCDStreamingInitialRoundIterator(
            DCDStreamingTermStore parentStore, List<WeightedRule> rules,
            AtomManager atomManager, DCDTermGenerator termGenerator,
            List<DCDObjectiveTerm> termCache, List<DCDObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer lagrangeBuffer,
            int pageSize) {
        super(parentStore, rules, atomManager, termGenerator, termCache, termPool, termBuffer, lagrangeBuffer, pageSize);
    }

    @Override
    protected void writePage(String termPagePath, String lagrangePagePath) {
        flushTermCache(termPagePath);
        flushLagrangeCache(lagrangePagePath);

        termCache.clear();
    }

    private void flushTermCache(String termPagePath) {
        // Count the exact size we will need to write.
        int termsSize = 0;
        for (DCDObjectiveTerm term : termCache) {
            termsSize += term.fixedByteSize();
        }

        // Allocate an extra two ints for the number of terms and size of terms in that page.
        int termBufferSize = termsSize + (Integer.SIZE / 8) * 2;

        if (termBuffer == null || termBuffer.capacity() < termBufferSize) {
            termBuffer = ByteBuffer.allocate((int)(termBufferSize * OVERALLOCATION_RATIO));
        }
        termBuffer.clear();

        // First put the size of the terms and number of terms.
        termBuffer.putInt(termsSize);
        termBuffer.putInt(termCache.size());

        // Now put in all the terms.
        for (DCDObjectiveTerm term : termCache) {
            term.writeFixedValues(termBuffer);
        }

        try (FileOutputStream stream = new FileOutputStream(termPagePath)) {
            stream.write(termBuffer.array(), 0, termBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write term cache page: " + termPagePath, ex);
        }
    }

    private void flushLagrangeCache(String lagrangePagePath) {
        int lagrangeBufferSize = (Float.SIZE / 8) * termCache.size();

        if (lagrangeBuffer == null || lagrangeBuffer.capacity() < lagrangeBufferSize) {
            lagrangeBuffer = ByteBuffer.allocate((int)(lagrangeBufferSize * OVERALLOCATION_RATIO));
        }
        lagrangeBuffer.clear();

        // Put in all the lagrange values.
        for (DCDObjectiveTerm term : termCache) {
            lagrangeBuffer.putFloat(term.getLagrange());
        }

        try (FileOutputStream stream = new FileOutputStream(lagrangePagePath)) {
            stream.write(lagrangeBuffer.array(), 0, lagrangeBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write lagrange cache page: " + lagrangePagePath, ex);
        }
    }
}
