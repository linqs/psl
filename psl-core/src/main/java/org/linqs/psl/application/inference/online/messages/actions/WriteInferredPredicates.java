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
package org.linqs.psl.application.inference.online.messages.actions;

import org.linqs.psl.util.StringUtils;

import java.util.UUID;

/**
 * Write out targets on the server side.
 * String format: WRITE [path]
 */
public class WriteInferredPredicates extends OnlineAction {
    private String outputDirectoryPath;

    public WriteInferredPredicates(UUID actionID, String clientCommand) {
        super(actionID, clientCommand);
    }

    public String getOutputDirectoryPath() {
        return outputDirectoryPath;
    }

    @Override
    public void setMessage(String newMessage) {
        parse(newMessage.split("\t"));

        if (outputDirectoryPath == null) {
            message = String.format("WRITE");
        } else {
            message = String.format("WRITE\t%s", outputDirectoryPath);
        }
    }

    @Override
    protected void parse(String[] parts) throws IllegalArgumentException {
        assert(parts[0].equalsIgnoreCase("write"));

        if (parts.length > 2) {
            throw new IllegalArgumentException("Too many arguments.");
        }

        outputDirectoryPath = null;
        if (parts.length == 2) {
            outputDirectoryPath = parts[1];
        }
    }
}