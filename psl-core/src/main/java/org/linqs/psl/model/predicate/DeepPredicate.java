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
 *
 * Before a DeepPredicate can be used, loadModel() must be called.
 */
public class DeepPredicate extends StandardPredicate {
    private static final Logger log = Logger.getLogger(DeepPredicate.class);

    private boolean initComplete;
    private ArrayList<Integer> atomIndexes;
    private ArrayList<Integer> featureIndexes;

    public static final String DELIM = "\t";

    public static final String CONFIG_MODEL_PATH = "model-path";
    public static final String CONFIG_ENTITY_DATA_MAP_PATH = "entity-data-map-path";
    public static final String CONFIG_CLASS_SIZE = "class-size";
    public static final String CONFIG_DATA_SIZE = "data-size";
    public static final String CONFIG_ENTITY_ARGUMENT_INDEXES = "entity-argument-indexes";

    protected int[] entityArgumentIndexes;
    protected float[] gradients;

    private int featuresSize;
    private int classSize;
    private int dataSize;
    private String entityDataMapPath;

    public static final long SERVER_SLEEP_TIME_MS = (long)(0.5 * 1000);

    private static int startingPort = -1;
    private static Map<Integer, DeepPredicate> usedPorts = null;

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

        initComplete = false;
        atomIndexes = new ArrayList<>();
        featureIndexes = new ArrayList<>();

        entityArgumentIndexes = null;
        featuresSize = -1;
        classSize = -1;
        dataSize = -1;
        entityDataMapPath = null;

        gradients = null;

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
     * Load atom and feature indexes using the entity feature map file, which maps entity IDs to feature vectors.
     */
    public void init(AtomStore atomStore) {
        loadIndexes(atomStore);

        gradients = new float[atomIndexes.size()];
        initComplete = true;
    }

    public int getGradientSize() {
        return gradients.length;
    }

    private void loadIndexes(AtomStore atomStore) {
        Constant[] arguments = new Constant[entityArgumentIndexes.length + 1];
        ConstantType type;
        int featureIndex;

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
                    featuresSize = width - entityArgumentIndexes.length;

                    if (featuresSize <= 0) {
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

                // Add atom index and feature index for each class.
                type = this.getArgumentType(arguments.length - 1);
                featureIndex = featureIndexes.size() / classSize;

                for (int index = 0; index < classSize; index++) {
                    arguments[arguments.length - 1] =  ConstantType.getConstant(String.valueOf(index), type);
                    atomIndexes.add(atomStore.getAtomIndex(this, arguments));
                    featureIndexes.add(featureIndex);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse features file: " + entityDataMapPath, ex);
        }
    }

    /**
     * Load a deep model.
     * If any relative paths are supplied in the config, |relativeDir| can be used to resolve them.
     */
    public void loadDeepModel(Map<String, String> config, String relativeDir) {
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

        String configClassSize = config.get(CONFIG_CLASS_SIZE);
        if (configClassSize == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a class size (\"%s\") specified in predicate config.",
                    CONFIG_CLASS_SIZE));
        }

        String configDataSize = config.get(CONFIG_DATA_SIZE);
        if (configDataSize == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a data size (\"%s\") specified in predicate config.",
                    CONFIG_DATA_SIZE));
        }

        String configEntityArgumentIndexes = config.get(CONFIG_ENTITY_ARGUMENT_INDEXES);
        if (configEntityArgumentIndexes == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have entity argument indexes (\"%s\") specified in predicate config.",
                    CONFIG_ENTITY_ARGUMENT_INDEXES));
        }

        entityDataMapPath = configEntityDataMapPath;
        classSize = Integer.parseInt(configClassSize);
        dataSize = Integer.parseInt(configDataSize);
        entityArgumentIndexes = StringUtils.splitInt(configEntityArgumentIndexes, ",");

        loadModel(configModelPath, configEntityDataMapPath, config);
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

        JSONObject response = sendSocketMessage(message);
    }

    /**
     * Get deep model predictions and update atom values.
     */
    public void predictDeepModel(AtomStore atomStore) {
        if (!initComplete) {
            throw new RuntimeException(String.format("Deep predicate not initialized, run init before predict."));
        }

        JSONObject message = new JSONObject();
        message.put("task", "predict");

        JSONObject response = sendSocketMessage(message);

        // Read the predictions off the buffer.
        sharedBuffer.clear();

        int count = sharedBuffer.getInt();
        if (count != atomIndexes.size()) {
            throw new RuntimeException(String.format(
                    "External model did not make the desired number of predictions, got %d, expected %d.",
                    count, atomIndexes.size()));
        }

        float[] atomValues = atomStore.getAtomValues();
        for(int index = 0; index < atomIndexes.size(); index++) {
            atomValues[atomIndexes.get(featureIndexes.get(index))] = sharedBuffer.getFloat();
            ((RandomVariableAtom)atomStore.getAtom(atomIndexes.get(index))).setValue(sharedBuffer.getFloat());
        }
    }

    /**
     * Fit the model using values set through setLabel().
     */
    public void fitDeepModel(AtomStore atomStore, Map<RandomVariableAtom, Float> gradientAtomMap) {
        if (!initComplete) {
            throw new RuntimeException(String.format("Deep predicate not initialized, run init before fit."));
        }

        log.trace("Fitting {}.", this);

        sharedBuffer.clear();

        for (int index = 0; index < atomIndexes.size(); index++) {
            gradients[featureIndexes.get(index)] = gradientAtomMap.get(atomStore.getAtom(atomIndexes.get(featureIndexes.get(index))));
        }

        writeEntityData(gradients);

        sharedBuffer.force();

        String resultString = null;

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("epochs", 1);
        options.put("batch_size", batchSize);
        options.put("learning_rate", learningRate);
        options.put("has_gradients", true);
        options.put("alpha", alpha);

        JSONObject message = new JSONObject();
        message.put("task", "fit");
        message.put("options", options);

        JSONObject response = sendSocketMessage(message);

        resultString = getResultString(response);
        log.debug("Result: {}", resultString);
    }

    @Override
    public synchronized void close() {
        super.close();

        if (atomIndexes != null) {
            atomIndexes.clear();
            atomIndexes = null;
        }

        if (featureIndexes != null) {
            featureIndexes.clear();
            featureIndexes = null;
        }

        entityArgumentIndexes = null;
        featuresSize = -1;

        gradients = null;

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

    private void loadModel(String modelDef, String featuresPath, Map<String, String> config) {
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

        JSONObject message = new JSONObject();
        message.put("task", "init");
        message.put("shared_memory_path", sharedMemoryPath);
        message.put("options", config);

        sendSocketMessage(message);
    }

    private String getResultString(JSONObject response) {
        JSONObject result = response.optJSONObject("result");
        if (result == null) {
            return "<No Result Provided>";
        }

        return result.toString();
    }

    private void writeEntityData(float[] data) {
        // Write out the number of values.
        sharedBuffer.putInt(data.length / classSize);

        // Write out the indexes.
        for (int i = 0; i < data.length / classSize; i++) {
            sharedBuffer.putInt(featureIndexes.get(i));
        }

        // Write out the actual data.
        for (int i = 0; i < data.length; i++) {
            sharedBuffer.putFloat(data[i]);
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
