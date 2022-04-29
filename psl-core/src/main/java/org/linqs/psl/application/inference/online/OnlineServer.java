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
import org.linqs.psl.application.inference.online.messages.responses.ActionStatus;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.config.Options;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.SystemUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * A class for listening for new client connections, queueing actions from OnlineClients, and sending OnlineResponses.
 */
public class OnlineServer {
    private static final Logger log = LoggerFactory.getLogger(OnlineServer.class);

    public static final String TEMP_FILE_DIR_PREFIX = "onlinePSLServer";
    public static final String TEMP_FILE_NAME = "onlinePSLServer.lock";

    private boolean listening;
    private ServerConnectionThread serverThread;
    private Set<ClientConnectionThread> clientConnectionThreads;
    private BlockingQueue<OnlineMessage> queue;
    private ConcurrentMap<UUID, ClientConnectionThread> messageIDConnectionMap;
    private List<Rule> rules;
    private File tempFile;

    public OnlineServer(List<Rule> rules) {
        listening = false;
        serverThread = new ServerConnectionThread();
        tempFile = null;
        queue = new LinkedBlockingQueue<OnlineMessage>();
        messageIDConnectionMap = new ConcurrentHashMap<UUID, ClientConnectionThread>();
        clientConnectionThreads = Collections.newSetFromMap(new ConcurrentHashMap<ClientConnectionThread, Boolean>());
        this.rules = rules;
    }

    /**
     * Start up the server and listen for new connections on the configured port.
     * This method does not block. A new thread is started to wait for connections.
     */
    public void start() {
        listening = true;
        serverThread.start();
        serverThread.blockUntilReady();
    }

    /**
     * Create a temporary file in the default temporary file directory.
     * The creation of the temporary file may be used as a signal that this server is
     * ready to accept client connections and online actions.
     */
    private void createServerTempFile() {
        String tempDirectory = SystemUtils.getTempDir(TEMP_FILE_DIR_PREFIX);
        FileUtils.mkdir(tempDirectory);

        tempFile = new File(new File(tempDirectory), TEMP_FILE_NAME);
        try {
            if (!tempFile.createNewFile()) {
                throw new IllegalStateException(String.format("Temp file already exists at: %s", tempFile.getAbsolutePath()));
            }
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Error creating temp file at: %s", tempFile.getAbsolutePath()), ex);
        }
        tempFile.deleteOnExit();
        log.debug(String.format("Temporary server config file at: %s", tempFile.getAbsolutePath()));
    }

    /**
     * Get the next action from the client.
     * This method will block until an action is available to take from the queue.
     */
    public OnlineMessage getAction() {
        OnlineMessage nextAction = null;

        do {
            try {
                nextAction = queue.take();
            } catch (InterruptedException ex) {
                log.warn("Interrupted while taking an online action from the queue.", ex);
                return null;
            }

            if (nextAction instanceof Exit) {
                onActionExecution(nextAction, new ActionStatus(nextAction, true, "Session Closed."));
                nextAction = null;
            }
        } while (nextAction == null);

        return nextAction;
    }

    public void onActionExecution(OnlineMessage action, OnlineResponse onlineResponse) {
        ClientConnectionThread clientConnectionThread = messageIDConnectionMap.get(action.getIdentifier());
        ObjectOutputStream outputStream = clientConnectionThread.outputStream;

        try {
            outputStream.writeObject(onlineResponse);
        } catch (IOException ex) {
            log.warn(String.format("Failed to send client onlineResponse: %s", onlineResponse), ex);
        }

        if (action instanceof Exit || action instanceof Stop) {
            closeClient(clientConnectionThread);
        }

        if (onlineResponse instanceof ActionStatus) {
            messageIDConnectionMap.remove(action.getIdentifier());
        }
    }

    public void closeClient(ClientConnectionThread clientConnectionThread) {
        clientConnectionThread.close();
        clientConnectionThreads.remove(clientConnectionThread);
    }

    public void addClient(ClientConnectionThread clientConnectionThread) {
        clientConnectionThreads.add(clientConnectionThread);
    }

    public void close() {
        listening = false;

        if (tempFile != null) {
            FileUtils.delete(tempFile);
            tempFile = null;
        }

        if (serverThread != null) {
            serverThread.close();
            serverThread = null;
        }

        if (clientConnectionThreads != null) {
            for (ClientConnectionThread clientConnection : clientConnectionThreads) {
                closeClient(clientConnection);
            }
            clientConnectionThreads = null;
        }

        if (queue != null) {
            queue.clear();
            queue = null;
        }
    }

    /**
     * The thread that waits for client connections.
     */
    private class ServerConnectionThread extends Thread {
        private int port;
        private ServerSocket socket;
        private Semaphore readyLock;

        public ServerConnectionThread() {
            port = Options.ONLINE_PORT_NUMBER.getInt();
            socket = null;

            readyLock = new Semaphore(1);
            try {
                readyLock.acquire();
            } catch (InterruptedException ex) {
                throw new RuntimeException("Unable to acquire a new lock.", ex);
            }
        }

        private void openListenSocket() {
            try {
                socket = new ServerSocket(port);
            } catch (IOException ex) {
                throw new RuntimeException(String.format("Could not establish socket on port %s.", port));
            }
        }

        /**
         * Block until the server has opened its socket.
         */
        public void blockUntilReady() {
            try {
                readyLock.acquire();
            } catch (InterruptedException ex) {
                throw new RuntimeException("Unable to acquire ready lock.", ex);
            }

            readyLock.release();
        }

        @Override
        public void run() {
            Socket client = null;
            ClientConnectionThread connectionThread = null;

            openListenSocket();
            createServerTempFile();
            readyLock.release();
            log.info(String.format("Online server started on port %s.", port));

            while (listening) {
                try {
                    client = socket.accept();
                } catch (IOException ex) {
                    if (socket.isClosed()) {
                        continue;
                    }
                    throw new RuntimeException(ex);
                }

                connectionThread = new ClientConnectionThread(client);
                addClient(connectionThread);
                connectionThread.start();
            }
        }

        public void close() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    // Ignore.
                }
            }
        }
    }

    private class ClientConnectionThread extends Thread {
        public Socket socket;
        public ObjectInputStream inputStream;
        public ObjectOutputStream outputStream;

        public ClientConnectionThread(Socket socket) {
            this.socket = socket;

            setUncaughtExceptionHandler(new ClientConnectionExceptionHandler());
        }

        private void initializeConnection() {
            try {
                inputStream = new ObjectInputStream(socket.getInputStream());
                outputStream = new ObjectOutputStream(socket.getOutputStream());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        private void sendModelInformation() {
            // Send Client model information for action validation.
            List<Predicate> predicates = new ArrayList<Predicate>(Predicate.getAll());
            List<Predicate> modelInformationPredicates = new ArrayList<Predicate>();

            // Add non-functional predicates.
            for (Predicate predicate : predicates) {
                if (!(predicate instanceof FunctionalPredicate)) {
                    modelInformationPredicates.add(predicate);
                }
            }

            ModelInformation modelInformation = new ModelInformation(
                    modelInformationPredicates.toArray(new Predicate[]{}),
                    rules.toArray(new Rule[]{}));
            try {
                outputStream.writeObject(modelInformation);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void run() {
            OnlineMessage newAction = null;

            initializeConnection();

            sendModelInformation();

            // Read and queue new actions from client until exit or stop.
            while (true) {
                try {
                    newAction = (OnlineMessage)inputStream.readObject();
                    log.trace(String.format("Server received action from client: %s", newAction));
                } catch (EOFException ex) {
                    throw new RuntimeException("Client closed socket without Exit or Stop action.", ex);
                } catch (IOException ex) {
                    if (socket.isClosed()) {
                        // Socket closed before client issued Stop or Exit.
                        // This can occur when a Stop is issued while other clients are still connected.
                        break;
                    }
                    // Unexpected IOException.
                    throw new RuntimeException(ex);
                } catch(ClassNotFoundException ex) {
                    log.warn("Failed to deserialized last OnlineMessage from client.");
                    continue;
                }

                try {
                    messageIDConnectionMap.put(newAction.getIdentifier(), this);
                    queue.put(newAction);
                } catch (InterruptedException ex) {
                    continue;
                }

                if (newAction instanceof Exit || newAction instanceof Stop) {
                    // Break loop.
                    break;
                }
            }
        }

        public void close() {
            try {
                socket.close();
            } catch (IOException ex) {
                // Ignore.
            }
        }
    }

    private class ClientConnectionExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            if (!(thread instanceof ClientConnectionThread)) {
                throw new RuntimeException("ClientConnectionExceptionHandler can only be used by ClientConnectionThreads.", ex);
            }

            log.warn(String.format("Uncaught exception in ClientConnectionThread. "
                    + " Exception message: %s", ex.getMessage()));

            ClientConnectionThread clientConnectionThread = (ClientConnectionThread)thread;
            closeClient(clientConnectionThread);
        }
    }
}
