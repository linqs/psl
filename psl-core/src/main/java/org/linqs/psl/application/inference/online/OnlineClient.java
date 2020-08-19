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
import org.linqs.psl.application.inference.online.messages.responses.ModelInformation;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.config.Options;

import org.linqs.psl.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * A client that takes input on stdin and passes it to the online host specified in configuration.
 */
public class OnlineClient {
    private static final Logger log = LoggerFactory.getLogger(OnlineClient.class);

    public static final String EXIT_STRING = "exit";

    public static final int NUM_CONNECTION_RETRIES = 5;

    // Static only.
    //TODO: On server socket connection get model from server.
    private OnlineClient() {}

    public static List<OnlineResponse> run(InputStream in, PrintStream out) {
        return run(Options.ONLINE_HOST.getString(), Options.ONLINE_PORT_NUMBER.getInt(), in, out);
    }

    public static List<OnlineResponse> run(String hostname, int port, InputStream in, PrintStream out) {
        Boolean serverConnected = false;
        ArrayList<OnlineResponse> serverResponses = new ArrayList<OnlineResponse>();

        int i = 0;
        while (!serverConnected) {
            try (
                    Socket server = new Socket(hostname, port);
                    ObjectOutputStream socketOutputStream = new ObjectOutputStream(server.getOutputStream());
                    ObjectInputStream socketInputStream = new ObjectInputStream(server.getInputStream());
                    BufferedReader commandReader = new BufferedReader(new InputStreamReader(in))) {
                boolean exit = false;
                String userInput = null;

                serverConnected = true;

                // Get model information from server.
                ModelInformation modelInformation = null;
                try {
                    modelInformation = (ModelInformation)OnlineResponse.getResponse(
                            OnlineMessage.getOnlineMessage(socketInputStream.readObject().toString()).getMessage());
                } catch (IOException | ClassNotFoundException ex) {
                    throw new RuntimeException(ex);
                }

                // Startup serverConnectionThread for reading server responses.
                ServerConnectionThread serverConnectionThread = new ServerConnectionThread(server, socketInputStream, out, serverResponses);
                serverConnectionThread.start();

                // Read and parse userInput to send actions to server.
                while (!exit) {
                    try {
                        // Read next command.
                        userInput = commandReader.readLine();
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
                        socketOutputStream.writeObject(onlineAction.toString());
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
                i ++;
                if (i < NUM_CONNECTION_RETRIES) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new RuntimeException(
                            String.format("Error establishing connection to the online server (%s:%d).", hostname, port), ex);
                }
            } catch (InterruptedException ex) {
                log.error("Client session interrupted");
            }
        }

        return serverResponses;
    }

    /**
     * Private class for reading OnlineResponse objects sent from server.
     */
    // TODO: ArrayList -> List
    private static class ServerConnectionThread extends Thread {
        private ObjectInputStream inputStream;
        private PrintStream out;
        private Socket socket;
        private ArrayList<OnlineResponse> serverResponses;

        public ServerConnectionThread(Socket socket, ObjectInputStream inputStream, PrintStream out, ArrayList<OnlineResponse> serverResponses) {
            this.socket = socket;
            this.inputStream = inputStream;
            this.out = out;
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
                out.println(serverMessage.getMessage());
            }
        }
    }
}

