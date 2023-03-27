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
import org.linqs.psl.model.atom.RandomVariableAtom;
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
import java.util.HashMap;
import java.util.Map;


/**
 * A predicate that is backed by some deep model.
 *
 * Before a DeepPredicate can be used, loadModel() must be called.
 */
public class DeepPredicate extends StandardPredicate {
    private static final Logger log = Logger.getLogger(DeepPredicate.class);

    private boolean modelLoaded;
    private boolean modelRan;

    public static final String DELIM = "\t";

    public static final String CONFIG_MODEL_PATH = "model-path";
    public static final String CONFIG_ENTITY_FEATURE_MAP_PATH = "entity-feature-map-path";
    public static final String CONFIG_CLASS_SIZE = "class-size";
    public static final String CONFIG_ENTITY_ARGUMENT_INDEXES = "entity-argument-indexes";
    public static final String CONFIG_LABEL_ARGUMENT_INDEXES = "label-argument-indexes";

    /**
     * The indexes of this predicate that compose the entity ID.
     */
    protected int[] entityArgumentIndexes;

    /**
     * The indexes of this predicate that compose the label ID.
     */
    protected int[] labelArgumentIndexes;

    /**
     * Map the ID for an entity to an index.
     * Note that multiple atoms can have the same entity ID.
     */
    protected Map<String, Integer> entityIndexMapping;

    /**
     * Map the ID for a label to its index in the output layer.
     */
    protected Map<String, Integer> labelIndexMapping;

    /**
     * Labels and Gradients manually set by the reasoner to use for fitting.
     * Includes both observed and unobserved data points.
     * [entities x labels]
     */
    protected float[][] manualLabels;
    protected float[][] manualGradients;

    private int featuresSize;
    private int classSize;
    private int batchSize;
    private float learningRate;
    private float alpha;

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

    /**
     * The most recent output from the deep model.
     * This is the full output for all entities.
     * [entities x labels]
     */
    private float[][] deepOutput;

    protected DeepPredicate(String name, ConstantType[] types) {
        super(name, types);

        modelLoaded = false;
        modelRan = false;

        entityIndexMapping = null;
        labelIndexMapping = null;
        entityArgumentIndexes = null;
        labelArgumentIndexes = null;
        featuresSize = -1;
        classSize = -1;

        manualLabels = null;
        manualGradients = null;

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

        batchSize = Options.PREDICATE_DEEP_BATCH_SIZE.getInt();
        learningRate = Options.PREDICATE_DEEP_LEARNING_RATE.getFloat();
        alpha = Options.PREDICATE_DEEP_ALPHA.getFloat();

        deepOutput = null;
    }

    /**
     * Load a deep model.
     * If any relative paths are supplied in the config, |relativeDir| can be used to resilve them.
     */
    public void loadModel(Map<String, String> config, String relativeDir) {
        if (pythonServerProcess != null) {
            throw new RuntimeException("Cannot load a DeepPredicate that has already been loaded.");
        }

        String configModelPath = config.get(CONFIG_MODEL_PATH);
        if (configModelPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a model path (\"%s\") specified in predicate config.",
                    CONFIG_MODEL_PATH));
        }

        String configEntityFeatureMapPath = FileUtils.makePath(relativeDir, config.get(CONFIG_ENTITY_FEATURE_MAP_PATH));
        if (configEntityFeatureMapPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a id feature map path (\"%s\") specified in predicate config.",
                    CONFIG_ENTITY_FEATURE_MAP_PATH));
        }

        String configClassSize = config.get(CONFIG_CLASS_SIZE);
        if (configClassSize == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have a class size (\"%s\") specified in predicate config.",
                    CONFIG_CLASS_SIZE));
        }

        String configEntityArgumentIndexes = config.get(CONFIG_ENTITY_ARGUMENT_INDEXES);
        if (configEntityArgumentIndexes == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have entity argument indexes (\"%s\") specified in predicate config.",
                    CONFIG_ENTITY_ARGUMENT_INDEXES));
        }

        String configLabelArgumentIndexes = config.get(CONFIG_LABEL_ARGUMENT_INDEXES);
        if (configLabelArgumentIndexes == null) {
            throw new IllegalArgumentException(String.format(
                    "A DeepPredicate must have label argument indexes (\"%s\") specified in predicate config.",
                    CONFIG_LABEL_ARGUMENT_INDEXES));
        }

        loadEntityFeatureMap(configEntityFeatureMapPath);
        classSize = Integer.parseInt(configClassSize);
        entityArgumentIndexes = StringUtils.splitInt(configEntityArgumentIndexes, ",");
        labelArgumentIndexes = StringUtils.splitInt(configLabelArgumentIndexes, ",");

        deepOutput = new float[entityIndexMapping.size()][classSize];
        manualLabels = new float[entityIndexMapping.size()][classSize];
        manualGradients = new float[entityIndexMapping.size()][classSize];

        loadModel(configModelPath, configEntityFeatureMapPath, config);
        modelLoaded = true;
    }

    /**
     * Save the model.
     */
    public void save() {
        checkModel();

        JSONObject message = new JSONObject();
        message.put("task", "save");

        JSONObject response = sendSocketMessage(message);
    }

    /**
     * Run the model and store the result internally.
     */
    public void runModel() {
        checkModel();

        JSONObject message = new JSONObject();
        message.put("task", "predict");

        JSONObject response = sendSocketMessage(message);

        // Read the predictions off the buffer.
        sharedBuffer.clear();

        int count = sharedBuffer.getInt();
        if (count != deepOutput.length) {
            throw new RuntimeException(String.format(
                    "External model did not make the desired number of predictions, got %d, expected %d.",
                    count, deepOutput.length));
        }

        for (int entityIndex = 0; entityIndex < deepOutput.length; entityIndex++) {
            for (int labelIndex = 0; labelIndex < classSize; labelIndex++) {
                deepOutput[entityIndex][labelIndex] = sharedBuffer.getFloat();
            }
        }

        modelRan = true;
    }

    /**
     * Fit the model using values set through setLabel().
     */
    public void fit() {
        checkModel();

        log.trace("Fitting {}.", this);

        sharedBuffer.clear();

        // Write out the labels.
        writeEntityData(manualLabels);

        // Write out the gradients.
        writeEntityData(manualGradients);

        sharedBuffer.force();

        String resultString = null;

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("epochs", 1);
        options.put("batch_size", Math.min(batchSize, manualLabels.length));
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

        if (entityIndexMapping != null) {
            entityIndexMapping.clear();
            entityIndexMapping = null;
        }

        if (labelIndexMapping != null) {
            labelIndexMapping.clear();
            labelIndexMapping = null;
        }

        entityArgumentIndexes = null;
        labelArgumentIndexes = null;
        featuresSize = -1;

        manualLabels = null;
        manualGradients = null;

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

    private void checkModel() {
        if (!modelLoaded) {
            throw new IllegalStateException("DeepPredicate (" + this + ") has not been initialized via loadModel().");
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
        // labels + gradients (same size as the labels)
        // = 2 * labels
        // = 2 * (sizeof(int) + (num_entities * num_labels * sizeof(float)))
        int bufferLength = 2 * (Integer.SIZE + (entityIndexMapping.size() * classSize * Float.SIZE));

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
        message.put("model", modelDef);
        message.put("features", featuresPath);
        message.put("num_labels", classSize);
        message.put("entity_argument_length", entityArgumentIndexes.length);
        message.put("shared_memory_path", sharedMemoryPath);
        message.put("options", config);

        sendSocketMessage(message);
    }

    /**
     * Load the file that maps entities to features.
     * The features themselves are not used or stored on the PSL side,
     * but knowing the order and entity ids are important for communication with deep models.
     * The mapping of entity to index will be placed in |entityIndexMapping| and |numFeatures| will be set.
     * Each entity's identifier in |entityIndexMapping| will match their index in the feature file.
     * Although not invoked directly by this class, this is made available to supporting models.
     */
    private void loadEntityFeatureMap(String path) {
        log.debug("Loading features for {} from {}", this, path);

        entityIndexMapping = new HashMap<String, Integer>();

        int width = -1;

        try (BufferedReader reader = FileUtils.getBufferedReader(path)) {
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

                entityIndexMapping.put(getAtomIdentifier(parts, entityArgumentIndexes), entityIndexMapping.size());
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse features file: " + path, ex);
        }

        log.debug("Loaded features for {} [{} x {}]", this, entityIndexMapping.size(), featuresSize);
    }

    private AtomIndexes getAtomIndexes(RandomVariableAtom atom) {
        int entityIndex = getEntityIndex(atom);
        if (entityIndex < 0) {
            log.warn("Could not locate entity for atom: {}", atom);
            return null;
        }

        int labelIndex = getLabelIndex(atom);
        if (labelIndex < 0) {
            log.warn("Could not locate label for atom: {}", atom);
            return null;
        }

        return new AtomIndexes(entityIndex, labelIndex);
    }

    private int getEntityIndex(RandomVariableAtom atom) {
        String key = getAtomIdentifier(atom, entityArgumentIndexes);

        Integer index = entityIndexMapping.get(key);
        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    private int getLabelIndex(RandomVariableAtom atom) {
        String key = getAtomIdentifier(atom, labelArgumentIndexes);

        Integer index = labelIndexMapping.get(key);
        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    private String getAtomIdentifier(String[] stringArgs, int[] argumentIndexes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < argumentIndexes.length; i++) {
            if (i != 0) {
                builder.append(DELIM);
            }

            builder.append(stringArgs[argumentIndexes[i]]);
        }

        return builder.toString();
    }

    private String getAtomIdentifier(RandomVariableAtom atom, int[] argumentIndexes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < argumentIndexes.length; i++) {
            if (i != 0) {
                builder.append(DELIM);
            }

            builder.append(atom.getArguments()[argumentIndexes[i]].rawToString());
        }

        return builder.toString();
    }

    private static final class AtomIndexes {
        public int entityIndex;
        public int labelIndex;

        public AtomIndexes(int entityIndex, int labelIndex) {
            this.entityIndex = entityIndex;
            this.labelIndex = labelIndex;
        }
    }

    private String getResultString(JSONObject response) {
        JSONObject result = response.optJSONObject("result");
        if (result == null) {
            return "<No Result Provided>";
        }

        return result.toString();
    }

    private void writeEntityData(float[][] data) {
        // Write out the number of values.
        sharedBuffer.putInt(data.length);

        // Write out the indexes.
        for (int i = 0; i < data.length; i++) {
            sharedBuffer.putInt(i);
        }

        // Write out the actual data.
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < classSize; j++) {
                sharedBuffer.putFloat(data[i][j]);
            }
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
     * Get and set functions for DeepPredicate.
     */

    public float getValue(int entityIndex, int labelIndex) {
        checkModel();

        return deepOutput[entityIndex][labelIndex];
    }

    public float getValue(RandomVariableAtom atom) {
        checkModel();

        if (!modelRan) {
            throw new IllegalStateException("Cannot invoke getValue() before runModel() has been called.");
        }

        // TODO(eriq): Warn on out-of-range value?
        AtomIndexes indexes = getAtomIndexes(atom);
        if (indexes == null) {
            return 0.0f;
        }

        float value = getValue(indexes.entityIndex, indexes.labelIndex);

        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public float getLabel(RandomVariableAtom atom) {
        checkModel();

        AtomIndexes indexes = getAtomIndexes(atom);
        if (indexes == null) {
            return 0.0f;
        }

        return manualLabels[indexes.entityIndex][indexes.labelIndex];
    }

    public void setLabel(RandomVariableAtom atom, float labelValue) {
        checkModel();

        AtomIndexes indexes = getAtomIndexes(atom);
        if (indexes == null) {
            return;
        }

        manualLabels[indexes.entityIndex][indexes.labelIndex] = labelValue;
    }

    public void resetGradients() {
        checkModel();

        for (int i = 0; i < manualGradients.length; i++) {
            for (int j = 0; j < manualGradients[i].length; j++) {
                manualGradients[i][j] = 0.0f;
            }
        }
    }

    public float getGradient(RandomVariableAtom atom) {
        checkModel();

        AtomIndexes indexes = getAtomIndexes(atom);
        if (indexes == null) {
            return 0.0f;
        }

        return manualGradients[indexes.entityIndex][indexes.labelIndex];
    }

    public void setGradient(RandomVariableAtom atom, float gradientValue) {
        checkModel();

        AtomIndexes indexes = getAtomIndexes(atom);
        if (indexes == null) {
            return;
        }

        manualGradients[indexes.entityIndex][indexes.labelIndex] = gradientValue;
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
