/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl;

import org.linqs.psl.model.predicate.model.SupportingModel;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public abstract class NeuralPSLTest {
    public static final String RESOURCES_BASE_FILE = ".resources";
    public static final String SAVED_MODELS_DIRNAME = "saved-models";

    public static final String MODELS_DIRNAME = "model";

    public static final String DATA_DIRNAME = "data";
    public static final String FEATURES_FILENAME = "features.txt";
    public static final String LABELS_FILENAME = "labels.txt";
    public static final String OBSERVATIONS_FILENAME = "observations.txt";

    public static final String SIGN_MODEL_ID = "sign";

    protected final String RESOURCE_DIR;
    protected final String SAVED_MODELS_DIR;

    public NeuralPSLTest() {
        RESOURCE_DIR = (new File(this.getClass().getClassLoader().getResource(RESOURCES_BASE_FILE).getFile())).getParentFile().getAbsolutePath();
        SAVED_MODELS_DIR = Paths.get(RESOURCE_DIR, SAVED_MODELS_DIRNAME).toString();
    }

    public Map<String, String> getModelConfig(String modelID) {
        Map<String, String> config = new HashMap<String, String>();

        config.put(SupportingModel.CONFIG_MODEL, Paths.get(SAVED_MODELS_DIR, modelID, MODELS_DIRNAME).toString());

        config.put(SupportingModel.CONFIG_FEATURES, Paths.get(SAVED_MODELS_DIR, modelID, DATA_DIRNAME, FEATURES_FILENAME).toString());
        config.put(SupportingModel.CONFIG_LABELS, Paths.get(SAVED_MODELS_DIR, modelID, DATA_DIRNAME, LABELS_FILENAME).toString());
        config.put(SupportingModel.CONFIG_OBSERVATIONS, Paths.get(SAVED_MODELS_DIR, modelID, DATA_DIRNAME, OBSERVATIONS_FILENAME).toString());

        return config;
    }
}
