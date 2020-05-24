package org.linqs.psl.application.inference.online.actions;

import org.linqs.psl.reasoner.term.ReasonerTerm;

public class AddTerm extends OnlineAction {
    public ReasonerTerm termToAdd;

    public AddTerm(ReasonerTerm newTerm) {
        termToAdd = newTerm;
    }

    @Override
    public void initAction(String[] tokenized_command) throws IllegalArgumentException {
        // Pass
    }

    @Override
    public String getName() {
        return "AddTerm";
    }
}
