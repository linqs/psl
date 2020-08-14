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
package org.linqs.psl.application.inference.online.messages;

import java.io.Serializable;
import java.util.UUID;

public class OnlineMessage implements Serializable {
    private UUID identifier;
    protected String message;

    public OnlineMessage(UUID identifier, String message) {
        this.identifier = identifier;
        setMessage(message);
    }

    @Override
    public String toString() {
        return String.format(
                "%s\t%s",
                identifier,
                message);
    }

    public static OnlineMessage getOnlineMessage(String string) {
        String[] parts = string.split("\t", 2);
        UUID identifier = UUID.fromString(parts[0].trim());
        String message = parts[1];

        return new OnlineMessage(identifier, message);
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public UUID getIdentifier() {
        return identifier;
    }
}
