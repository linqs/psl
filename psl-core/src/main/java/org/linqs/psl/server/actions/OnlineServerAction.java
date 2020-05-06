package org.linqs.psl.server.actions;

import org.linqs.psl.reasoner.sgd.term.SGDStreamingTermStore;

public abstract class OnlineServerAction {
    SGDStreamingTermStore termStore;

    public OnlineServerAction(SGDStreamingTermStore providedTermStore){

        termStore = providedTermStore;

    }

    public abstract void executeAction ();

    public abstract boolean close ();

}
