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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Parse CLI client commands.
 * TODO (Charles): This class could accept more information about the server model to perform more interesting validation.
 */
public class ClientCommandLoader {
    private static final Logger log = LoggerFactory.getLogger(ClientCommandLoader.class);

    /**
     * Method for parsing client commands, performing some validation, and creating new action instances.
     * The expected format for commands are: <ActionClassName>\t<ActionSpecificArgs...>
     */
    public static OnlineAction parseClientCommand(String clientCommand) throws RuntimeException {
        log.trace("Parsing: " + clientCommand);

        // tokenize
        String[] tokenized_command = clientCommand.split("\t");

        // Construct OnlineAction
        String className = tokenized_command[0];
        log.trace("Creating Action Type: " + className);
        OnlineAction onlineAction = OnlineAction.getOnlineAction(className);
        log.trace("Initializing Action: " + className);
        onlineAction.initAction(tokenized_command);
        log.trace("Action Initialized");
        return onlineAction;
    }
}
