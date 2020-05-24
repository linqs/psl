package org.linqs.psl.application.inference.online.actions;

public class Close extends OnlineAction {

    public Close() {
    }

    @Override
    public void initAction(String[] tokenized_command) throws IllegalArgumentException {
        // Pass
    }

    @Override
    public String getName() {
        return "Close";
    }
}
