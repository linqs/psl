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

import org.linqs.psl.application.inference.online.actions.OnlineAction;
import org.linqs.psl.config.Options;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A class that handles establishing a server socket and waiting for client connections.
 * Actions given by any client connections will be held in a shared queue and
 * accessible via the getAction() method.
 */
public class OnlineServer implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(OnlineServer.class);

    private ServerConnectionThread serverThread;
    private BlockingQueue<OnlineAction> queue;

    public OnlineServer() {
        serverThread = new ServerConnectionThread();
        queue = new LinkedBlockingQueue<OnlineAction>();
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
        try {
            return queue.take();
        } catch (InterruptedException ex) {
            log.warn("Interrupted while taking an online action from the queue.", ex);
            return null;
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
        private Set<ClientConnectionThread> clientConnections;

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

        public void close() {
            if (clientConnections != null) {
                for (ClientConnectionThread clientConnection : clientConnections) {
                    clientConnection.close();
                }

                clientConnections.clear();
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
        private Socket socket;
        private ObjectInputStream inputStream;

        public ClientConnectionThread(Socket socket) {
            this.socket = socket;

            try {
                inputStream = new ObjectInputStream(socket.getInputStream());
            } catch (IOException ex) {
                close();
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void run() {
            while (socket.isConnected() && !isInterrupted()) {
                try {
                    queue.put((OnlineAction)inputStream.readObject());
                } catch (InterruptedException ex) {
                    break;
                } catch (IOException ex) {
                    close();
                    throw new RuntimeException(ex);
                } catch (ClassNotFoundException ex) {
                    close();
                    throw new RuntimeException(ex);
                }
            }

            close();
        }

        public void close() {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    // Ignore.
                }

                inputStream = null;
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
}
