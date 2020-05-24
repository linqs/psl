package org.linqs.psl.application.inference.online;

import org.linqs.psl.config.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Online server class
 * */
public class OnlineServer<T> extends Thread{
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private int port;
    private ServerSocket server;
    private LinkedBlockingQueue<T> queue;


    public OnlineServer() throws IOException {

        // Startup server listening on a port from the list of ports
        port = Options.ONLINE_PORT_NUMBER.getInt();

        try {
            server = new ServerSocket(port);
        } catch (IOException ex) {
            log.debug(ex.getMessage());
        }

        if (server == null) {
            throw new IOException("Port not free " + port);
        }

        log.info("Started Server at port: " + port);

        this.queue = new LinkedBlockingQueue<T>();
    }


    public void run() {
        try {
            int counter = 0;
            while(!interrupted()) {
                Socket client = server.accept();
                log.info("Client Connected");
                counter++;
                ServerClientThread sct = new ServerClientThread(client, counter); //send  the request to a separate thread
                sct.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                // TODO: (Charles) Make sure ServerClientThreads are cleaned up here as well
                server.close();
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        }
    }

    public void enqueue(T newObject) {
        queue.offer(newObject);
    }

    public synchronized T dequeClientInput() throws InterruptedException {
        return queue.take();
    }

    public void closeServer() {
        interrupt();
    }

    private class ServerClientThread extends Thread {
        Socket client;
        int clientNo;
        private ObjectInputStream inStream;

        ServerClientThread(Socket inSocket,int counter){
            client = inSocket;
            clientNo = counter;
            try {
                inStream = new ObjectInputStream(client.getInputStream());
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        }

        public void run(){
            T newCommand;
            while (client.isConnected()) {
                try {
                    newCommand = (T) inStream.readObject();
                    log.info("Recieved new action from client: ");
                    enqueue(newCommand);
                } catch (IOException e) {
                    log.debug("Error reading object from client");
                    log.debug(e.getMessage());
                } catch (ClassNotFoundException e) {
                    log.debug("Error casting object serialized by client");
                    log.debug(e.getMessage());
                }
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

