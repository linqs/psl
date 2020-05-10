package org.linqs.psl.server.actions;

import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

public abstract class OnlineServerAction {
    StreamingTermStore termStore;

    public OnlineServerAction(StreamingTermStore providedTermStore){

        termStore = providedTermStore;

    }

    public abstract void executeAction ();

    public abstract boolean close ();

}
