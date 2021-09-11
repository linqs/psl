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
package org.linqs.psl.application.inference.online;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.OnlineTest;
import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.application.inference.online.messages.actions.controls.Exit;
import org.linqs.psl.application.inference.online.messages.actions.controls.Stop;
import org.linqs.psl.application.inference.online.messages.actions.model.AddAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.QueryAtom;
import org.linqs.psl.application.inference.online.messages.responses.ActionStatus;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.UniqueStringID;

import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SGDOnlineInferenceTest {
    private TestModel.ModelInformation modelInfo;
    private Database inferDB;
    private OnlineInferenceThread onlineInferenceThread;

    public SGDOnlineInferenceTest() {
        modelInfo = null;
        inferDB = null;
        onlineInferenceThread = null;
    }

    @Before
    public void setup() {
        cleanup();

        Options.SGD_LEARNING_RATE.set(10.0);

        modelInfo = TestModel.getModel(true);

        inferDB = modelInfo.dataStore.getDatabase(modelInfo.targetPartition,
                new HashSet<StandardPredicate>(), modelInfo.observationPartition);

        // Start up inference on separate thread.
        onlineInferenceThread = new OnlineInferenceThread();
        onlineInferenceThread.start();
    }

    @After
    public void cleanup() {
        stop();

        if (onlineInferenceThread != null) {
            try {
                onlineInferenceThread.join();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            onlineInferenceThread = null;
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

    protected void stop() {
        if (onlineInferenceThread == null) {
            return;
        }

        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        Stop stop = new Stop();
        commands.add(stop);

        OnlineResponse[] expectedResponses = new OnlineResponse[1];
        expectedResponses[0] = new ActionStatus(stop, true, "OnlinePSL inference stopped.");

        OnlineTest.assertServerResponse(commands, expectedResponses);

        try {
            onlineInferenceThread.join();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        onlineInferenceThread = null;
    }

    /**
     * A test that to see if the inference method is running, accepting client connections, and stopping.
     */
    @Test
    public void baseTest() {
        stop();
    }

    /**
     * Make sure that new atoms are added to model, are considered during inference, and
     * result in the expected groundings.
     */
    @Test
    public void testAddAtoms() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        // Check that adding atoms will not create new random variable atoms.
        commands.add(new AddAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {-1.0, -1.0, -1.0, -1.0});

        // Reset model.
        cleanup();
        setup();

        // Check that atoms added to the model have the expected values at the MAP state.
        commands.add(new AddAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}, 0.0f));
        commands.add(new QueryAtom(StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}));
        commands.add(new QueryAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {1.0, 0.0, 0.0, 0.0, 0.0, 0.0});
    }

    /**
     * Add an atom with predicates and arguments that already exists in the model but with a different partition.
     */
    @Test
    public void testChangeAtomPartition() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        // Add existing atom with different partition.
        commands.add(new AddAtom("Read", StandardPredicate.get("Friends"),
                new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}, 0.5f));
        commands.add(new QueryAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        double[] values = {0.5};

        OnlineTest.assertAtomValues(commands, values);
    }

    private class OnlineInferenceThread extends Thread {
        SGDOnlineInference onlineInference;

        public OnlineInferenceThread() {
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
