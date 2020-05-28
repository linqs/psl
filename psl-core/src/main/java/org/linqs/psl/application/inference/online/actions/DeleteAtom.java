package org.linqs.psl.application.inference.online.actions;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAtom extends OnlineAction{

    private String predicateName;
    private String partitionName;
    private Constant[] arguments;
    private float newValue;
    private static final Logger log = LoggerFactory.getLogger(UpdateObservation.class);

    public DeleteAtom() {
        this.newValue = (float)0.5;
    }

    public String getPredicateName() {
        return this.predicateName;
    }

    public String getPartitionName() {
        return this.partitionName;
    }

    public float getValue() {
        return this.newValue;
    }

    public Constant[] getArguments() {
        return this.arguments;
    }

    @Override
    public String getName() {
        return "DeleteAtom";
    }

    @Override
    public void initAction(String[] tokenized_command) throws IllegalArgumentException {
        // Format: AddAtom PartitionName PredicateName Arguments Value(Optional)
        Predicate registeredPredicate = null;
        int argumentLength = 0;
        for (int i = 1; i < tokenized_command.length; i++) {
            if (i == 1) {
                argumentLength = 3;
            } else if (i == 2){
                // Predicate Field: Ensure predicate is registered in data store
                registeredPredicate = resolvePredicate(tokenized_command[i]);
                predicateName = registeredPredicate.getName();
                if (tokenized_command.length < registeredPredicate.getArity() + argumentLength) {
                    throw new IllegalArgumentException("Not enough arguments provided for updating Predicate: " +
                            tokenized_command[i] + " With arity: " + registeredPredicate.getArity());
                }
                arguments = new Constant[registeredPredicate.getArity()];
            } else if (i <= (2 + registeredPredicate.getArity())) {
                // Argument Field:
                // Resolve Arguments
                ConstantType type = registeredPredicate.getArgumentType(i - 3);
                arguments[i - 3] = resolveConstant(tokenized_command[i], type);
            } else if (i == (3 + registeredPredicate.getArity())) {
                // Value Field: Ensure value is valid
                // Block only reached if value provided
                newValue = resolveValue(tokenized_command[i]);
            } else {
                throw new IllegalArgumentException("Too many arguments provided for Predicate: " +
                        tokenized_command[i] + " With arity: " + registeredPredicate.getArity());
            }
        }
    }
}
