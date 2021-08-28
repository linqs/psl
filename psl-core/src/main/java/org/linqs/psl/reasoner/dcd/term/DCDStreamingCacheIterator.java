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

import org.linqs.psl.reasoner.term.streaming.StreamingCacheIterator;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;
import org.linqs.psl.util.RuntimeStats;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class DCDStreamingCacheIterator extends StreamingCacheIterator<DCDObjectiveTerm> {
    public DCDStreamingCacheIterator(
            StreamingTermStore<DCDObjectiveTerm> parentStore, boolean readonly,
            List<DCDObjectiveTerm> termCache, List<DCDObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            boolean shufflePage, int[] shuffleMap, boolean randomizePageAccess,
            int numPages) {
        super(parentStore, readonly, termCache, termPool, termBuffer,
                volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    protected void readPage(String termPagePath, String volatilePagePath) {
        int termsSize = 0;
        int numTerms = 0;
        int headerSize = (Integer.SIZE / 8) * 2;
        int volatilesSize = 0;

        try (
                FileInputStream termStream = new FileInputStream(termPagePath);
                FileInputStream volatileStream = new FileInputStream(volatilePagePath)) {
            // First read the term size information.
            int readSize = termStream.read(termBuffer.array(), 0, headerSize);
            if (readSize != headerSize) {
                throw new RuntimeException(String.format(
                    "Short read for page header. Page: [%s], expected size: %d, read size: %d.",
                    termPagePath, headerSize, readSize));
            }

            termsSize = termBuffer.getInt();
            numTerms = termBuffer.getInt();
            volatilesSize = (Float.SIZE / 8) * numTerms;

            // Now read in all the terms and volatile values.

            readSize = termStream.read(termBuffer.array(), headerSize, termsSize);
            if (readSize != termsSize) {
                throw new RuntimeException(String.format(
                    "Short read for page terms. Page: [%s], expected size: %d, read size: %d.",
                    termPagePath, termsSize, readSize));
            }

            readSize = volatileStream.read(volatileBuffer.array(), 0, volatilesSize);
            if (readSize != volatilesSize) {
                throw new RuntimeException(String.format(
                    "Short read for volitile page. Page: [%s], expected size: %d, read size: %d.",
                    volatilePagePath, volatilesSize, readSize));
            }
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unable to read cache pages: [%s ; %s].", termPagePath, volatilePagePath), ex);
        }

        // Log io.
        RuntimeStats.logDiskRead(headerSize + termsSize);
        RuntimeStats.logDiskRead(volatilesSize);

        // Convert all the terms from binary to objects.
        // Use the terms from the pool.
        for (int i = 0; i < numTerms; i++) {
            DCDObjectiveTerm term = termPool.get(i);
            term.read(termBuffer, volatileBuffer);
            termCache.add(term);
        }
    }

    @Override
    protected void writeVolatilePage(String volatilePagePath) {
        int volatileBufferSize = (Float.SIZE / 8) * termCache.size();

        // If this page was picked up from the cache (and not from grounding) and shuffled,
        // then we will need to use the shuffle map to write the volatile values back in
        // the same order as the terms.
        if (shufflePage) {
            for (int shuffledIndex = 0; shuffledIndex < termCache.size(); shuffledIndex++) {
                int writeIndex = shuffleMap[shuffledIndex];
                DCDObjectiveTerm term = termCache.get(shuffledIndex);
                volatileBuffer.putFloat(writeIndex * (Float.SIZE / 8), term.getLagrange());
            }
        } else {
            for (DCDObjectiveTerm term : termCache) {
                volatileBuffer.putFloat(term.getLagrange());
            }
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
