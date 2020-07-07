package org.linqs.psl.application.inference.online.actions;

public class OnlineActionException extends Exception {
    protected OnlineActionException(String s) {
        super(s);
    }

    protected OnlineActionException(String s, Exception ex) {
        super(s, ex);
    }
}
