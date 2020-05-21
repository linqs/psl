package org.linqs.psl.application.inference.mpe.online.actions;

import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

public class Continue extends OnlineAction{

    public Continue(StreamingTermStore providedTermStore) {
        super(providedTermStore);
    }

    @Override
    public void executeAction() {}

    @Override
    public boolean close() {return false;}
}
