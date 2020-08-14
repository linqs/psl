package org.linqs.psl.application.inference.online.messages.responses;

import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.application.inference.online.messages.actions.QueryAtom;

import java.util.UUID;

public class ActionStatus extends OnlineResponse {
    private boolean success;
    private String statusMessage;

    public ActionStatus(OnlineAction onlineAction, boolean success, String statusMessage) {
        super(UUID.randomUUID(), String.format(
                "ActionStatus\t%s\t%s\t%s",
                onlineAction.getIdentifier(),
                Boolean.toString(success),
                statusMessage));
    }

    public ActionStatus(UUID identifier, String serverResponse) {
        super(identifier, serverResponse);
    }

    @Override
    public void setMessage(String newMessage) {
        parse(newMessage.split("\t", 4));

        message = String.format(
                "ActionStatus\t%s\t%s\t%s",
                onlineActionID,
                Boolean.toString(success),
                statusMessage);
    }

    private void parse(String[] parts) {
        assert(parts[0].equalsIgnoreCase("ActionStatus"));

        onlineActionID = UUID.fromString(parts[1].trim());
        success = Boolean.parseBoolean(parts[2].trim());
        statusMessage = parts[3].trim();
    }
}
