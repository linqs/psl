package org.linqs.psl.server.actions;

import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

public class UpdateObservation extends OnlineServerAction{

    Predicate predicate;
    Constant[] arguments;
    float newValue;

    public UpdateObservation(StreamingTermStore providedTermStore, float value,
                             Predicate predicate, Constant... arguments) {
        super(providedTermStore);

        this.predicate = predicate;
        this.arguments = arguments;
        this.newValue = value;
    }

    @Override
    public void executeAction() {
        termStore.updateObservationValue(predicate, arguments, newValue);
    }

    @Override
    public boolean close() {return false;}
}
