package org.linqs.psl.server.actions;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

public class UpdateObservation extends OnlineServerAction{

    public UpdateObservation(StreamingTermStore providedTermStore, float value,
                             Predicate predicate, Constant... arguments) {
        super(providedTermStore);

        // ToDo: (Charles) Get the atom that
        this.atom = atom;
    }

    @Override
    public void executeAction() {
        termStore.updateObservationValue(atom, newValue);
    }

    @Override
    public boolean close() {return false;}
}
