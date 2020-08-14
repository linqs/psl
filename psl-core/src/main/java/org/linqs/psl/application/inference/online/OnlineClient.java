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
import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.application.inference.online.messages.actions.OnlineActionException;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.config.Options;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

/**
 * A client that takes input on stdin and passes it to the online host specified in configuration.
 */
public class OnlineClient {
    private static final Logger log = LoggerFactory.getLogger(OnlineClient.class);

    public static final String EXIT_STRING = "exit";

    // Static only.
    private OnlineClient() {}

    public static ArrayList<OnlineResponse> run() {
        return run(Options.ONLINE_HOST.getString(), Options.ONLINE_PORT_NUMBER.getInt(), System.in);
    }

    public static ArrayList<OnlineResponse> run(InputStream in) {
        return run(Options.ONLINE_HOST.getString(), Options.ONLINE_PORT_NUMBER.getInt(), in);
    }

    public static ArrayList<OnlineResponse> run(String hostname, int port, InputStream in) {
        ArrayList<OnlineResponse> serverResponses = new ArrayList<OnlineResponse>();

        try (
                Socket server = new Socket(hostname, port);
                ObjectOutputStream outputStream = new ObjectOutputStream(server.getOutputStream());
                ObjectInputStream inputStream = new ObjectInputStream(server.getInputStream());
                BufferedReader stdin = new BufferedReader(new InputStreamReader(in))) {
            boolean exit = false;
            String userInput = null;

            ServerConnectionThread serverConnectionThread = new ServerConnectionThread(server, inputStream, serverResponses);
            serverConnectionThread.start();

            while (!exit) {
                try {
                    // Read next command.
                    userInput = stdin.readLine();
                    if (userInput == null) {
                        break;
                    }

                    // Parse command.
                    userInput = userInput.trim();
                    if (userInput.equals("")) {
                        continue;
                    }
                    exit = (userInput.equalsIgnoreCase(EXIT_STRING));

                    OnlineAction onlineAction = OnlineAction.getAction(userInput);
                    outputStream.writeObject(onlineAction.toString());

                } catch (OnlineActionException ex) {
                    log.error(String.format("Error parsing command: [%s].", userInput));
                    log.error(ex.getMessage());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }

            // Wait for serverConnectionThread.
            serverConnectionThread.join();

        } catch (IOException ex) {
            throw new RuntimeException(
                    String.format("Error establishing connection to the online server (%s:%d).", hostname, port), ex);
        } catch (InterruptedException ex) {
            log.error("Client session interrupted");
        }

        return serverResponses;
    }

    /**
     * Private class for reading OnlineResponse objects sent from server
     */
    private static class ServerConnectionThread extends Thread {
        private ObjectInputStream inputStream;
        private Socket socket;
        private ArrayList<OnlineResponse> serverResponses;

        public ServerConnectionThread(Socket socket, ObjectInputStream inputStream, ArrayList<OnlineResponse> serverResponses) {
            this.socket = socket;
            this.inputStream = inputStream;
            this.serverResponses = serverResponses;
        }

        @Override
        public void run() {
            OnlineMessage serverMessage = null;

            while (socket.isConnected() && !isInterrupted()) {
                try {
                    serverMessage = OnlineMessage.getOnlineMessage(inputStream.readObject().toString());
                } catch (EOFException ex) {
                    // Done.
                    break;
                } catch (IOException | ClassNotFoundException ex) {
                    throw new RuntimeException(ex);
                }

                serverResponses.add(OnlineResponse.getResponse(serverMessage.getMessage()));
            }
        }
    }
}

