package org.linqs.psl.server.actions;

import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.sgd.term.SGDStreamingTermStore;

public class Close extends OnlineServerAction{

    public Close(SGDStreamingTermStore providedTermStore) {
        super(providedTermStore);
    }

    @Override
    public void executeAction() {}

    @Override
    public boolean close() {return true;}
}
