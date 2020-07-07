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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class OnlineClient {
    private String hostname;
    private int portNumber;

    public OnlineClient() {
        hostname = Options.ONLINE_HOST_NAME.getString();
        portNumber = Options.ONLINE_PORT_NUMBER.getInt();
    }

    public void run() {
        ObjectOutputStream out = null;
        String userInput = null;
        Socket server = null;

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

        try {
            server = new Socket(hostname, portNumber);
        } catch (IOException ex) {
            throw new RuntimeException(String.format(
                    "Exception thrown when connecting to server at hostname: %s and port number: %d",
                    hostname, portNumber), ex);
        }

        try {
            out = new ObjectOutputStream(server.getOutputStream());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        try {
            while (!(userInput = stdIn.readLine().trim()).equalsIgnoreCase("exit")) {
                try {
                    out.writeObject(OnlineAction.getOnlineAction(userInput));
                } catch (OnlineActionException ex) {
                    System.err.println(String.format("Error parsing command: %s", userInput));
                    ex.printStackTrace(System.err);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        try {
            server.close();
            stdIn.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
