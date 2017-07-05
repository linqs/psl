/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.application.inference;

import java.util.Observable;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.application.inference.result.FullInferenceResult;
import org.linqs.psl.application.inference.result.memory.MemoryFullInferenceResult;
import org.linqs.psl.application.util.GroundKernels;
import org.linqs.psl.application.util.Grounding;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.config.Factory;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabasePopulator;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.AtomEventFramework;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.ReasonerFactory;
import org.linqs.psl.reasoner.admm.ADMMReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Infers the most-probable explanation (MPE) state of the
 * {@link RandomVariableAtom RandomVariableAtoms} persisted in a {@link Database},
 * according to a {@link Model}, given the Database's {@link ObservedAtom ObservedAtoms}.
 * <p>
 * The set of RandomVariableAtoms is those persisted in the Database when {@link #mpeInference()}
 * is called. This set must contain all RandomVariableAtoms the Model might access.
 * ({@link DatabasePopulator} can help with this.)
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class LazyMPEInference extends Observable implements ModelApplication {
	
	private static final Logger log = LoggerFactory.getLogger(LazyMPEInference.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "lazympeinference";
	
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
	
	/** Key for int property for the maximum number of rounds of inference. */
	public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxrounds";
	
	/** Default value for MAX_ROUNDS_KEY property */
	public static final int MAX_ROUNDS_DEFAULT = 100;
	
	protected Model model;
	protected Database db;
	protected ConfigBundle config;
	protected final int maxRounds;
	
	/** stop flag to quit the loop. */
	protected boolean toStop = false;
	
	public LazyMPEInference(Model model, Database db, ConfigBundle config) {
		this.model = model;
		this.db = db;
		this.config = config;
		maxRounds = config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);
	}
	
	/**
	 * Minimizes the total weighted incompatibility of the {@link GroundAtom GroundAtoms}
	 * in the Database according to the Model and commits the updated truth
	 * values back to the Database.
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} to be inferred are those
	 * persisted in the Database when this method is called. All RandomVariableAtoms
	 * which the Model might access must be persisted in the Database.
	 * 
	 * @return inference results
	 * @see DatabasePopulator
	 */
	public FullInferenceResult mpeInference() 
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {

		Reasoner reasoner = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		AtomEventFramework eventFramework = new AtomEventFramework(db, config);
		
		/* Registers the Model's Kernels with the AtomEventFramework */
		for (Rule k : model.getRules())
			k.registerForAtomEvents(eventFramework, reasoner);
		
		/* Initializes the ground model */
		Grounding.groundAll(model, eventFramework, reasoner);
		while (eventFramework.checkToActivate() > 0)
			eventFramework.workOffJobQueue();
		
		/* Performs rounds of inference until the ground model stops growing */
		int rounds = 0;
		int numActivated = 0;
		do {
			rounds++;
			log.debug("Starting round {} of inference.", rounds);
			reasoner.optimize();
			/* Only activates if there is another round */
			if (rounds < maxRounds) {
				numActivated = eventFramework.checkToActivate();
				eventFramework.workOffJobQueue();
			}
			log.debug("Completed round {} and activated {} atoms.", rounds, numActivated);
			// notify registered observers
			setChanged();
			notifyObservers(new IntermediateState(rounds, numActivated, maxRounds));
		} while (numActivated > 0 && rounds < maxRounds && !toStop);

		// TODO: Check for consideration events when deciding to terminate?
		
		/* Commits the RandomVariableAtoms back to the Database */
		int count = 0;
		for (RandomVariableAtom atom : db.getAtomCache().getCachedRandomVariableAtoms()) {
			atom.commitToDB();
			count++;
		}
		
		double incompatibility = GroundKernels.getTotalWeightedIncompatibility(reasoner.getCompatibilityKernels());
		double infeasibility = GroundKernels.getInfeasibilityNorm(reasoner.getConstraintKernels());
		
		/* Unregisters the Model's Kernels with the AtomEventFramework */
		for (Rule k : model.getRules())
			k.unregisterForAtomEvents(eventFramework, reasoner);
		
		int size = reasoner.size();
		reasoner.close();
		
		return new MemoryFullInferenceResult(incompatibility, infeasibility, count, size);
	}
	
	/**
	 * Notifies LazyMPEInference to stop inference at the end of the current round
	 */
	public void stop() {
		toStop = true;
	}

	@Override
	public void close() {
		model=null;
		db = null;
		config = null;
	}
	
	/**
	 * Intermediate state object to 
	 * notify the registered observers.
	 *
	 */
	public class IntermediateState {
		public final int rounds;
		public final int numActivated;
		public final int maxRounds; 
		
		public IntermediateState(int currRounds, int currNumActivated, int confMaxRounds) {
			this.rounds = currRounds;
			this.numActivated = currNumActivated;
			this.maxRounds = confMaxRounds;
		}
	}

}
