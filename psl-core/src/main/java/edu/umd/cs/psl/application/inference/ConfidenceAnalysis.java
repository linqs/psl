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
package edu.umd.cs.psl.application.inference;

import de.mathnbits.statistics.DoubleDist;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.evaluation.result.FullConfidenceAnalysisResult;
import edu.umd.cs.psl.evaluation.result.memory.MemoryFullConfidenceAnalysisResult;
import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.PersistedAtomManager;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory;
import edu.umd.cs.psl.sampler.MarginalSampler;

/**
 * Infers the means and inverse standard deviations of the
 * {@link RandomVariableAtom RandomVariableAtoms} persisted in a {@link Database},
 * according to a {@link Model}, given the Database's {@link ObservedAtom ObservedAtoms}.
 * <p>
 * The set of RandomVariableAtoms is those persisted in the Database when {@link #mpeInference()}
 * is called. This set must contain all RandomVariableAtoms the Model might access.
 * ({@link DatabasePopulator} can help with this.)
 * 
 * @author Matthias Broecheler
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class ConfidenceAnalysis implements ModelApplication {
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "confidenceanalysis";
	
	/**
	 * Positive integer key for the number of samples to collect for confidence
	 * analysis.
	 */
	public static final String NUM_SAMPLES_KEY = CONFIG_PREFIX + ".numsamples";
	/** Default value for NUM_SAMPLES_KEY */
	public static final int NUM_SAMPLES_DEFAULT = 1000;
	
	/**
	 * Key for {@link Factory} or String property.
	 * <p>
	 * Should be set to a {@link ReasonerFactory} or the fully qualified
	 * name of one. Will be used to instantiate a {@link Reasoner}.
	 */
	public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
	/**
	 * Default value for REASONER_KEY.
	 * <p>
	 * Value is instance of {@link ADMMReasonerFactory}. 
	 */
	public static final ReasonerFactory REASONER_DEFAULT = new ADMMReasonerFactory();
	
	private Model model;
	private Database db;
	private ConfigBundle config;
	private final int numSamples;
	
	public ConfidenceAnalysis(Model model, Database db, ConfigBundle config) {
		this.model = model;
		this.db = db;
		this.config = config;
		numSamples = config.getInt(NUM_SAMPLES_KEY, NUM_SAMPLES_DEFAULT);
		if (numSamples <= 0)
			throw new IllegalArgumentException("Number of samples must be positive.");
	}
	
	/**
	 * Infers the means and inverse standard deviations of the
	 * {@link RandomVariableAtom RandomVariableAtoms} persisted in a {@link Database},
	 * according to a {@link Model}, given the Database's {@link ObservedAtom ObservedAtoms},
	 * sets them as each Atom's truth values and confidence values, and commits
	 * them back to the Database.
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} to be inferred are those
	 * persisted in the Database when this method is called. All RandomVariableAtoms
	 * which the Model might access must be persisted in the Database.
	 * 
	 * @return analysis results
	 * @see DatabasePopulator
	 */
	public FullConfidenceAnalysisResult runConfidenceAnalysis() 
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		Reasoner reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		PersistedAtomManager atomManager = new PersistedAtomManager(db);
		
		/* Builds the ground model */
		Grounding.groundAll(model, atomManager, reasoner);
		
		/* Starts in the MPE state */
		reasoner.optimize();
		
		/* Performs sampling */
		MarginalSampler sampler = new MarginalSampler(numSamples);
		sampler.sample(reasoner.getGroundKernels(), 1.0, 1);
		
		/*
		 * Sets means and confidence scores and commits the RandomVariableAtoms
		 * back to the Database
		 */
		for (RandomVariableAtom atom : atomManager.getPersistedRVAtoms()) {
			DoubleDist dist = sampler.getDistribution(atom.getVariable());
			atom.setValue(dist.mean());
			double conf = dist.stdDev();
			conf = 1 / conf;
			conf = Math.max(conf, ConfidenceValues.getMin());
			conf = Math.min(conf, ConfidenceValues.getMax());
			atom.setConfidenceValue(conf);
			atom.commitToDB();
		}
		
		return new MemoryFullConfidenceAnalysisResult(sampler.getDistributions());

	}

	@Override
	public void close() {
		model=null;
		db = null;
		config = null;
	}

}
