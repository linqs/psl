package org.linqs.psl.model.deep;

import org.linqs.psl.database.AtomStore;
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

    public DeepModelPredicate(Predicate predicate) {
        super();

        this.atomStore = null;
        this.predicate = predicate;

        this.classSize = -1;
        this.atomIndexes = null;
        this.dataIndexes = null;
        this.gradients = null;
        this.symbolicGradients = null;
    }

    public int init() {
        log.debug("Initializing deep model predicate: {}", predicate.getName());

        validateOptions();

        classSize = Integer.parseInt(pythonOptions.get(CONFIG_CLASS_SIZE));
        String entityDataMapPath = FileUtils.makePath(pythonOptions.get(CONFIG_RELATIVE_DIR), pythonOptions.get(CONFIG_ENTITY_DATA_MAP_PATH));
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

        return 2 * dataIndexes.length * classSize * Float.SIZE;
    }

    public void writeFitData() {
        log.debug("Writing fit data for deep model predicate: {}", predicate.getName());
        for (int index = 0; index < gradients.length; index++) {
            gradients[index] = symbolicGradients[atomIndexes[index]];
        }

        writeEntityData(gradients);
    }

    public void writePredictData() {
        log.debug("Writing predict data for deep model predicate: {}", predicate.getName());
        writeIndexData(dataIndexes.length);
    }

    public void readPredictData() {
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

        for(int index = 0; index < atomIndexes.length; index++) {
            deepPrediction = sharedBuffer.getFloat();
            atomIndex = atomIndexes[dataIndexes[index / classSize] * classSize + index % classSize];

            atomValues[atomIndex] = deepPrediction;
            ((RandomVariableAtom)atomStore.getAtom(atomIndex)).setValue(deepPrediction);
        }
    }

    public void writeEvalData() {
        log.debug("Writing eval data for deep model predicate: {}", predicate.getName());
        writeIndexData(dataIndexes.length);
    }

    @Override
    public synchronized void close() {
        super.close();

        classSize = -1;

        atomIndexes = null;
        dataIndexes = null;
        gradients = null;
        symbolicGradients = null;
    }

    public void setAtomStore(AtomStore atomStore) {
        this.atomStore = atomStore;
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
                            lineNumber, numEntityArgs, predicate.getName()));
                }

                // Get constant types for this entity.
                for (int index = 0; index < arguments.length - 1; index++) {
                    type = predicate.getArgumentType(index);
                    arguments[index] = ConstantType.getConstant(parts[index], type);
                }

                // Add atom index and data index for each class.
                type = predicate.getArgumentType(arguments.length - 1);

                for (int index = 0; index < classSize; index++) {
                    arguments[arguments.length - 1] =  ConstantType.getConstant(String.valueOf(index), type);
                    atomIndex = atomStore.getAtomIndex(predicate, arguments);
                    if (atomIndex == -1) {
                        break;
                    }
                    atomIndexes.add(atomStore.getAtomIndex(predicate, arguments));
                }

                // Verify that the entities have atoms for all classes.
                if (atomIndexes.size() % classSize != 0) {
                    throw new RuntimeException(String.format(
                            "Entity found on line (%d) has unspecified class values for predicate %s.",
                            lineNumber, predicate.getName()));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse entity data map file: " + filePath, ex);
        }

        return atomIndexes;
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
}
