package org.linqs.psl.application.inference.mpe.online.actions;

import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.sgd.term.SGDStreamingTermStore;

public class AddTerm extends OnlineAction{
    SGDObjectiveTerm termToAdd;

    public AddTerm(SGDStreamingTermStore providedTermStore, SGDObjectiveTerm newTerm) {
        super(providedTermStore);
        termToAdd = newTerm;
    }

    public SGDObjectiveTerm getTermToAdd (){
        return termToAdd;
    }

    @Override
    public void executeAction() {
        termStore.add(termToAdd);
    }

    @Override
    public boolean close() {return false;}
}
