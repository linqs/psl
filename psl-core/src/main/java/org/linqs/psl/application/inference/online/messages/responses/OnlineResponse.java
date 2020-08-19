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
package org.linqs.psl.application.inference.online.messages.responses;

import org.linqs.psl.application.inference.online.messages.OnlineMessage;

import java.util.UUID;

public abstract class OnlineResponse extends OnlineMessage {
    protected UUID onlineActionID;

    public OnlineResponse(UUID identifier, String serverResponse) {
        super(identifier, serverResponse);

        this.onlineActionID = null;
    }

    /**
     * Construct an OnlineAction given the name and necessary information.
     */
    // TODO: Move to online message.
    public static OnlineResponse getResponse(String serverResponse) {
        return getResponse(UUID.randomUUID(), serverResponse);
    }

    public static OnlineResponse getResponse(UUID identifier, String serverResponse) {
        String responseClass = serverResponse.split("\t")[0].trim();

        if (responseClass.equalsIgnoreCase("ActionACK")) {
            return new ActionAcknowledgement(identifier, serverResponse);
        } else if (responseClass.equalsIgnoreCase("ModelInfo")) {
            return new ModelInformation(identifier, serverResponse);
        } else if (responseClass.equalsIgnoreCase("ActionStatus")) {
            return new ActionStatus(identifier, serverResponse);
        } else if (responseClass.equalsIgnoreCase("Query")) {
            return new QueryAtomResponse(identifier, serverResponse);
        } else {
            throw new IllegalArgumentException("Unknown online response: '" + responseClass + "'.");
        }
    }

    public UUID getOnlineActionID() {
        return onlineActionID;
    }
}
