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

import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class DeepModelPredicate extends DeepModel {
    private static final Logger log = Logger.getLogger(DeepModelPredicate.class);

    private static final String DELIM = "\t";

    public static final String CONFIG_ENTITY_DATA_MAP_PATH = "entity-data-map-path";
    public static final String CONFIG_ENTITY_ARGUMENT_INDEXES = "entity-argument-indexes";
    public static final String CONFIG_CLASS_SIZE = "class-size";

    private AtomStore atomStore;
    private Predicate predicate;

    private int classSize;
    private int[] atomIndexes;
    private int[] dataIndexes;
    private float[] gradients;
    private float[] symbolicGradients;

    private ArrayList<Integer> validAtomIndexes;
    private ArrayList<Integer> validDataIndexes;

    public DeepModelPredicate(Predicate predicate) {
        super("DeepModelPredicate");

        this.atomStore = null;
        this.predicate = predicate;

        this.classSize = -1;
        this.atomIndexes = null;
        this.dataIndexes = null;
        this.gradients = null;
        this.symbolicGradients = null;

        this.validAtomIndexes = new ArrayList<Integer>();
        this.validDataIndexes = new ArrayList<Integer>();
    }

    public DeepModelPredicate copy() {
        DeepModelPredicate copy = new DeepModelPredicate(predicate);

        copy.pythonOptions = pythonOptions;

        copy.application = application;

        freePort(copy.port);
        copy.port = (this.port);

        copy.pythonModule = pythonModule;
        copy.sharedMemoryPath = sharedMemoryPath;
        copy.pythonServerProcess = pythonServerProcess;
        copy.sharedFile = sharedFile;
        copy.sharedBuffer = sharedBuffer;
        copy.socket = socket;
        copy.socketInput = socketInput;
        copy.socketOutput = socketOutput;
        copy.serverOpen = serverOpen;

        copy.atomStore = atomStore;

        copy.classSize = classSize;

        copy.atomIndexes = null;
        if (atomIndexes != null) {
            copy.atomIndexes = Arrays.copyOf(atomIndexes, atomIndexes.length);
        }

        copy.dataIndexes = null;
        if (dataIndexes != null) {
            copy.dataIndexes = Arrays.copyOf(dataIndexes, dataIndexes.length);
        }

        copy.validAtomIndexes = new ArrayList<Integer>(validAtomIndexes.size());
        copy.validAtomIndexes.addAll(validAtomIndexes);
        copy.validDataIndexes = new ArrayList<Integer>(validDataIndexes.size());
        copy.validDataIndexes.addAll(validDataIndexes);

        copy.gradients = null;
        if (gradients != null) {
            copy.gradients = Arrays.copyOf(gradients, gradients.length);
        }

        copy.symbolicGradients = null;
        if (symbolicGradients != null) {
            copy.symbolicGradients = Arrays.copyOf(symbolicGradients, symbolicGradients.length);
        }

        return copy;
    }

    public int init() {
        log.debug("Initializing deep model predicate: {}", predicate.getName());

        validateOptions();

        classSize = Integer.parseInt(pythonOptions.get(CONFIG_CLASS_SIZE));
        String entityDataMapPath = FileUtils.makePath(pythonOptions.get(CONFIG_RELATIVE_DIR), pythonOptions.get(CONFIG_ENTITY_DATA_MAP_PATH));
        int numEntityArgs = StringUtils.splitInt(pythonOptions.get(CONFIG_ENTITY_ARGUMENT_INDEXES), ",").length;
        int maxDataIndex = mapEntitiesFromFileToAtoms(entityDataMapPath, atomStore, numEntityArgs);

        // Switch arraylists to arrays for faster access.
        atomIndexes = new int[validAtomIndexes.size()];
        gradients = new float[validAtomIndexes.size()];
        dataIndexes = new int[validDataIndexes.size()];

        for (int i = 0; i < atomIndexes.length; i++) {
            atomIndexes[i] = validAtomIndexes.get(i);
            gradients[i] = 0.0f;
        }

        for (int i = 0; i < dataIndexes.length; i++) {
            dataIndexes[i] = validDataIndexes.get(i);
        }

        validAtomIndexes.clear();
        validDataIndexes.clear();

        return Integer.SIZE + maxDataIndex * Integer.SIZE + maxDataIndex * classSize * Float.SIZE;
    }

    public void writeFitData() {
        log.debug("Writing fit data for deep model predicate: {}", predicate.getName());
        for (int index = 0; index < gradients.length; index++) {
            gradients[index] = symbolicGradients[atomIndexes[index]];
        }

        writeDataIndexData();
        writeGradientData(gradients);
    }

    public void writePredictData() {
        log.debug("Writing predict data for deep model predicate: {}", predicate.getName());
        writeDataIndexData();
    }

    public float readPredictData() {
        log.debug("Reading predict data for deep model predicate: {}", predicate.getName());
        int count = sharedBuffer.getInt();
        if (count != atomIndexes.length) {
            throw new RuntimeException(String.format(
                    "External model did not make the desired number of predictions, got %d, expected %d.",
                    count, atomIndexes.length));
        }

        float[] atomValues = atomStore.getAtomValues();
        float deepPrediction = 0.0f;
        int atomIndex = 0;

        float change = 0.0f;
        for(int index = 0; index < atomIndexes.length; index++) {
            deepPrediction = sharedBuffer.getFloat();
            atomIndex = atomIndexes[index];

            change += Math.abs(atomValues[atomIndex] - deepPrediction);
            atomValues[atomIndex] = deepPrediction;
            ((RandomVariableAtom)atomStore.getAtom(atomIndex)).setValue(deepPrediction);
        }

        return change;
    }

    public void writeEvalData() {
        log.debug("Writing eval data for deep model predicate: {}", predicate.getName());
        writeDataIndexData();
    }

    @Override
    public void close() {
        super.close();

        classSize = -1;

        atomIndexes = null;
        dataIndexes = null;
        gradients = null;
        symbolicGradients = null;

        validAtomIndexes.clear();
        validDataIndexes.clear();
    }

    public void setAtomStore(AtomStore atomStore) {
        setAtomStore(atomStore, false);
    }

    public void setAtomStore(AtomStore atomStore, boolean init) {
        this.atomStore = atomStore;

        if (init) {
            init();
        }
    }

    public void setSymbolicGradients(float[] symbolicGradients) {
        this.symbolicGradients = symbolicGradients;
    }

    /**
     * Load predicate options that will be sent to python as strings and verify certain all required options exist.
     */
    private void validateOptions() {
        for (Map.Entry<String, Object> entry : predicate.getPredicateOptions().entrySet()) {
            pythonOptions.put(entry.getKey(), (String) entry.getValue());
        }

        if (FileUtils.makePath(pythonOptions.get(CONFIG_RELATIVE_DIR), pythonOptions.get(CONFIG_ENTITY_DATA_MAP_PATH)) == null) {
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

        for (Map.Entry<String, String> entry : pythonOptions.entrySet()) {
            if (entry.getKey().contains(application + "::")) {
                String[] optionParts = entry.getKey().split("::");
                pythonOptions.put(optionParts[1], entry.getValue());
            }
        }
    }

    /**
     * Read the entities from a file and map to atom indexes.
     */
    private int mapEntitiesFromFileToAtoms(String filePath, AtomStore atomStore, int numEntityArgs) {
        Constant[] arguments = new Constant[numEntityArgs + 1];
        ConstantType type;

        String line = null;
        int lineNumber = 0;
        int atomIndex = 0;
        int dataIndex = 0;

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
                            lineNumber, numEntityArgs, predicate.getName()));
                }

                // Get constant types for this entity.
                for (int index = 0; index < arguments.length - 1; index++) {
                    type = predicate.getArgumentType(index);
                    arguments[index] = ConstantType.getConstant(parts[index], type);
                }

                // Add atom index and data index for each class.
                type = predicate.getArgumentType(arguments.length - 1);

                QueryAtom queryAtom = null;
                for (int index = 0; index < classSize; index++) {
                    arguments[arguments.length - 1] =  ConstantType.getConstant(String.valueOf(index), type);

                    if (index == 0) {
                        queryAtom = new QueryAtom(predicate, arguments);
                    } else {
                        queryAtom.assume(predicate, arguments);
                    }

                    atomIndex = atomStore.getAtomIndex(queryAtom);
                    if (atomIndex == -1) {
                        break;
                    }
                    validAtomIndexes.add(atomIndex);
                }

                // Verify that the entities have atoms for all classes.
                if (validAtomIndexes.size() % classSize != 0) {
                    throw new RuntimeException(String.format(
                            "Entity found on line (%d) has unspecified class values for predicate %s.",
                            lineNumber, predicate.getName()));
                }

                if (atomIndex != -1) {
                    validDataIndexes.add(dataIndex);
                }
                dataIndex++;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse entity data map file: " + filePath, ex);
        }

        return dataIndex;
    }

    private void writeGradientData(float[] data) {
        for (int i = 0; i < data.length; i++) {
            sharedBuffer.putFloat(data[i]);
        }
    }

    private void writeDataIndexData() {
        sharedBuffer.putInt(dataIndexes.length);

        for (int i = 0; i < dataIndexes.length; i++) {
            sharedBuffer.putInt(dataIndexes[i]);
        }
    }
}