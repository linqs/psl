package org.linqs.psl.application.inference.mpe.online.actions;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

public class UpdateObservation extends OnlineAction{

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
        termStore.updateValue(predicate, arguments, newValue);
    }

    @Override
    public boolean close() {return false;}
}
