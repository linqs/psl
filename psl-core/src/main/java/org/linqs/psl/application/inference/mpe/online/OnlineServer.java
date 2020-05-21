package org.linqs.psl.application.inference.mpe.online;

import org.linqs.psl.application.inference.mpe.online.actions.Continue;
import org.linqs.psl.application.inference.mpe.online.actions.OnlineAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Online server class
 * */
public class OnlineServer extends Thread{
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private int[] PORTS = {8888, 8889, 8890, 8891};
    private ServerSocket server;
    private LinkedBlockingQueue<OnlineAction> actionQueue;


    public OnlineServer() throws IOException {
        // Startup server listening on a port from the list of ports
        for (int port : PORTS) {
            try {
                server = new ServerSocket(port);
            } catch (IOException ex) {
                log.debug(ex.getMessage());
            }
        }

        if (server == null) {
            throw new IOException("no free port found from list of ports " + Arrays.toString(PORTS));
        }

        actionQueue = new LinkedBlockingQueue<>();
    }


    public void run() {
        try {
            int counter = 0;
            while(!interrupted()) {
                Socket client = server.accept();
                counter ++;
                ServerClientThread sct = new ServerClientThread(client, counter); //send  the request to a separate thread
                sct.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                server.close();
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        }
    }

    private synchronized void addToActionQueue(OnlineAction newAction) {
        actionQueue.offer(newAction);
    }

    public synchronized OnlineAction dequeNextAction() throws InterruptedException {
        return actionQueue.take();
    }

    private OnlineAction parseClientCommand(String clientCommand) {
        //TODO: (Charles)
        return null;
    }

    public void closeServer() {
        interrupt();
    }

    private class ServerClientThread extends Thread {
        Socket client;
        int clientNo;
        private BufferedReader reader;

        ServerClientThread(Socket inSocket,int counter){
            client = inSocket;
            clientNo=counter;
            try {
                reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        }

        public void run(){
            BufferedReader inStream = null;
            try {
                inStream = new BufferedReader(new InputStreamReader(client.getInputStream()));
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
            String line;
            OnlineAction newOnlineAction;
            while (client.isConnected()) {
                try {
                    line = reader.readLine();
                } catch (IOException e) {
                    log.debug("Error reading line from client");
                    log.debug(e.getMessage());
                    continue;
                }

                newOnlineAction = parseClientCommand(line);
                if (newOnlineAction == null) {
                    newOnlineAction = new Continue(null);
                }

                addToActionQueue(newOnlineAction);
            }
            try {
                inStream.close();
                client.close();
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        }
    }
}

