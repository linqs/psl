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
package org.linqs.psl.cli;

import org.linqs.psl.application.inference.online.actions.OnlineAction;
import org.linqs.psl.application.inference.online.actions.OnlineActionException;
import org.linqs.psl.config.Options;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * A client that takes input on stdin and passes it to the online host specified in configuration.
 */
public class OnlineClient {
    public static final String EXIT_STRING = "exit";

    // Static only.
    private OnlineClient() {}

    public static void run() {
        run(Options.ONLINE_HOST.getString(), Options.ONLINE_PORT_NUMBER.getInt());
    }

    public static void run(String hostname, int port) {
        try (
                Socket server = new Socket(hostname, port);
                ObjectOutputStream out = new ObjectOutputStream(server.getOutputStream());
                BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in))) {
            String userInput = null;

            while (true) {
                try {
                    userInput = stdin.readLine();
                    if (userInput == null) {
                        break;
                    }

                    userInput = userInput.trim();
                    if (userInput.equals("")) {
                        continue;
                    } else if (userInput.equalsIgnoreCase(EXIT_STRING)) {
                        break;
                    }

                    out.writeObject(OnlineAction.getOnlineAction(userInput));
                } catch (OnlineActionException ex) {
                    System.err.println(String.format("Error parsing command: [%s].", userInput));
                    System.err.println(ex);
                    ex.printStackTrace(System.err);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(
                    String.format("Error establishing connection to the online server (%s:%d).", hostname, port),
                    ex);
        }
    }
}
