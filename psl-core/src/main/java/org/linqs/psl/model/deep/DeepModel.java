/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.model.deep;

import org.json.JSONObject;
import org.linqs.psl.config.Config;
import org.linqs.psl.config.Options;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;


/**
 * Abstract class for deep models.
 * Contains socket communication with python server, and shared memory.
 */
public abstract class DeepModel {
    private static final Logger log = Logger.getLogger(DeepModel.class);

    protected static final String CONFIG_MODEL_PATH = "model-path";
    protected static final String CONFIG_RELATIVE_DIR = "relative-dir";

    private static final long SERVER_SLEEP_TIME_MS = (long)(0.5 * 1000);
    private static int startingPort = -1;
    private static Map<Integer, DeepModel> usedPorts = null;

    protected Map<String, String> pythonOptions;

    private int port;
    private String pythonModule;
    private String sharedMemoryPath;
    private Process pythonServerProcess;
    private RandomAccessFile sharedFile;
    protected MappedByteBuffer sharedBuffer;
    private Socket socket;
    private BufferedReader socketInput;
    private PrintWriter socketOutput;
    private boolean serverOpen;

    protected DeepModel() {
        pythonOptions = new HashMap<String, String>();

        initStatic();

        port = getOpenPort(this);
        pythonModule = Options.PREDICATE_DEEP_PYTHON_WRAPPER_MODULE.getString();
        sharedMemoryPath = Options.PREDICATE_DEEP_SHARED_MEMORY_PATH.getString();
        pythonServerProcess = null;
        sharedFile = null;
        sharedBuffer = null;
        socket = null;
        socketInput = null;
        socketOutput = null;
        serverOpen = false;
    }

    /**
     * Abstract class for initializing the model.
     * Return the length of the shared buffer.
     */
    public abstract int initSpecific();

    /**
     * Abstract class for writing the fit data to the shared buffer.
     */
    public abstract void writeFitData();

    /**
     * Abstract class for writing the predict data to the shared buffer.
     */
    public abstract void writePredictData();

    /**
     * Abstract class for reading the predict data from the shared buffer.
     */
    public abstract void readPredictData();

    /**
     * Abstract class for writing the eval data to the shared buffer.
     */
    public abstract void writeEvalData();

    public void init(String application){
        log.debug("Init {}.", this);

        pythonOptions.put(CONFIG_RELATIVE_DIR, Config.getString("runtime.relativebasepath", null));

        int bufferLength = initSpecific();

        if (pythonOptions.get(CONFIG_MODEL_PATH) == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a model path (\"%s\") specified in predicate config.",
                    CONFIG_MODEL_PATH));
        }

        if (pythonServerProcess == null) {
            log.debug("Deep server not found (\"{}\") starting server.", this);
            initServer(bufferLength);
        }

        JSONObject message = new JSONObject();
        message.put("task", "init");
        message.put("shared_memory_path", sharedMemoryPath);
        message.put("application", application);
        message.put("options", pythonOptions);

        JSONObject response = sendSocketMessage(message);

        String resultString = getResultString(response);
        log.debug("Deep init result for {} : {}", this, resultString);
    }

    public void fitDeepModel() {
        log.debug("Fitting {}.", this);

        sharedBuffer.clear();
        writeFitData();
        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "fit");
        message.put("options", pythonOptions);

        JSONObject response = sendSocketMessage(message);

        String resultString = getResultString(response);
        log.debug("Deep fit result for {} : {}", this, resultString);
    }

    public void predictDeepModel() {
        log.debug("Predicting {}.", this);

        sharedBuffer.clear();
        writePredictData();
        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "predict");
        message.put("options", pythonOptions);

        JSONObject response = sendSocketMessage(message);

        sharedBuffer.clear();
        readPredictData();

        String resultString = getResultString(response);
        log.debug("Deep predict result for {} : {}", this, resultString);
    }

    public void evalDeepModel() {
        log.debug("Evaluating {}.", this);

        sharedBuffer.clear();
        writeEvalData();
        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "eval");
        message.put("options", pythonOptions);

        JSONObject response = sendSocketMessage(message);

        String resultString = getResultString(response);
        log.debug("Deep eval result for {} : {}", this, resultString);
    }

    public void saveDeepModel() {
        log.debug("Saving {}.", this);

        JSONObject message = new JSONObject();
        message.put("task", "save");
        message.put("options", pythonOptions);

        JSONObject response = sendSocketMessage(message);

        String resultString = getResultString(response);
        log.debug("Deep save result for {} : {}", this, resultString);
    }

    public synchronized void close() {
        log.debug("Closing {}.", this);

        if (pythonOptions != null) {
            pythonOptions.clear();
            pythonOptions = null;
        }

        closeServer();
        log.debug("Closed {}.", this);
    }

    private String getResultString(JSONObject response) {
        JSONObject result = response.optJSONObject("result");
        if (result == null) {
            return "<No Result Provided>";
        }

        return result.toString();
    }

    private void initServer(int bufferLength) {
        // Do our best to make sure close() gets called.
        final DeepModel finalThis = this;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                finalThis.close();
            }
        });

        try {
            sharedFile = new RandomAccessFile(sharedMemoryPath, "rw");
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Could not open random access file: " + sharedMemoryPath, ex);
        }

        try {
            sharedBuffer = sharedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, bufferLength);
            sharedBuffer.clear();

            ProcessBuilder builder = new ProcessBuilder("python3", "-m", pythonModule, "" + port);
            builder.inheritIO();
            pythonServerProcess = builder.start();

            sleepForServer();
            serverOpen = true;

            // TODO: Set encoding?
            socket = new Socket("127.0.0.1", port);
            socketInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            socketOutput = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected synchronized void closeServer() {
        if (socketOutput != null) {
            JSONObject message = new JSONObject();
            message.put("task", "close");
            sendSocketMessage(message);
            serverOpen = false;
            sleepForServer();
            freePort(port);

            socketOutput.close();
            socketOutput = null;
        }

        if (socketInput != null) {
            try {
                socketInput.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            socketInput = null;
        }

        if (socket != null) {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }

            socket = null;
        }

        if (sharedBuffer != null) {
            sharedBuffer = null;
        }

        if (sharedFile != null) {
            try {
                sharedFile.close();
                FileUtils.delete(sharedMemoryPath);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to clean up shared file: " + sharedMemoryPath, ex);
            }

            sharedFile = null;
        }

        if (pythonServerProcess != null) {
            if (pythonServerProcess.isAlive()) {
                pythonServerProcess.destroyForcibly();
            }

            pythonServerProcess = null;
        }
    }

    /**
     * Sleep for a short duration to give the Python server time.
     */
    private void sleepForServer() {
        try {
            Thread.sleep(SERVER_SLEEP_TIME_MS);
        } catch (InterruptedException ex) {
            // Do nothing.
        }
    }

    private JSONObject sendSocketMessage(JSONObject message) {
        if (!serverOpen) {
            // This should only happen when trying to close the server.
            return null;
        }

        String rawResponse = null;

        log.trace(String.format("Sending server message: '%s'.", message.toString()));

        try {
            socketOutput.println(message.toString());
            rawResponse = socketInput.readLine();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        log.trace(String.format("Received server message: '%s'.", rawResponse));

        JSONObject response = new JSONObject(rawResponse);

        String status = response.optString("status", "<UNKNOWN>");
        if (!status.equals("success")) {
            serverOpen = false;
            sleepForServer();

            String failureMessage = response.optString("message", "<no message provided>");
            throw new RuntimeException(String.format("Server sent a failure status (%s): '%s'.", status, failureMessage));
        }

        return response;
    }

    /**
     * Initialize the static portions of this class.
     */
    private static synchronized void initStatic() {
        if (startingPort != -1) {
            return;
        }

        startingPort = Options.PREDICATE_DEEP_PYTHON_PORT.getInt();
        usedPorts = new HashMap<Integer, DeepModel>();
    }

    private static synchronized int getOpenPort(DeepModel model) {
        int port = startingPort;

        while (usedPorts.containsKey(Integer.valueOf(port))) {
            port++;
        }

        usedPorts.put(Integer.valueOf(port), model);
        return port;
    }

    private static synchronized void freePort(int port) {
        usedPorts.remove(Integer.valueOf(port));
    }

    /**
     * Close all open models (according to the ports).
     * Mainly for testing.
     */
    public static synchronized void closeModels() {
        for (Map.Entry<Integer, DeepModel> entry : usedPorts.entrySet()) {
            entry.getValue().close();
        }

        usedPorts.clear();
    }
}
