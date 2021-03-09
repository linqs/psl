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
package org.deeplearning4j.nn.conf;

import java.util.List;

/**
 * Provide additional access for copying an existing network.
 */
public class MultiLayerConfigurationAccess extends MultiLayerConfiguration {
    public static MultiLayerConfiguration.Builder getBuilder(MultiLayerConfiguration config, NeuralNetConfiguration extraLayer) {
        config = config.clone();

        MultiLayerConfiguration.Builder builder = new MultiLayerConfiguration.Builder();

        List<NeuralNetConfiguration> layerConfigs = config.confs;
        layerConfigs.add(extraLayer);

        builder.backpropType(config.backpropType);
        builder.cacheMode(config.cacheMode);
        builder.confs(layerConfigs);
        builder.dataType(config.dataType);
        builder.inputPreProcessors(config.inputPreProcessors);

        return builder;
    }
}
