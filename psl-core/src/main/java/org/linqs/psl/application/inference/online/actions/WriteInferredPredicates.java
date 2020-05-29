package org.linqs.psl.application.inference.online.actions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteInferredPredicates extends OnlineAction{

    private String outputDirectoryPath;
    private static final Logger log = LoggerFactory.getLogger(WriteInferredPredicates.class);

    public WriteInferredPredicates() {
        outputDirectoryPath = null;
    }

    @Override
    public String getName() {
        return "WriteInferredPredicates";
    }

    public String getOutputDirectoryPath(){
        return outputDirectoryPath;
    }

    @Override
    public void initAction(String[] tokenized_command) throws IllegalArgumentException {
        // Format: WriteInferredPredicates outputDirectoryPath(optional)
        for (int i = 1; i < tokenized_command.length; i++) {
            if (i == 1) {
                // outputDirectoryPath Field:
                outputDirectoryPath = tokenized_command[i];
            } else {
                throw new IllegalArgumentException("Too many arguments provided for Action: WriteInferredPredicates");
            }
        }
    }
}
