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

import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.application.inference.online.messages.actions.Exit;
import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.application.inference.online.messages.actions.Stop;
import org.linqs.psl.application.inference.online.messages.responses.ActionAcknowledgement;
import org.linqs.psl.application.inference.online.messages.responses.ActionStatus;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.config.Options;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class that handles establishing a server socket and waiting for client connections.
 * Actions given by any client connections will be held in a shared queue and
 * accessible via the getAction() method.
 */
public class OnlineServer implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(OnlineServer.class);

    private ServerConnectionThread serverThread;
    private BlockingQueue<OnlineAction> queue;

    private ConcurrentHashMap<UUID, ClientConnectionThread> messageIDConnectionMap;

    public OnlineServer() {
        serverThread = new ServerConnectionThread();
        queue = new LinkedBlockingQueue<OnlineAction>();
        messageIDConnectionMap = new ConcurrentHashMap<UUID, ClientConnectionThread>();
    }

    /**
     * Start up the server on the configured port and wait for connections.
     * This does not block, as another thread will be waiting for connections.
     */
    public void start() {
        serverThread.start();
    }

    /**
     * Get the next action from the client.
     * If no action is already enqueued, this method will block indefinitely until an action is available.
     */
    public OnlineAction getAction() {
        OnlineAction nextAction = null;

        while (nextAction == null) {
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
        }

        return nextAction;
    }

    public void onActionExecution(OnlineAction action, OnlineResponse onlineResponse) {
        ClientConnectionThread clientConnectionThread = messageIDConnectionMap.get(action.getIdentifier());
        ObjectOutputStream outputStream = clientConnectionThread.outputStream;

        try {
            outputStream.writeObject(onlineResponse.toString());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        if (action instanceof Exit || action instanceof Stop) {
            // Interrupt waiting thread to finish closing.
            serverThread.close(clientConnectionThread);
        }

        if (onlineResponse instanceof ActionStatus) {
            messageIDConnectionMap.remove(action.getIdentifier());
        }
    }

    @Override
    public void close() {
        if (queue != null) {
            queue.clear();
            queue = null;
        }

        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.close();
            serverThread = null;
        }
    }

    /**
     * The thread that waits for client connections.
     */
    private class ServerConnectionThread extends Thread {
        private ServerSocket socket;
        private HashSet<ClientConnectionThread> clientConnections;

        public ServerConnectionThread() {
            clientConnections = new HashSet<ClientConnectionThread>();

            int port = Options.ONLINE_PORT_NUMBER.getInt();

            try {
                socket = new ServerSocket(port);
            } catch (IOException ex) {
                throw new RuntimeException("Could not establish socket on port " + port + ".", ex);
            }

            log.info("Online server started on port " + port + ".");
        }

        public void run() {
            Socket client = null;

            while (!isInterrupted()) {
                try {
                    client = socket.accept();
                } catch (IOException ex) {
                    if (isInterrupted()) {
                        break;
                    }

                    close();
                    throw new RuntimeException(ex);
                }

                ClientConnectionThread connectionThread = new ClientConnectionThread(client);
                clientConnections.add(connectionThread);
                connectionThread.start();
            }

            close();
        }

        public void close(ClientConnectionThread clientConnectionThread) {
            // Wake up waiting thread.
            synchronized (clientConnectionThread) {
                clientConnectionThread.notify();
            }

            clientConnections.remove(clientConnectionThread);
        }

        public void close() {
            if (clientConnections != null) {
                for (ClientConnectionThread clientConnection : clientConnections) {
                    close(clientConnection);
                }

                clientConnections = null;
            }

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    // Ignore.
                }

                socket = null;
            }
        }
    }

    private class ClientConnectionThread extends Thread {
        public Socket socket;
        public ObjectInputStream inputStream;
        public ObjectOutputStream outputStream;

        public ClientConnectionThread(Socket socket) {
            this.socket = socket;

            try {
                inputStream = new ObjectInputStream(socket.getInputStream());
                outputStream = new ObjectOutputStream(socket.getOutputStream());
            } catch (IOException ex) {
                close();
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void run() {
            OnlineMessage clientMessage = null;
            while (socket.isConnected() && !isInterrupted()) {
                try {
                    clientMessage = OnlineMessage.getOnlineMessage(inputStream.readObject().toString());
                    OnlineAction newAction = OnlineAction.getAction(clientMessage.getIdentifier(), clientMessage.getMessage());

                    // Acknowledge new action received.
                    outputStream.writeObject(new ActionAcknowledgement(newAction).toString());

                    // Queue new action.
                    messageIDConnectionMap.put(newAction.getIdentifier(), this);
                    queue.put(newAction);

                    if (newAction instanceof Exit || newAction instanceof Stop) {
                        // Break loop.
                        break;
                    }
                } catch (InterruptedException ex) {
                    break;
                } catch (IOException | ClassNotFoundException ex) {
                    close();
                    throw new RuntimeException(ex);
                }
            }

            // Wait until actions are finished executing before closing socket.
            // The onActionExecution callback will interrupt wait when the exit action is finished executing.
            synchronized(this) {
                try {
                    this.wait();
                } catch (InterruptedException ex) {
                    // Ignore.
                }
            }

            close();
        }

        public synchronized void close() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    // Ignore.
                }

                socket = null;
            }
        }
    }
}
