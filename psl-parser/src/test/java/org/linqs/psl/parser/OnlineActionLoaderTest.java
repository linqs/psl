/*
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
package org.linqs.psl.parser;

import org.linqs.psl.OnlinePSLTest;

import org.junit.Test;

public class OnlineActionLoaderTest extends LoaderTest {
    @Test
    public void testAddAtom() {
        String input =
            "AddAtom Read SINGLE('A') 1.0\n" +
            "AddAtom Write DOUBLE('A', 'B')";
        String[] expected = new String[]{
            "ADDATOM\tREAD\tSINGLE\t'A'\t1.00",
            "ADDATOM\tWRITE\tDOUBLE\t'A'\t'B'"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testDeleteAtom() {
        String input =
            "DeleteAtom Read SINGLE('A')\n" +
            "DeleteAtom Write DOUBLE('A', 'B')";
        String[] expected = new String[]{
            "DELETEATOM\tREAD\tSINGLE\t'A'",
            "DELETEATOM\tWRITE\tDOUBLE\t'A'\t'B'"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testObserveAtom() {
        String input =
            "ObserveAtom SINGLE('A') 0.5\n" +
            "ObserveAtom DOUBLE('A', 'B') 1";
        String[] expected = new String[]{
            "OBSERVEATOM\tSINGLE\t'A'\t0.50",
            "OBSERVEATOM\tDOUBLE\t'A'\t'B'\t1.00"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testUpdateObservation() {
        String input =
            "UpdateAtom SINGLE('A') 0.5\n" +
            "UpdateAtom DOUBLE('A', 'B') 1";
        String[] expected = new String[]{
            "UPDATEATOM\tSINGLE\t'A'\t0.50",
            "UPDATEATOM\tDOUBLE\t'A'\t'B'\t1.00"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testGetAtom() {
        String input =
            "GetAtom SINGLE('A')\n" +
            "GetAtom DOUBLE('A', 'B')";
        String[] expected = new String[]{
            "GETATOM\tSINGLE\t'A'",
            "GETATOM\tDOUBLE\t'A'\t'B'"
        };

        OnlinePSLTest.assertActions(input, expected);
    }

    @Test
    public void testAddRule() {
        String input = "AddRule 1: Single(A) & Double(A, B) >> Single(B) ^2";
        String expected = "ADDRULE\t1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2";

        OnlinePSLTest.assertAction(input, expected);
    }

    @Test
    public void testActivateRule() {
        String input = "ActivateRule 1: Single(A) & Double(A, B) >> Single(B) ^2";
        String expected = "ACTIVATERULE\t1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2";

        OnlinePSLTest.assertAction(input, expected);
    }

    @Test
    public void testDeleteRule() {
        String input = "DeleteRule 1: Single(A) & Double(A, B) >> Single(B) ^2";
        String expected = "DELETERULE\t1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2";

        OnlinePSLTest.assertAction(input, expected);
    }

    @Test
    public void testDeactivateRule() {
        String input = "DeactivateRule 1: Single(A) & Double(A, B) >> Single(B) ^2";
        String expected = "DEACTIVATERULE\t1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2";

        OnlinePSLTest.assertAction(input, expected);
    }

    @Test
    public void testExit() {
        String input = "Exit";
        String expected = "EXIT";

        OnlinePSLTest.assertAction(input, expected);
    }

    @Test
    public void testStop() {
        String input = "Stop";
        String expected = "STOP";

        OnlinePSLTest.assertAction(input, expected);
    }

    @Test
    public void testSync() {
        String input = "Sync";
        String expected = "SYNC";

        OnlinePSLTest.assertAction(input, expected);
    }

    @Test
    public void testWriteInferredPredicates() {
        String input = "WriteInferredPredicates 'file/path'";
        String expected = "WRITEINFERREDPREDICATES\tfile/path";

        OnlinePSLTest.assertAction(input, expected);
    }
}
