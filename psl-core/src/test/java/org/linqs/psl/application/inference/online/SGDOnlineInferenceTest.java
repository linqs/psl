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
import org.linqs.psl.application.inference.online.OnlineClient;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.util.StringUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

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

    private GroundAtom getAtom(SGDOnlineInference inference, String predicateName, String[] argumentStrings) {
        Constant[] arguments = new Constant[argumentStrings.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = new UniqueStringID(argumentStrings[i]);
        }

        Predicate predicate = Predicate.get(predicateName);
        return inference.getAtomManager().getAtom(predicate, arguments);
    }

    private double getAtomValue(String predicateName, String[] argumentStrings) {
        String queryResult = null;

        String commands = "QUERY\t" + predicateName + "\t" + StringUtils.join("\t", argumentStrings) + "\n" +
                "EXIT";

        String nonExistentAtomResponse = "Atom: " + predicateName + "("
                + StringUtils.join(",", argumentStrings) + ")"
                + " does not exist.";

        // Parse atom value
        queryResult = clientSession(commands).split("\n")[0].replaceAll("'", "");


        if (queryResult.equalsIgnoreCase(nonExistentAtomResponse)) {
            return -1.0;
        } else {
            return Double.parseDouble(queryResult.split("=")[1]);
        }
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

        clientSession("STOP\nEXIT");
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

        // Check that a non-existent new atom results in the expected server response.
        assertEquals(-1.0, getAtomValue( "Friends",  new String[]{"Bob", "Bob"}), 0.1);

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

        clientSession("STOP\nEXIT");
    }

//    @Test
//    public void testPageRewriting() {
//        Options.STREAMING_TS_PAGE_SIZE.set(2);
//        String commands = "ADD\tRead\tSim_Users\tConnor\tAlice\t0.0",
//                "ADD\tRead\tSim_Users\tAlice\tConnor\t0.0",
//                "ADD\tWrite\tRating\tConnor\tAvatar",
//                "WRITE",
//                "ADD\tRead\tSim_Users\tConnor\tBob\t1.0",
//                "ADD\tRead\tSim_Users\tBob\tConnor\t1.0",
//                "ADD\tRead\tRating\tBob\tSurfs Up\t0.5",
//                "ADD\tWrite\tRating\tConnor\tSurfs Up",
//                "WRITE",
//                "STOP"));
//
//        clientSession(inference, commands);
//        inference.inference();
//
//        float atomValue = getAtomValue(inference, "Rating", new String[]{"Connor", "Avatar"});
//        assertEquals(atomValue, 1.0, 0.01);
//    }

//    @Test
//    public void testAtomDeleting() {
//        // TODO (Charles): This order of commands will catch a behavior where there may be an unexpected outcome.
//        //  The atom will not be deleted if there is an add and then a delete of the same atom before the atoms are
//        //  activated. This behavior is also noted in streaming term store deleteAtom.
//        /*
//        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
//                "DELETE\tRead\tSim_Users\tAlice\tEddie",
//                "ADD\tRead\tSim_Users\tAlice\tEddie\t1.0",
//                "DELETE\tRead\tSim_Users\tAlice\tEddie",
//                "WRITE",
//                "STOP"));
//        */
//
//        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
//                "DELETE\tRead\tSim_Users\tAlice\tEddie",
//                "DELETE\tRead\tSim_Users\tEddie\tAlice",
//                "WRITE",
//                "STOP"));
//        clientSession(inference, commands);
//
//        @SuppressWarnings("unchecked")
//        VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore = (VariableTermStore<SGDObjectiveTerm, GroundAtom>)inference.getTermStore();
//        int numTerms = 0;
//        for (SGDObjectiveTerm term : termStore) {
//            numTerms++;
//        }
//
//        assertEquals(2.0, numTerms, 0.01);
//
//        inference.inference();
//
//        numTerms = 0;
//        for (SGDObjectiveTerm term: termStore) {
//            numTerms++;
//        }
//
//        assertEquals(1.0, numTerms, 0.01);
//    }
//
//    @Test
//    public void testChangeAtomPartition() {
//        Options.STREAMING_TS_PAGE_SIZE.set(4);
//        setup();
//
//        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
//                "ADD\tRead\tSim_Users\tConnor\tAlice\t1.0",
//                "ADD\tRead\tSim_Users\tAlice\tConnor\t1.0",
//                "ADD\tWrite\tRating\tConnor\tAvatar",
//                "ADD\tRead\tSim_Users\tConnor\tBob\t1.0",
//                "ADD\tRead\tSim_Users\tBob\tConnor\t1.0",
//                "ADD\tRead\tRating\tBob\tSurfs Up\t0.5",
//                "ADD\tWrite\tRating\tConnor\tSurfs Up",
//                "ADD\tRead\tRating\tAlice\tAvatar\t0.5",
//                "WRITE",
//                "STOP"));
//
//        clientSession(inference, commands);
//        inference.inference();
//        float atomValue = getAtomValue(inference, "Rating", new String[]{"Alice", "Avatar"});
//        assertEquals(atomValue, 0.5, 0.01);
//    }

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
