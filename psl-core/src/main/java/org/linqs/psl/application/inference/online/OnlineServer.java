package org.linqs.psl.application.inference.online;

import org.linqs.psl.config.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Online server class
 * */
public class OnlineServer<T> extends Thread{
    private static final Logger log = LoggerFactory.getLogger(OnlineInference.class);

    private boolean waiting;
    private int port;
    private int max_clients;
    private int connections;
    private InetAddress addr;
    private ServerSocket server;
    private LinkedBlockingQueue<T> queue;
    private Set<ServerClientThread> threads;


    public OnlineServer() throws IOException {
        waiting = false;

        // Startup server listening on a port from the list of ports
        port = Options.ONLINE_PORT_NUMBER.getInt();
        max_clients = Options.ONLINE_MAX_CLIENTS.getInt();
        addr = InetAddress.getByName(Options.ONLINE_HOST_NAME.getString());

        try {
            server = new ServerSocket(port, max_clients, addr);
        } catch (IOException ex) {
            log.debug(ex.getMessage());
        }

        if (server == null) {
            throw new IOException("Port not free " + port);
        }

        log.info("Started Server at port: " + server.getLocalPort() + " and IP: + " + server.getInetAddress());

        this.queue = new LinkedBlockingQueue<T>();
        this.threads = new HashSet<>();
    }

    private Socket connectClient() throws IOException {
        Socket client = server.accept();
        log.info("Client Connected");
        return client;
    }

    private void close() {
        try {
            server.close();
        } catch (IOException e) {
            log.debug(e.getMessage());
        } finally {
            log.trace("Interrupting Child Threads");
            for(ServerClientThread childThread : threads){
                childThread.close();
            }
        }
    }

    private void addThread(Socket client) {
        log.trace("Creating Thread: " + connections);
        ServerClientThread sct = new ServerClientThread(client);
        log.trace("Starting Thread: " + connections);
        sct.start();
        log.trace("Thread Started");
        threads.add(sct);
        connections++;
    }

    void removeThread(ServerClientThread sct) {
        log.trace("Client Disconnected. Removing Thread.");
        threads.remove(sct);
        connections--;
        if (waiting) {
            log.info("Waking Server up to accept new connections.");
            notify();
        }
    }

    public void run() {
        while(!isInterrupted()) {
            Socket client = null;
            try {
                if (connections < max_clients){
                    try {
                        client = connectClient();
                    } catch (IOException e) {
                        log.debug("Exception Connecting to Client");
                        log.debug(e.getMessage());
                        break;
                    }
                    addThread(client);
                } else {
                    log.info("Too Many Clients Connected to Server. Waiting for Openings");
                    wait();
                }
            } catch (InterruptedException e) {
                log.info("Server Interrupted");
                log.info(e.getMessage());
            }
        }
        log.info("Server Complete");
    }

    public void enqueue(T newObject) {
        queue.offer(newObject);
    }

    public synchronized T dequeClientInput() throws InterruptedException {
        return queue.take();
    }

    public void closeServer() {
        log.info("Closing Server");
        close();
        log.info("Server Closed");
    }

    private class ServerClientThread extends Thread {

        private Socket client;
        private ObjectInputStream inStream;

        ServerClientThread(Socket inSocket){
            client = inSocket;
            try {
                inStream = new ObjectInputStream(client.getInputStream());
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        }

        public void close(){
            interrupt();

            try {
                inStream.close();
                client.close();
            } catch (IOException e) {
                log.debug(e.getMessage());
            } finally {
                removeThread(this);
            }
        }

        public void run(){
            T newCommand;
            while (client.isConnected() && !isInterrupted()) {
                try {
                    newCommand = (T) inStream.readObject();
                    log.info("Received new action from client: ");
                    enqueue(newCommand);
                } catch (IOException e) {
                    log.debug("Error reading object from client");
                    log.debug(e.getMessage());
                } catch (ClassNotFoundException e) {
                    log.debug("Error casting object serialized by client");
                    log.debug(e.getMessage());
                } catch (NullPointerException e) {
                    log.debug("Error reading object");
                    log.debug(e.getMessage());
                    log.debug(Arrays.toString(e.getStackTrace()));
                }
            }

            log.info("Thread Completed");
        }
    }
}

