package org.linqs.psl.application.inference.online.messages.responses;

import org.linqs.psl.application.inference.online.messages.actions.OnlineAction;
import org.linqs.psl.application.inference.online.messages.actions.QueryAtom;

import java.util.UUID;

public class ActionAcknowledgement extends OnlineResponse {

    public ActionAcknowledgement(OnlineAction onlineAction) {
        super(UUID.randomUUID(), String.format("ActionACK\t%s", onlineAction.getIdentifier()));
    }

    public ActionAcknowledgement(UUID identifier, String serverResponse) {
        super(identifier, serverResponse);
    }

    @Override
    public void setMessage(String newMessage) {
        parse(newMessage.split("\t"));

        message = String.format(
                "ActionACK\t%s",
                onlineActionID);
    }

    private void parse(String[] parts) {
        assert(parts[0].equalsIgnoreCase("ActionACK"));

        onlineActionID = UUID.fromString(parts[1].trim());
    }
}
