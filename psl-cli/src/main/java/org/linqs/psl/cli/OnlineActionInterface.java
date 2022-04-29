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
package org.linqs.psl.cli;

import org.linqs.psl.application.inference.online.OnlineClient;
import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.application.inference.online.messages.actions.controls.Exit;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.parser.OnlineActionLoader;
import org.linqs.psl.util.FileUtils;

import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An interface for users to provide online actions via stdin.
 * Online actions are parsed and provided to a client to send to the online server.
 */
public class OnlineActionInterface {
    public static List<OnlineResponse> run() {
        List<OnlineResponse> serverResponses = new ArrayList<OnlineResponse>();
        CountDownLatch modelRegistrationLatch = new CountDownLatch(1);

        try (BufferedReader commandReader = FileUtils.getBufferedReader(System.in)) {
            String userInput = null;
            OnlineMessage onlineAction = null;
            BlockingQueue<OnlineMessage> onlineActions = new LinkedBlockingQueue<OnlineMessage>();
            OnlineClient onlineClient = new OnlineClient(onlineActions, serverResponses, modelRegistrationLatch);

            // Start client session and wait for countdown indicating the server model is registered.
            Thread onlineClientThread = new Thread(onlineClient);
            onlineClientThread.start();
            modelRegistrationLatch.await();

            // Read and parse userInput to create actions to send to the online server.
            while (!(onlineAction instanceof Exit)) {
                try {
                    // Read next command.
                    userInput = commandReader.readLine();
                    if (userInput == null) {
                        break;
                    }

                    // Parse command.
                    if (userInput.trim().equals("")) {
                        continue;
                    }
                    onlineAction = OnlineActionLoader.loadAction(userInput);
                    onlineActions.add(onlineAction);
                } catch (ParseCancellationException | InputMismatchException ex) {
                    System.out.printf("Error parsing command: [%s].%n", userInput);
                    System.out.printf("Caused by: %s.%n", ex.getMessage());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }

            // Wait for serverConnectionThread.
            onlineClientThread.join();
        } catch(IOException ex) {
            throw new RuntimeException(ex);
        } catch (InterruptedException ex) {
            // Ignore.
        }

        return serverResponses;
    }
}

