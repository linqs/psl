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
package org.linqs.psl.application.inference.online;

import org.linqs.psl.TestModel;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.util.StringUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SGDOnlineInferenceTest {
    private TestModel.ModelInformation modelInfo;
    private Database inferDB;
    private OnlineInferenceThread onlineInferenceThread;

    public SGDOnlineInferenceTest() {
        modelInfo = null;
        inferDB = null;
    }

    @Before
    public void setup() {
        cleanup();

        Options.ONLINE.set(true);

        modelInfo = TestModel.getModel(true);

        // Close the predicates we are using.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();

        inferDB = modelInfo.dataStore.getDatabase(modelInfo.targetPartition, toClose, modelInfo.observationPartition);

        // Start up inference on separate thread.
        onlineInferenceThread = new OnlineInferenceThread();
        onlineInferenceThread.start();
    }

    @After
    public void cleanup() {
        if (onlineInferenceThread != null) {
            clientSession("STOP\nEXIT");

            try {
                // Will wait 5 seconds for thread to finish otherwise will interrupt.
                onlineInferenceThread.join(5000);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            onlineInferenceThread.close();
        }

        if (inferDB != null) {
            inferDB.close();
            inferDB = null;
        }

        if (modelInfo != null) {
            modelInfo.dataStore.close();
            modelInfo = null;
        }
    }

    private String clientSession(String commands) {
        String sessionOutput = null;

        try (
                InputStream testInput = new ByteArrayInputStream(commands.getBytes());
                ByteArrayOutputStream testOutput = new ByteArrayOutputStream()) {

            // Set client in to string.
            InputStream stdIn = System.in;
            System.setIn(testInput);

            // Set client out to reader.
            PrintStream stdOut = System.out;
            System.setOut(new PrintStream(testOutput));

            // Start client to issue commands.
            OnlineClient.run();
            sessionOutput = testOutput.toString();

            // Close InputStream and reset in.
            System.setIn(stdIn);
            System.setOut(stdOut);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return sessionOutput;
    }

    private double getAtomValue(String predicateName, String[] argumentStrings) {
        String queryResult = null;

        String commands =
                "QUERY\t" + predicateName + "\t" + StringUtils.join("\t", argumentStrings) + "\n" +
                "EXIT";

        String nonExistentAtomResponse = String.format("Atom: %s('%s') does not exist.", predicateName, StringUtils.join("', '", argumentStrings));

        // Parse atom value
        queryResult = clientSession(commands).trim();

        if (queryResult.equalsIgnoreCase(nonExistentAtomResponse)) {
            return -1.0;
        } else {
            return Double.parseDouble(queryResult.split("=")[1]);
        }
    }

    /**
     * Test that a non-existent atom results in the expected server response.
     */
    @Test
    public void testBadQuery() {
        // Check that a non-existent new atom results in the expected server response.
        assertEquals(-1.0, getAtomValue( "Friends",  new String[]{"Bob", "Bob"}), 0.1);
    }

    /**
     * Make sure that updates issued by client commands are made as expected.
     */
    @Test
    public void testUpdateObservation() {
        String commands =
                "UPDATE\tNice\tAlice\t0.0\n" +
                "EXIT";

        clientSession(commands);

        double atomValue = getAtomValue("Nice", new String[]{"Alice"});
        assertEquals(0.0, atomValue, 0.01);
    }

    /**
     * Make sure that new atoms are added to model, are considered during inference, and
     * result in the expected groundings.
     */
    @Test
    public void testAddAtoms() {
        String commands =
                "ADD\tRead\tPerson\tConnor\t1.0\n" +
                "ADD\tRead\tNice\tConnor\t0.01\n" +
                "EXIT";

        clientSession(commands);

        // Check that new atoms were added to the model.
        assertNotEquals(-1.0, getAtomValue( "Person", new String[]{"Connor"}));
        assertNotEquals(-1.0, getAtomValue( "Nice", new String[]{"Connor"}));

        // Check that new atoms were not yet added to the model.
        assertEquals(-1.0, getAtomValue( "Friends", new String[]{"Connor", "Alice"}), 0.1);
        assertEquals(-1.0, getAtomValue( "Friends", new String[]{"Alice", "Connor"}), 0.1);
        assertEquals(-1.0, getAtomValue( "Friends", new String[]{"Connor", "Bob"}), 0.1);
        assertEquals(-1.0, getAtomValue( "Friends",  new String[]{"Bob", "Connor"}), 0.1);

        // Add write atoms to model.
        commands =
                "ADD\tWrite\tFriends\tAlice\tConnor\n" +
                "ADD\tWrite\tFriends\tConnor\tAlice\n" +
                "ADD\tWrite\tFriends\tConnor\tBob\n" +
                "ADD\tWrite\tFriends\tBob\tConnor\n" +
                "EXIT";

        clientSession(commands);

        // Check that atoms were considered during inference.
        assertEquals(0.0, getAtomValue( "Friends", new String[]{"Connor", "Alice"}), 0.1);
        assertEquals(0.0, getAtomValue( "Friends", new String[]{"Alice", "Connor"}), 0.1);
        assertEquals(0.0, getAtomValue( "Friends", new String[]{"Connor", "Bob"}), 0.1);
        assertEquals(0.0, getAtomValue( "Friends", new String[]{"Bob", "Connor"}), 0.1);
    }

    @Test
    public void testAtomDeleting() {
        // TODO (Charles): This order of commands will catch a behavior where there may be an unexpected outcome.
        //  The atom will not be deleted if there is an add and then a delete of the same atom before the atoms are
        //  activated. This behavior is also noted in streaming term store deleteAtom.
//        String commands =
//                "DELETE\tRead\tNice\tAlice\n" +
//                "ADD\tRead\tNice\tAlice\t1.0\n" +
//                "DELETE\tRead\tNice\tAlice\n" +
//                "EXIT";

        String commands =
                "DELETE\tRead\tNice\tAlice\n" +
                "DELETE\tRead\tPerson\tAlice\n" +
                "Exit";

        clientSession(commands);

        // Check that atoms were deleted from the model.
        assertEquals(-1.0, getAtomValue( "Person", new String[]{"Alice"}), 0.1);
        assertEquals(-1.0, getAtomValue( "Nice", new String[]{"Alice"}), 0.1);
    }

    @Test
    public void testChangeAtomPartition() {
        String commands =
                "ADD\tRead\tFriends\tAlice\tBob\t0.5\n" +
                "EXIT";

        clientSession(commands);

        assertEquals(0.5, getAtomValue("Friends", new String[]{"Alice", "Bob"}), 0.01);
    }

    private class OnlineInferenceThread extends Thread {
        SGDOnlineInference onlineInference;

        public OnlineInferenceThread() {
            // Constructor for OnlineInference applications calls initialize which starts up
            // OnlineServer on separate thread.
            onlineInference = new SGDOnlineInference(modelInfo.model.getRules(), inferDB);
        }

        @Override
        public void run() {
            onlineInference.inference();
        }

        public void close() {
            if (onlineInference != null) {
                onlineInference.close();
                onlineInference = null;
            }
        }
    }
}
