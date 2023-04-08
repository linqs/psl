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
import org.linqs.psl.config.Config;
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

    public static final String CONFIG_MODEL_PATH = "model-path";
    public static final String CONFIG_ENTITY_DATA_MAP_PATH = "entity-data-map-path";
    public static final String CONFIG_ENTITY_ARGUMENT_INDEXES = "entity-argument-indexes";
    public static final String CONFIG_CLASS_SIZE = "class-size";
    public static final String CONFIG_REALTIVE_DIR = "relative-dir";

    private static final long SERVER_SLEEP_TIME_MS = (long)(0.5 * 1000);
    private static int startingPort = -1;
    private static Map<Integer, DeepPredicate> usedPorts = null;

    private Map<String, String> pythonOptions;
    private int classSize;

    private int[] atomIndexes;
    private int[] dataIndexes;
    private float[] gradients;

    private boolean initDeepInference;
    private boolean initDeepWeightLearning;

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

        pythonOptions = new HashMap<String, String>();
        classSize = -1;

        atomIndexes = null;
        dataIndexes = null;
        gradients = null;

        initDeepInference = true;
        initDeepWeightLearning = true;

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
     * Initialize a deep model for inference.
     */
    public void initDeepModelInference(AtomStore atomStore){
        if (!initDeepInference) {
            return;
        }

        initDeepModel(atomStore);

        // Set up python model.
        JSONObject message = new JSONObject();
        message.put("task", "init");
        message.put("shared_memory_path", sharedMemoryPath);
        message.put("application", "inference");
        message.put("options", pythonOptions);

        sendSocketMessage(message);
        initDeepInference = false;
    }

    /**
     * Initialize a deep model for weight learning.
     */
    public void initDeepModelWeightLearning(AtomStore atomStore) {
        if (!initDeepWeightLearning) {
            return;
        }

        initDeepModel(atomStore);
        pythonOptions.put("application", "learning");

        // Set up python model.
        JSONObject message = new JSONObject();
        message.put("task", "init");
        message.put("shared_memory_path", sharedMemoryPath);
        message.put("application", "learning");
        message.put("options", pythonOptions);

        sendSocketMessage(message);
        initDeepWeightLearning = false;
    }

    /**
     * Fit the model using values set through setLabel().
     */
    public void fitDeepModel(AtomStore atomStore, float[] symbolicGradients) {
        checkModel();

        log.trace("Fitting {}.", this);

        sharedBuffer.clear();

        // Populate gradients.
        for (int index = 0; index < gradients.length; index++) {
            gradients[index] = symbolicGradients[atomIndexes[index]];
        }

        writeEntityData(gradients);

        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "fit");
        message.put("options", pythonOptions);

        JSONObject response = sendSocketMessage(message);

        String resultString = getResultString(response);
        log.debug("Fit Result: {}", resultString);
    }

    /**
     * Get deep model predictions and update atom values.
     */
    public void predictDeepModel(AtomStore atomStore) {
        checkModel();

        log.trace("Fitting {}.", this);

        sharedBuffer.clear();
        writeIndexData(dataIndexes.length);
        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "predict");
        message.put("options", pythonOptions);

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
            atomIndex = atomIndexes[dataIndexes[index / classSize] * classSize + index % classSize];

            atomValues[atomIndex] = deepPrediction;
            ((RandomVariableAtom)atomStore.getAtom(atomIndex)).setValue(deepPrediction);
        }
    }

    /**
     * Evaluate using deep model.
     */
    public void evalDeepModel(AtomStore atomStore) {
        checkModel();

        log.trace("Fitting {}.", this);

        sharedBuffer.clear();
        writeIndexData(dataIndexes.length);
        sharedBuffer.force();

        JSONObject message = new JSONObject();
        message.put("task", "eval");
        message.put("options", pythonOptions);

        JSONObject response = sendSocketMessage(message);

        String resultString = getResultString(response);
        log.debug("Deep Eval Result for {} : {}", this, resultString);
    }

    /**
     * Save the deep model.
     */
    public void saveDeepModel() {
        checkModel();

        JSONObject message = new JSONObject();
        message.put("task", "save");
        message.put("options", pythonOptions);

        JSONObject response = sendSocketMessage(message);
    }

    @Override
    public synchronized void close() {
        super.close();
        checkModel();

        if (pythonOptions != null) {
            pythonOptions.clear();
            pythonOptions = null;
        }
        classSize = -1;

        atomIndexes = null;
        dataIndexes = null;
        gradients = null;

        initDeepInference = true;
        initDeepWeightLearning = true;

        closeServer();
    }

    private void checkModel() {
        if (initDeepInference && initDeepWeightLearning) {
            throw new IllegalStateException(
                    "DeepPredicate (" + this + ") has not been initialized via " +
                            "initDeepModelInference() or initDeepModelWeightLearning().");
        }
    }

    /**
     * Initialize deep model variables and python server.
     */
    private void initDeepModel(AtomStore atomStore) {
        // Make sure all python options exist in the predicate.
        validateOptions();
        classSize = Integer.parseInt(pythonOptions.get(CONFIG_CLASS_SIZE));

        // Map entities from file to atoms.
        String entityDataMapPath = FileUtils.makePath(pythonOptions.get(CONFIG_REALTIVE_DIR), pythonOptions.get(CONFIG_ENTITY_DATA_MAP_PATH));
        int numEntityArgs = StringUtils.splitInt(pythonOptions.get(CONFIG_ENTITY_ARGUMENT_INDEXES), ",").length;
        ArrayList<Integer> validAtomIndexes = mapEntitiesFromFileToAtoms(entityDataMapPath, atomStore, numEntityArgs);

        // Switch arraylists to arrays for faster access.
        atomIndexes = new int[validAtomIndexes.size()];
        gradients = new float[validAtomIndexes.size()];
        dataIndexes = new int[validAtomIndexes.size() / classSize];

        for (int i = 0; i < atomIndexes.length; i++) {
            atomIndexes[i] = validAtomIndexes.get(i);
            gradients[i] = 0.0f;
            if (i % classSize == 0) {
                dataIndexes[i / classSize] = i / classSize;
            }
        }

        if (pythonServerProcess != null) {
            log.debug("DeepPredicate server already running.");
            return;
        }

        initServer();
    }

    /**
     * Load predicate options that will be sent to python as strings and verify certain all required options exist.
     */
    private void validateOptions() {
        for (Map.Entry<String, Object> entry : this.getPredicateOptions().entrySet()) {
            pythonOptions.put(entry.getKey(), (String) entry.getValue());
        }

        pythonOptions.put(CONFIG_REALTIVE_DIR, Config.getString("runtime.relativebasepath", null));

        if (pythonOptions.get(CONFIG_MODEL_PATH) == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a model path (\"%s\") specified in predicate config.",
                    CONFIG_MODEL_PATH));
        }

        if (FileUtils.makePath(pythonOptions.get(CONFIG_REALTIVE_DIR), pythonOptions.get(CONFIG_ENTITY_DATA_MAP_PATH)) == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have an entity to data map path (\"%s\") specified in predicate config.",
                    CONFIG_ENTITY_DATA_MAP_PATH));
        }

        if (pythonOptions.get(CONFIG_ENTITY_ARGUMENT_INDEXES) == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have entity argument indexes (\"%s\") specified in predicate config.",
                    CONFIG_ENTITY_ARGUMENT_INDEXES));
        }

        if (pythonOptions.get(CONFIG_CLASS_SIZE) == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a class size (\"%s\") specified in predicate config.",
                    CONFIG_CLASS_SIZE));
        }
    }

    /**
     * Read the entities from a file and map to atom indexes.
     */
    private ArrayList<Integer> mapEntitiesFromFileToAtoms(String filePath, AtomStore atomStore, int numEntityArgs) {
        ArrayList<Integer> atomIndexes = new ArrayList<Integer>();
        Constant[] arguments = new Constant[numEntityArgs + 1];
        ConstantType type;

        String line = null;
        int lineNumber = 0;
        int atomIndex = 0;

        try (BufferedReader reader = FileUtils.getBufferedReader(filePath)) {
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(DELIM);

                // Check that the entity has enough arguments.
                if (parts.length < numEntityArgs) {
                    throw new RuntimeException(String.format(
                            "Entity found on line (%d) must contain %d arguments for predicate %s.",
                            lineNumber, numEntityArgs, this.getName()));
                }

                // Get constant types for this entity.
                for (int index = 0; index < arguments.length - 1; index++) {
                    type = this.getArgumentType(index);
                    arguments[index] = ConstantType.getConstant(parts[index], type);
                }

                // Add atom index and data index for each class.
                type = this.getArgumentType(arguments.length - 1);

                for (int index = 0; index < classSize; index++) {
                    arguments[arguments.length - 1] =  ConstantType.getConstant(String.valueOf(index), type);
                    atomIndex = atomStore.getAtomIndex(this, arguments);
                    if (atomIndex == -1) {
                        break;
                    }
                    atomIndexes.add(atomStore.getAtomIndex(this, arguments));
                }

                // Verify that the entities have atoms for all classes.
                if (atomIndexes.size() % classSize != 0) {
                    throw new RuntimeException(String.format(
                            "Entity found on line (%d) has unspecified class values for predicate %s.",
                            lineNumber, this.getName()));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse entity data map file: " + filePath, ex);
        }

        return atomIndexes;
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

    private void writeIndexData(int size) {
        // Write out the number of values.
        sharedBuffer.putInt(size);

        // Write out the indexes.
        for (int i = 0; i < size; i++) {
            sharedBuffer.putInt(dataIndexes[i]);
        }
    }

    private void initServer() {
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
        int bufferLength = 2 * dataIndexes.length * classSize * Float.SIZE;

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

    private synchronized void closeServer() {
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