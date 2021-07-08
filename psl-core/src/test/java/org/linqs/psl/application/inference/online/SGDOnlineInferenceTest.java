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
import org.linqs.psl.application.inference.online.messages.actions.controls.Stop;
import org.linqs.psl.application.inference.online.messages.responses.ActionStatus;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;

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
    }

    @Before
    public void setup() {
        cleanup();

        modelInfo = TestModel.getModel(true);

        inferDB = modelInfo.dataStore.getDatabase(modelInfo.targetPartition,
                new HashSet<StandardPredicate>(), modelInfo.observationPartition);

        // Start up inference on separate thread.
        onlineInferenceThread = new OnlineInferenceThread();
        onlineInferenceThread.start();
    }

    @After
    public void cleanup() {
        if (onlineInferenceThread != null) {
            onlineInferenceThread.close();
            onlineInferenceThread = null;
        }
    }

    /**
     * A test that to see if the inference method is running, accepting client connections, and stopping.
     */
    @Test
    public void baseTest() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        Stop stop = new Stop();
        commands.add(stop);

        OnlineResponse[] expectedResponses = new OnlineResponse[1];
        expectedResponses[0] = new ActionStatus(stop, true, "OnlinePSL inference stopped.");

        OnlineTest.assertServerResponse(commands, expectedResponses);
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
