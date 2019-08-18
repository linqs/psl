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

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.streaming.StreamingCacheIterator;

import org.apache.commons.lang.mutable.MutableInt;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class DCDStreamingCacheIterator extends StreamingCacheIterator<DCDObjectiveTerm> {
    public DCDStreamingCacheIterator(
            DCDStreamingTermStore parentStore, boolean readonly, Map<MutableInt, RandomVariableAtom> variables,
            List<DCDObjectiveTerm> termCache, List<DCDObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer lagrangeBuffer,
            boolean shufflePage, int[] shuffleMap, boolean randomizePageAccess,
            int numPages) {
        super(parentStore, readonly, variables, termCache, termPool, termBuffer,
                lagrangeBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    protected void readPage(String termPagePath, String lagrangePagePath) {
        int termsSize = 0;
        int numTerms = 0;
        int headerSize = (Integer.SIZE / 8) * 2;
        int lagrangesSize = 0;

        try (
                FileInputStream termStream = new FileInputStream(termPagePath);
                FileInputStream lagrangeStream = new FileInputStream(lagrangePagePath)) {
            // First read the term size information.
            termStream.read(termBuffer.array(), 0, headerSize);

            termsSize = termBuffer.getInt();
            numTerms = termBuffer.getInt();
            lagrangesSize = (Float.SIZE / 8) * numTerms;

            // Now read in all the terms and lagrange values.
            termStream.read(termBuffer.array(), headerSize, termsSize);
            lagrangeStream.read(lagrangeBuffer.array(), 0, lagrangesSize);
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unable to read cache pages: [%s ; %s].", termPagePath, lagrangePagePath), ex);
        }

        // Convert all the terms from binary to objects.
        // Use the terms from the pool.

        MutableInt intBuffer = new MutableInt();
        for (int i = 0; i < numTerms; i++) {
            DCDObjectiveTerm term = termPool.get(i);
            term.read(termBuffer, lagrangeBuffer, variables, intBuffer);
            termCache.add(term);
        }
    }

    @Override
    protected void writePage(String lagrangePagePath) {
        int lagrangeBufferSize = (Float.SIZE / 8) * termCache.size();

        // If this page was picked up from the cache (and not from grounding) and shuffled,
        // then we will need to use the shuffle map to write the lagrange values back in
        // the same order as the terms.
        if (shufflePage) {
            for (int shuffledIndex = 0; shuffledIndex < termCache.size(); shuffledIndex++) {
                int writeIndex = shuffleMap[shuffledIndex];
                DCDObjectiveTerm term = termCache.get(shuffledIndex);
                lagrangeBuffer.putFloat(writeIndex * (Float.SIZE / 8), term.getLagrange());
            }
        } else {
            for (DCDObjectiveTerm term : termCache) {
                lagrangeBuffer.putFloat(term.getLagrange());
            }
        }

        try (FileOutputStream stream = new FileOutputStream(lagrangePagePath)) {
            stream.write(lagrangeBuffer.array(), 0, lagrangeBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write lagrange cache page: " + lagrangePagePath, ex);
        }
    }
}
