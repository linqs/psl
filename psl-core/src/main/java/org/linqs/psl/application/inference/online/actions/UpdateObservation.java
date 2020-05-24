package org.linqs.psl.application.inference.online.actions;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateObservation extends OnlineAction{

    private String predicateName;
    private Constant[] arguments;
    private float newValue;
    private static final Logger log = LoggerFactory.getLogger(UpdateObservation.class);

    public UpdateObservation() {

    }

    public String getPredicateName() {
        return this.predicateName;
    }

    public float getValue() {
        return this.newValue;
    }

    public Constant[] getArguments() {
        return this.arguments;
    }

    @Override
    public String getName() {
        return "UpdateObservation";
    }

    @Override
    public void initAction(String[] tokenized_command) throws IllegalArgumentException {
        Predicate registeredPredicate = null;
        for (int i = 1; i < tokenized_command.length; i++) {
            if (i == 1){
                // Predicate Field: Ensure predicate is registered in data store
                registeredPredicate = resolvePredicate(tokenized_command[i]);
                predicateName = registeredPredicate.getName();
                if (tokenized_command.length < registeredPredicate.getArity() + 3) {
                    throw new IllegalArgumentException("Not enough arguments provided for updating Predicate: " +
                            tokenized_command[i] + " With arity: " + registeredPredicate.getArity());
                }
                arguments = new Constant[registeredPredicate.getArity()];
            } else if (i <= (1 + registeredPredicate.getArity())) {
                // Argument Field:
                // Resolve Arguments
                ConstantType type = registeredPredicate.getArgumentType(i - 2);
                arguments[i - 2] = resolveConstant(tokenized_command[i], type);
            } else if (i == (2 + registeredPredicate.getArity())) {
                // Value Field: Ensure value is valid
                newValue = resolveValue(tokenized_command[i]);
            } else {
                throw new IllegalArgumentException("Too many arguments provided for Predicate: " +
                        tokenized_command[i] + " With arity: " + registeredPredicate.getArity());
            }
        }
    }
}
