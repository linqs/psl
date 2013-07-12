/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.application.learning.weight;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory;

/**
 * Abstract class for learning the weights of
 * {@link CompatibilityKernel CompatibilityKernels} in a {@link Model}
 * from observed data.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public abstract class WeightLearningApplication extends Observable implements ModelApplication {
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "weightlearning";
	
	/**
	 * Key for {@link Factory} or String property.
	 * <p>
	 * Should be set to a {@link ReasonerFactory} or the fully qualified
	 * name of one. Will be used to instantiate a {@link Reasoner}.
	 * <p>
	 * This reasoner will be used when constructing ground models for weight
	 * learning, unless this behavior is overriden by a subclass.
	 */
	public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
	/**
	 * Default value for REASONER_KEY.
	 * <p>
	 * Value is instance of {@link ADMMReasonerFactory}.
	 */
	public static final ReasonerFactory REASONER_DEFAULT = new ADMMReasonerFactory();
	
	protected Model model;
	protected Database rvDB, observedDB;
	protected ConfigBundle config;
	
	protected final List<CompatibilityKernel> kernels;
	protected TrainingMap trainingMap;
	protected Reasoner reasoner;
	
	public WeightLearningApplication(Model model, Database rvDB, Database observedDB, ConfigBundle config) {
		this.model = model;
		this.rvDB = rvDB;
		this.observedDB = observedDB;
		this.config = config;

		kernels = new ArrayList<CompatibilityKernel>();
	}
	
	/**
	 * Learns new weights.
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} in the distribution are those
	 * persisted in the random variable Database when this method is called. All
	 * RandomVariableAtoms which the Model might access must be persisted in the Database.
	 * <p>
	 * Each such RandomVariableAtom should have a corresponding {@link ObservedAtom}
	 * in the observed Database.
	 * 
	 * @see DatabasePopulator
	 */
	public void learn()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		/* Gathers the CompatibilityKernels */
		for (CompatibilityKernel k : Iterables.filter(model.getKernels(), CompatibilityKernel.class))
			kernels.add(k);
		
		/* Sets up the ground model */
		initGroundModel();
		
		/* Learns new weights */
		doLearn();
		
		kernels.clear();
		cleanUpGroundModel();
	}
	
	protected abstract void doLearn();
	
	/**
	 * Constructs a ground model using model and trainingMap, and stores the
	 * resulting GroundKernels in reasoner.
	 */
	protected void initGroundModel()
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		trainingMap = new TrainingMap(rvDB, observedDB);
		reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		if (trainingMap.getLatentVariables().size() > 0)
			throw new IllegalArgumentException("All RandomVariableAtoms must have " +
					"corresponding ObservedAtoms. Latent variables are not supported " +
					"by this WeightLearningApplication. " +
					"Example latent variable: " + trainingMap.getLatentVariables().iterator().next());
		Grounding.groundAll(model, trainingMap, reasoner);
	}
	
	protected void cleanUpGroundModel() {
		trainingMap = null;
		reasoner.close();
		reasoner = null;
	}

	@Override
	public void close() {
		model = null;
		rvDB = null;
		config = null;
	}

}
