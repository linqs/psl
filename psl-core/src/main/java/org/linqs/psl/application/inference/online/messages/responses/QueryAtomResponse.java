package org.linqs.psl.application.inference.online.messages.responses;

import org.linqs.psl.application.inference.online.messages.actions.QueryAtom;

import java.util.UUID;

public class QueryAtomResponse extends OnlineResponse {
    private double atomValue;

    public QueryAtomResponse(QueryAtom onlineAction, double atomValue) {
        super(UUID.randomUUID(), String.format(
                "Query\t%s\t%f",
                onlineAction.getIdentifier(),
                atomValue));
    }

    public QueryAtomResponse(UUID identifier, String serverResponse) {
        super(identifier, serverResponse);
    }

    public double getAtomValue() {
        return atomValue;
    }

    @Override
    public void setMessage(String newMessage) {
        parse(newMessage.split("\t"));

        message = String.format(
                "Query\t%s\t%f",
                onlineActionID,
                atomValue);
    }

    private void parse(String[] parts) {
        assert(parts[0].equalsIgnoreCase("query"));

        onlineActionID = UUID.fromString(parts[1].trim());
        atomValue = Double.parseDouble(parts[2].trim());
    }
}
