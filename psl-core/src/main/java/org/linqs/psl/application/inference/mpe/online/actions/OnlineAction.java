package org.linqs.psl.application.inference.mpe.online.actions;

import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

public abstract class OnlineAction {
    StreamingTermStore termStore;

    public OnlineAction(StreamingTermStore providedTermStore){

        termStore = providedTermStore;

    }

    public abstract void executeAction ();

    public abstract boolean close ();

}
