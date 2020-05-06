package org.linqs.psl.server;

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
        clientNextAction = null;
    }

    public synchronized OnlineServerAction getNextAction() {
        // TODO: get next action from client for now just return the class variable clientNextAction
        // TODO: initially class
        while (clientNextAction == null) {
            continue;
        }
        return clientNextAction;
    }
}
