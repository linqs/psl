package org.linqs.psl.server.actions;

import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

public class Close extends OnlineServerAction{

    public Close(StreamingTermStore providedTermStore) {
        super(providedTermStore);
    }

    @Override
    public void executeAction() {}

    @Override
    public boolean close() {return true;}
}
