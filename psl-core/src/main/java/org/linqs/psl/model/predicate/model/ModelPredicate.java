/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.model.predicate.model;

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A predicate that is backed by some supporting model.
 *
 * Before a ModelPredicate can be used, loadModel() must be called.
 */
public class ModelPredicate extends StandardPredicate {
    private static final Logger log = LoggerFactory.getLogger(ModelPredicate.class);

    private static final String CONFIG_MIRROR = "mirror";

    protected SupportingModel model;

    private boolean modelLoaded;
    private boolean modelRan;

    protected ModelPredicate(String name, ConstantType[] types, SupportingModel model) {
        super(name, types);

        this.model = model;
        modelLoaded = false;
        modelRan = false;
    }

    @Override
    public boolean isFixedMirror() {
        return true;
    }

    @Override
    public void close() {
        super.close();

        if (model != null) {
            model.close();
            model = null;
        }
    }

    /**
     * Load a supporting model.
     * If any relative paths are supplied in the config, |relativeDir| can be used to resilve them.
     */
    public void loadModel(Map<String, String> config, String relativeDir) {
        if (config.containsKey(CONFIG_MIRROR)) {
            StandardPredicate mirror = StandardPredicate.get(config.get(CONFIG_MIRROR));
            if (mirror == null) {
                throw new IllegalArgumentException(String.format(
                        "Cannot make unknwon predicate (%s) a mirror for %s.",
                        config.get(CONFIG_MIRROR), this));
            }

            setMirror(mirror);
            mirror.setMirror(this);
        }

        model.load(config, relativeDir);
        modelLoaded = true;
    }

    public float getValue(RandomVariableAtom atom) {
        checkModel();

        if (!modelRan) {
            throw new IllegalStateException("Cannot invoke getValue() before runModel() has been called.");
        }

        // TODO(eriq): Warn on out-of-range value?
        float value = model.getValue(atom);

        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public void runModel() {
        checkModel();

        model.run();
        modelRan = true;
    }

    public void resetLabels() {
        checkModel();

        model.resetLabels();
    }

    public float getLabel(RandomVariableAtom atom) {
        checkModel();

        return model.getLabel(atom);
    }

    public void setLabel(RandomVariableAtom atom, float label) {
        checkModel();

        model.setLabel(atom, label);
    }

    public void fit() {
        checkModel();

        log.trace("Fitting {} ({}).", this, model);
        model.fit();
        log.trace("Done fitting {} ({}).", this, model);
    }

    public void initialFit() {
        checkModel();

        log.trace("Initial fitting {} ({}).", this, model);
        model.initialFit();
        log.trace("Done initial fitting {} ({}).", this, model);
    }

    private void checkModel() {
        if (!modelLoaded) {
            throw new IllegalStateException("ModelPredicate (" + this + ") has not been initialized via loadModel().");
        }
    }

    /**
     * The an existing standard predicate (or null if none with this name exists).
     * If the predicate exists, but is not a ModelPredicate, an exception will be thrown.
     */
    public static ModelPredicate get(String name) {
        StandardPredicate predicate = StandardPredicate.get(name);
        if (predicate == null) {
            return null;
        }

        if (!(predicate instanceof ModelPredicate)) {
            throw new ClassCastException("Predicate (" + name + ") is not a ModelPredicate.");
        }

        return (ModelPredicate)predicate;
    }

    /**
     * Get a predicate if one already exists, othereise create a new one.
     */
    public static ModelPredicate get(String name, SupportingModel model, ConstantType... types) {
        ModelPredicate predicate = get(name);
        if (predicate == null) {
            return new ModelPredicate(name, types, model);
        }

        StandardPredicate.validateTypes(predicate, types);

        return predicate;
    }
}
