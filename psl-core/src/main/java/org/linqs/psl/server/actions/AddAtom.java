package org.linqs.psl.server.actions;

import org.linqs.psl.application.inference.mpe.SGDOnlineInference;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.reasoner.sgd.term.SGDStreamingTermStore;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

import java.util.List;

public class AddAtom extends OnlineServerAction{
    OnlineAtomManager atomManager;
    List<Rule> rules;

    public AddAtom(SGDStreamingTermStore termStore, OnlineAtomManager onlineAtomManager, List<Rule> rules) {
        super(termStore);
        this.atomManager = onlineAtomManager;
        this.rules = rules;
    }

    public void addObservedAtom(Predicate predicate, Float value, Constant... arguments){
        atomManager.addObservedAtom(predicate, value, arguments);
    }

    public void addRandomVariableAtom(StandardPredicate predicate, Float value, Constant... arguments){
        atomManager.addRandomVariableAtom(predicate, value, arguments);
    }

    @Override
    public void executeAction() {
        atomManager.activateAtoms(rules, (SGDStreamingTermStore) termStore);
    }

    @Override
    public boolean close() {return false;}
}
