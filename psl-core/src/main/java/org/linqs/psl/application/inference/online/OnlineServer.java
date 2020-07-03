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

import org.linqs.psl.config.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Online server class.
 * Listens and establishes client connections and enqueues objects provided by client to a shared queue.
 */
public class OnlineServer<T> extends Thread{
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private boolean waiting;
    private int port;
    private int max_clients;
    private int connections;
    private InetAddress addr;
    private ServerSocket server;
    private LinkedBlockingQueue<T> queue;
    private Set<ServerClientThread> threads;

    public OnlineServer() throws IOException {
        waiting = false;

        // Startup server listening on a port from the list of ports
        port = Options.ONLINE_PORT_NUMBER.getInt();
        max_clients = Options.ONLINE_MAX_CLIENTS.getInt();
        addr = InetAddress.getByName(Options.ONLINE_HOST_NAME.getString());

        try {
            server = new ServerSocket(port, max_clients, addr);
        } catch (IOException ex) {
            log.debug(ex.getMessage());
        }

        if (server == null) {
            throw new IOException("Port not free " + port);
        }

        log.info("Started Server at port: " + server.getLocalPort() + " and IP: + " + server.getInetAddress());

        this.queue = new LinkedBlockingQueue<T>();
        this.threads = new HashSet<>();
    }

    private Socket connectClient() throws IOException {
        return server.accept();
    }

    private void addThread(Socket client) {
        ServerClientThread sct = new ServerClientThread(client);
        sct.start();
        threads.add(sct);
        connections++;
    }

    private void removeThread(ServerClientThread sct) {
        threads.remove(sct);
        connections--;
        if (waiting) {
            log.debug("Waking Server up to accept new connections.");
            notify();
        }
    }

    public void run() {
        while(!isInterrupted()) {
            Socket client = null;
            try {
                if (connections < max_clients) {
                    try {
                        client = connectClient();
                    } catch (IOException e) {
                        log.debug("Exception Connecting to Client");
                        log.debug(e.getMessage());
                        break;
                    }
                    addThread(client);
                } else {
                    log.debug("Too Many Clients Connected to Server. Waiting for Openings");
                    wait();
                }
            } catch (InterruptedException e) {
                log.debug("Server Interrupted");
                log.debug(e.getMessage());
            }
        }
    }

    public void enqueue(T newObject) {
        queue.offer(newObject);
    }

    public synchronized T dequeClientInput() throws InterruptedException {
        return queue.take();
    }

    public void closeServer() {
        try {
            server.close();
        } catch (IOException e) {
            log.debug(e.getMessage());
        } finally {
            for(ServerClientThread childThread : threads) {
                childThread.close();
            }
        }
    }

    private class ServerClientThread extends Thread {

        private Socket client;
        private ObjectInputStream inStream;

        ServerClientThread(Socket inSocket) {
            client = inSocket;
            try {
                inStream = new ObjectInputStream(client.getInputStream());
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        }

        public void close() {
            interrupt();

            try {
                inStream.close();
                client.close();
            } catch (IOException e) {
                log.debug(e.getMessage());
            } finally {
                removeThread(this);
            }
        }

        public void run() {
            T newCommand;
            while (client.isConnected() && !isInterrupted()) {
                try {
                    newCommand = (T) inStream.readObject();
                    enqueue(newCommand);
                } catch (EOFException e) {
                    log.debug("Client Disconnected");
                    log.debug(e.getMessage());
                    break;
                } catch (IOException e) {
                    log.debug("Error reading object from client");
                    log.debug(e.getMessage());
                    log.debug(Arrays.toString(e.getStackTrace()));
                } catch (ClassNotFoundException e) {
                    log.debug("Error casting object serialized by client");
                    log.debug(e.getMessage());
                } catch (NullPointerException e) {
                    log.debug("Error reading object");
                    log.debug(e.getMessage());
                    log.debug(Arrays.toString(e.getStackTrace()));
                }
            }

            close();
        }
    }
}
