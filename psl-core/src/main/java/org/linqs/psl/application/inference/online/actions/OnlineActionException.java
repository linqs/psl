package org.linqs.psl.application.inference.online.actions;

public class OnlineActionException extends Exception {
    OnlineActionException(String s) {
        super(s);
    }

    OnlineActionException(String s, Exception ex) {
        super(s, ex);
    }
}
