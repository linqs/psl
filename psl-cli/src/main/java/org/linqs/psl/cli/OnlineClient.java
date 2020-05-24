package org.linqs.psl.cli;

import org.linqs.psl.application.inference.online.OnlineInference;
import org.linqs.psl.application.inference.online.actions.OnlineAction;
import org.linqs.psl.config.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

import static org.linqs.psl.cli.ClientCommandLoader.parseClientCommand;

public class OnlineClient {
    private String hostName;
    private int portNumber;
    private static final Logger log = LoggerFactory.getLogger(OnlineClient.class);


    public OnlineClient(){
        this.hostName = Options.ONLINE_HOST_NAME.getString();
        this.portNumber = Options.ONLINE_PORT_NUMBER.getInt();
    }

    public void run() throws IOException {
        Socket server = new Socket(hostName, portNumber);
        OnlineAction newAction;
        ObjectOutputStream out = new ObjectOutputStream(server.getOutputStream());
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

        String userInput;
        while (!(userInput = stdIn.readLine()).equals("Exit")) {
            try {
                newAction = parseClientCommand(userInput);
                log.trace("Action Initialized: " + newAction.getName());
                log.trace("Sending Action");
                out.writeObject(newAction);
                log.trace("Action Sent");
            } catch (RuntimeException e) {
                log.info("Error parsing command: " + userInput);
                log.info(e.toString());
                log.info(e.getMessage());
                log.info(Arrays.toString(e.getStackTrace()));
            } catch (NotSerializableException e) {
                log.info("Error parsing command: " + userInput);
                log.info(e.toString());
                log.info(e.getMessage());
                log.info(Arrays.toString(e.getStackTrace()));
            }
        }

        // Also closes output stream
        server.close();
        stdIn.close();
    }
}
