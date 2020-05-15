package org.linqs.psl.server;

import org.linqs.psl.server.actions.Close;
import org.linqs.psl.server.actions.OnlineServerAction;

public class OnlineServer {
    private OnlineServerAction clientNextAction;

    public OnlineServer(){
        clientNextAction = null;
    }

    public synchronized void setNextAction(OnlineServerAction action) {
        clientNextAction = action;
    }

    public synchronized void clearNextAction() {
        clientNextAction = new Close(null);
    }

    public void parseClientCommand(){
        //TODO: (Charles) Client can either provide
    }

    public synchronized OnlineServerAction getNextAction() {
        // TODO: (Charles)  get next action from client for now just return the class variable clientNextAction
        // TODO: (Charles)  initially
        while (clientNextAction == null) {
            continue;
        }
        return clientNextAction;
    }
}
