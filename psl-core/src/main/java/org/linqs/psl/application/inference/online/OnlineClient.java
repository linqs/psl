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
package org.linqs.psl.application.inference.online;

import org.linqs.psl.application.inference.online.messages.ModelInformation;
import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.application.inference.online.messages.actions.controls.Exit;
import org.linqs.psl.application.inference.online.messages.actions.controls.Stop;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.config.Options;
import org.linqs.psl.model.predicate.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * A client that communicates with an OnlineServer using OnlineMessages.
 */
public class OnlineClient implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(OnlineClient.class);

    private List<OnlineResponse> serverResponses;
    private BlockingQueue<OnlineMessage> actionQueue;
    private CountDownLatch modelRegistrationLatch;
    private String hostname;
    private int port;

    public OnlineClient(BlockingQueue<OnlineMessage> actionQueue, List<OnlineResponse> serverResponses,
            CountDownLatch modelRegistrationLatch) {
        this.serverResponses = serverResponses;
        this.actionQueue = actionQueue;
        this.hostname = Options.ONLINE_HOST.getString();
        this.port = Options.ONLINE_PORT_NUMBER.getInt();
        this.modelRegistrationLatch = modelRegistrationLatch;
    }

    public void run() {
        OnlineMessage onlineAction = null;

        try (
                Socket server = new Socket(hostname, port);
                ObjectOutputStream socketOutputStream = new ObjectOutputStream(server.getOutputStream());
                ObjectInputStream socketInputStream = new ObjectInputStream(server.getInputStream())) {
            // Read and register serverModel.
            registerServerModel(socketInputStream);
            modelRegistrationLatch.countDown();

            // Startup serverConnectionThread for reading server responses.
            ServerConnectionThread serverConnectionThread = new ServerConnectionThread(socketInputStream, serverResponses);
            serverConnectionThread.start();

            // Deque actions and send to server.
            do {
                // Dequeue next action.
                try {
                    onlineAction = actionQueue.take();
                } catch (InterruptedException ex) {
                    log.warn("Interrupted while taking an online action from the queue."
                            + " Stopping client session and not waiting for server responses.", ex);
                    socketOutputStream.writeObject(new Exit());
                    socketInputStream.close();
                    return;
                }
                log.trace("Sending Action {}", onlineAction);
                socketOutputStream.writeObject(onlineAction);
            } while (!(onlineAction instanceof Exit || onlineAction instanceof Stop));

            // Wait for serverConnectionThread.
            serverConnectionThread.join();
        } catch(IOException |  ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (InterruptedException ex) {
            log.warn("Client session interrupted. Client stopped.");
        }
    }

    private void registerServerModel(ObjectInputStream socketInputStream) throws IOException, ClassNotFoundException {
        // Get model information from server.
        ModelInformation modelInformation = (ModelInformation)socketInputStream.readObject();

        // Register model predicates.
        for (Predicate predicate: modelInformation.getPredicates()) {
            Predicate.registerPredicate(predicate);
            log.trace("Registered predicate: " + Predicate.get(predicate.getName()).toString() +
                    " Client Hash: " + Predicate.get(predicate.getName()).hashCode() +
                    " Server Hash: " + predicate.hashCode());
        }
    }

    /**
     * Private class for reading OnlineResponses from the OnlineServer.
     */
    private static class ServerConnectionThread extends Thread {
        private ObjectInputStream inputStream;
        private List<OnlineResponse> serverResponses;

        public ServerConnectionThread(ObjectInputStream inputStream, List<OnlineResponse> serverResponses) {
            this.inputStream = inputStream;
            this.serverResponses = serverResponses;
        }

        @Override
        public void run() {
            OnlineResponse response = null;

            while (true) {
                try {
                    response = (OnlineResponse)inputStream.readObject();
                } catch (EOFException ex) {
                    // Server closed socket.
                    break;
                } catch (IOException ex) {
                    // Unexpected IOException.
                    throw new RuntimeException(ex);
                } catch (ClassNotFoundException ex) {
                    log.warn("Unable to deserialized last OnlineResponse from server.");
                }
                serverResponses.add(response);
            }
        }
    }
}
