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
package org.linqs.psl.model.predicate;

import org.json.JSONObject;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * A predicate that is backed by some deep model.
 */
public class DeepPredicate extends StandardPredicate {
    private static final Logger log = Logger.getLogger(DeepPredicate.class);

    private static final String DELIM = "\t";

    private static final String CONFIG_MODEL_PATH = "model-path";
    private static final String CONFIG_ENTITY_DATA_MAP_PATH = "entity-data-map-path";
    private static final String CONFIG_ENTITY_ARGUMENT_INDEXES = "entity-argument-indexes";
    private static final String CONFIG_CLASS_SIZE = "class-size";

    private static final long SERVER_SLEEP_TIME_MS = (long)(0.5 * 1000);
    private static int startingPort = -1;
    private static Map<Integer, DeepPredicate> usedPorts = null;

    private Map<String, String> options;
    private String entityDataMapPath;
    private int[] entityArgumentIndexes;
    private int classSize;
    private int dataSize;

    private int[] atomIndexes;
    private int[] dataIndexes;
    private float[] gradients;

    private boolean initComplete;

    private int port;
    private String pythonModule;
    private String sharedMemoryPath;
    private Process pythonServerProcess;
    private RandomAccessFile sharedFile;
    private MappedByteBuffer sharedBuffer;
    private Socket socket;
    private BufferedReader socketInput;
    private PrintWriter socketOutput;
    private boolean serverOpen;

    protected DeepPredicate(String name, ConstantType[] types) {
        super(name, types);

        options = null;
        entityDataMapPath = null;
        entityArgumentIndexes = null;
        classSize = -1;
        dataSize = 0;

        atomIndexes = null;
        dataIndexes = null;
        gradients = null;

        initComplete = false;

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
     * Initialize a deep model. If any relative paths are supplied in the config, |relativeDir| will resolve them.
     */
    public void initDeepModel(Map<String, String> config, String relativeDir) {
        if (pythonServerProcess != null) {
            throw new RuntimeException("Cannot load a DeepPredicate that has already been loaded.");
        }

        String configModelPath = FileUtils.makePath(relativeDir, config.get(CONFIG_MODEL_PATH));
        if (configModelPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a model path (\"%s\") specified in predicate config.",
                    CONFIG_MODEL_PATH));
        }

        String configEntityDataMapPath = FileUtils.makePath(relativeDir, config.get(CONFIG_ENTITY_DATA_MAP_PATH));
        if (configEntityDataMapPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have an entity to data map path (\"%s\") specified in predicate config.",
                    CONFIG_ENTITY_DATA_MAP_PATH));
        }

        String configEntityArgumentIndexes = config.get(CONFIG_ENTITY_ARGUMENT_INDEXES);
        if (configEntityArgumentIndexes == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have entity argument indexes (\"%s\") specified in predicate config.",
                    CONFIG_ENTITY_ARGUMENT_INDEXES));
        }

        String configClassSize = config.get(CONFIG_CLASS_SIZE);
        if (configClassSize == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a class size (\"%s\") specified in predicate config.",
                    CONFIG_CLASS_SIZE));
        }

        entityDataMapPath = configEntityDataMapPath;
        entityArgumentIndexes = StringUtils.splitInt(configEntityArgumentIndexes, ",");
        classSize = Integer.parseInt(configClassSize);
        options = config;

        // Open data file and count how many data points there are.
        try (BufferedReader reader = FileUtils.getBufferedReader(entityDataMapPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                dataSize++;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse entity data map file: " + entityDataMapPath, ex);
        }

        // Do our best to make sure close() gets called.
        final DeepPredicate finalThis = this;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                finalThis.close();
            }
        });

        // Compute exactly how much memory we will need ahead of time.
        // The most memory we will need is on a full fit():
        // = 2 * (sizeof(int) + (num_entities * num_labels * sizeof(float)))
        int bufferLength = 2 * dataSize * classSize * Float.SIZE;

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

        atomIndexes = new int[dataSize * classSize];
        dataIndexes = new int[dataSize];

        JSONObject message = new JSONObject();
        message.put("task", "init");
        message.put("shared_memory_path", sharedMemoryPath);
        message.put("options", options);

        sendSocketMessage(message);
    }

    /**
     * Fit the model using values set through setLabel().
     */
    public void fitDeepModel(AtomStore atomStore, float[] newGradients) {
        finalizeInit(atomStore);

        log.trace("Fitting {}.", this);

        sharedBuffer.clear();

        for (int index = 0; index < atomIndexes.length; index++) {
            gradients[dataIndexes[index]] = newGradients[atomIndexes[dataIndexes[index / classSize]]];
        }

        writeEntityData(gradients);

        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "fit");
        message.put("options", options);

        JSONObject response = sendSocketMessage(message);

        String resultString = getResultString(response);
        log.debug("Fit Result: {}", resultString);
    }

    /**
     * Get deep model predictions and update atom values.
     */
    public void predictDeepModel(AtomStore atomStore) {
        finalizeInit(atomStore);

        log.trace("Fitting {}.", this);

        sharedBuffer.clear();
        writeIndexData(dataIndexes.length);
        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "predict");
        message.put("options", options);

        JSONObject response = sendSocketMessage(message);

        // Read the predictions off the buffer.
        sharedBuffer.clear();

        int count = sharedBuffer.getInt();
        if (count != atomIndexes.length) {
            throw new RuntimeException(String.format(
                    "External model did not make the desired number of predictions, got %d, expected %d.",
                    count, atomIndexes.length));
        }

        float[] atomValues = atomStore.getAtomValues();
        float deepPrediction = 0.0f;
        int atomIndex = 0;

        for(int index = 0; index < atomIndexes.length; index++) {
            deepPrediction = sharedBuffer.getFloat();
            atomIndex = atomIndexes[dataIndexes[index / classSize]];

            atomValues[atomIndex] = deepPrediction;
            ((RandomVariableAtom)atomStore.getAtom(atomIndex)).setValue(deepPrediction);
        }
    }

    /**
     * Evaluate using deep model.
     */
    public void evalDeepModel(AtomStore atomStore) {
        finalizeInit(atomStore);

        log.trace("Fitting {}.", this);

        sharedBuffer.clear();
        writeIndexData(dataIndexes.length);
        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "eval");
        message.put("options", options);

        JSONObject response = sendSocketMessage(message);

        String resultString = getResultString(response);
        log.debug("Deep Eval Result for {} : {}", this, resultString);
    }

    /**
     * Save the deep model.
     */
    public void saveDeepModel() {
        if (!initComplete) {
            throw new RuntimeException(String.format("Deep predicate not initialized, run init before save."));
        }

        JSONObject message = new JSONObject();
        message.put("task", "save");
        message.put("options", options);

        JSONObject response = sendSocketMessage(message);
    }

    @Override
    public synchronized void close() {
        super.close();

        if (options != null) {
            options.clear();
            options = null;
        }
        entityDataMapPath = null;
        entityArgumentIndexes = null;
        classSize = -1;
        dataSize = 0;

        atomIndexes = null;
        dataIndexes = null;
        gradients = null;

        initComplete = false;

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
     * Load atom and data indexes using the entity data map file, which maps entity IDs to data vectors.
     */
    private void finalizeInit(AtomStore atomStore) {
        if (initComplete) {
            return;
        }

        // Map data entities from file to atoms and indexes.
        Constant[] arguments = new Constant[entityArgumentIndexes.length + 1];
        ConstantType type;
        int dataIndex = 0;
        int width = -1;

        try (BufferedReader reader = FileUtils.getBufferedReader(entityDataMapPath)) {
            String line = null;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(DELIM);

                if (width == -1) {
                    width = parts.length;

                    if (width - entityArgumentIndexes.length <= 0) {
                        throw new RuntimeException(String.format(
                                "Line too short (%d). Expected at least %d values, found %d.",
                                lineNumber, entityArgumentIndexes.length + 1, width));
                    }
                } else if (parts.length != width) {
                    throw new RuntimeException(String.format(
                            "Incorrectly sized line (%d). Expected: %d values, found: %d.",
                            lineNumber, width, parts.length));
                }

                // Get constant types for this entity.
                for (int index = 0; index < arguments.length - 1; index++) {
                    type = this.getArgumentType(index);
                    arguments[index] = ConstantType.getConstant(parts[index], type);
                }

                // Add atom index and data index for each class.
                type = this.getArgumentType(arguments.length - 1);
                dataIndexes[dataIndex] = dataIndex;

                for (int index = 0; index < classSize; index++) {
                    arguments[arguments.length - 1] =  ConstantType.getConstant(String.valueOf(index), type);
                    atomIndexes[classSize * dataIndex + index] = atomStore.getAtomIndex(this, arguments);
                }

                dataIndex += 1;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse entity data map file: " + entityDataMapPath, ex);
        }

        gradients = new float[atomIndexes.length];
        initComplete = true;
    }

    private String getResultString(JSONObject response) {
        JSONObject result = response.optJSONObject("result");
        if (result == null) {
            return "<No Result Provided>";
        }

        return result.toString();
    }

    private void writeEntityData(float[] data) {
        // Write out the number of values and indexes.
        writeIndexData(data.length / classSize);

        // Write out the actual data.
        for (int i = 0; i < data.length; i++) {
            sharedBuffer.putFloat(data[i]);
        }
    }

    private void writeIndexData(int dataSize) {
        // Write out the number of values.
        sharedBuffer.putInt(dataSize);

        // Write out the indexes.
        for (int i = 0; i < dataSize; i++) {
            sharedBuffer.putInt(dataIndexes[i]);
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
        usedPorts = new HashMap<Integer, DeepPredicate>();
    }

    private static synchronized int getOpenPort(DeepPredicate model) {
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
        for (Map.Entry<Integer, DeepPredicate> entry : usedPorts.entrySet()) {
            entry.getValue().close();
        }

        usedPorts.clear();
    }

    /**
     * Get an existing standard predicate (or null if none with this name exists).
     * If the predicate exists, but is not a DeepPredicate, an exception will be thrown.
     */
    public static DeepPredicate get(String name) {
        StandardPredicate predicate = StandardPredicate.get(name);
        if (predicate == null) {
            return null;
        }

        if (!(predicate instanceof DeepPredicate)) {
            throw new ClassCastException("Predicate (" + name + ") is not a ModelPredicate.");
        }

        return (DeepPredicate)predicate;
    }

    /**
     * Get a predicate if one already exists, otherwise create a new one.
     */
    public static DeepPredicate get(String name, ConstantType... types) {
        DeepPredicate predicate = get(name);
        if (predicate == null) {
            return new DeepPredicate(name, types);
        }

        StandardPredicate.validateTypes(predicate, types);

        return predicate;
    }
}
