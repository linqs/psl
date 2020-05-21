package org.linqs.psl.server;

import org.linqs.psl.application.inference.mpe.online.actions.OnlineAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;


public class OnlineServer {
    private static final Logger log = LoggerFactory.getLogger(OnlineServer.class);

    private ArrayList<OnlineAction> actionQueue;

    private int[] PORTS = {8888, 8889, 8890, 8891};
    private ServerSocket server;
    private Socket client;
    private PrintWriter writer;
    private BufferedReader reader;

    public OnlineServer() throws IOException {
        // Startup server listening on a port from the list of ports
        for (int port : PORTS) {
            try {
                server = new ServerSocket(port);
            } catch (IOException ex) {
                // try next port
            }
        }

        if (server == null) {
            throw new IOException("no free port found from list of ports " + Arrays.toString(PORTS));
        }

        actionQueue = new ArrayList<>();
    }

    public void start() throws IOException {

        client = server.accept();

        writer = new PrintWriter(client.getOutputStream());
        reader = new BufferedReader(new InputStreamReader(client.getInputStream()));

        queueActions();
    }

    public synchronized OnlineAction dequeNextAction() {
        return checkNextAction() ? actionQueue.remove(actionQueue.size() - 1) : null;
    }

    public synchronized boolean checkNextAction() {
        return actionQueue.size() > 0;
    }

    private synchronized void addToActionQueue(OnlineAction newAction){
        actionQueue.add(newAction);
    }

    public OnlineAction parseClientCommand(String clientCommand){
        //TODO: (Charles)
        return null;
    }

    private void queueActions() {
        String line;
        OnlineAction newOnlineAction;
        while (client.isConnected()) {
            try {
                line = reader.readLine();

                newOnlineAction = parseClientCommand(line);
                if (newOnlineAction != null) {
                    addToActionQueue(newOnlineAction);
                }
            } catch (IOException e) {
                log.info("Error reading line from client");
                log.info(e.getMessage());
            }
        }
    }
}
