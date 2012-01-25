/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import de.mathnbits.statistics.DoubleDist;
import edu.umd.cs.psl.application.FullConfidenceAnalysis;
import edu.umd.cs.psl.application.GroundKernelStore;
import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.groundkernelstore.MemoryGroundKernelStore;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.config.PSLCoreConfiguration;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.evaluation.process.RunningProcess;
import edu.umd.cs.psl.evaluation.process.local.LocalProcessMonitor;
import edu.umd.cs.psl.evaluation.result.FullConfidenceAnalysisResult;
import edu.umd.cs.psl.evaluation.result.memory.MemoryFullConfidenceAnalysisResult;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.ModelEvent;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.atom.memory.MemoryAtomEventFramework;
import edu.umd.cs.psl.model.atom.memory.MemoryAtomStore;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.ConicReasoner;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.sampler.MarginalSampler;

public class MemoryFullConfidenceAnalysis implements ModelApplication, FullConfidenceAnalysis {
	
	private static final Logger log = LoggerFactory.getLogger(MemoryFullConfidenceAnalysis.class);

	private final Database database;
	private final DatabaseAtomStoreQuery dbProxy;
	private final AtomEventFramework atomEvents;
	private final AtomStore store;
	private final Reasoner reasoner;
	private final PSLCoreConfiguration config;
	private final Model model;
	private final GroundKernelStore groundkernels;
	
	private boolean isInitialized;
	
//	private final Proxy defaultProxy;
	
	public MemoryFullConfidenceAnalysis(Model m, Database db, PSLCoreConfiguration configuration)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		config = configuration;
		model = m;
		database = db;
		store = new MemoryAtomStore(database);
		dbProxy = new DatabaseAtomStoreQuery(store);
		groundkernels = new MemoryGroundKernelStore();
		atomEvents = new MemoryAtomEventFramework(m,this,store,AtomEventFramework.ActivationMode.All);
		reasoner = new ConicReasoner(atomEvents, new EmptyBundle());
		
		isInitialized=false;
	}
	
	public MemoryFullConfidenceAnalysis(Model m, Database db)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		this(m,db,new PSLCoreConfiguration());
	}
	
	//####### Evidence Handling #####
	
	@Override
	public void addGroundKernel(GroundKernel e) {
		Preconditions.checkArgument(!isInitialized);
		groundkernels.addGroundKernel(e);
		reasoner.addGroundKernel(e);
	}
	
	@Override
	public void changedGroundKernel(GroundKernel e) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void removeGroundKernel(GroundKernel e) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean containsGroundKernel(GroundKernel e) {
		return groundkernels.containsGroundKernel(e);
	}
	
	@Override
	public GroundKernel getGroundKernel(GroundKernel e) {
		return groundkernels.getGroundKernel(e);
	}
	
	@Override
	public Iterable<GroundKernel> getGroundKernel() {
		return groundkernels.getGroundKernels();
	}

	@Override
	public Iterable<GroundCompatibilityKernel> getCompatibilityKernels() {
		return groundkernels.getCompatibilityKernels();
	}

	@Override
	public void notifyModelEvent(ModelEvent event) {
		throw new UnsupportedOperationException();		
	}
	
	//####### Inference #####
	
	private void initialize() {
		if (!isInitialized) {
			atomEvents.setGroundingMode(GroundingMode.ForwardInitial);
			Grounding.groundAll(model, this);
			atomEvents.workOffJobQueue();
			isInitialized=true;
		}
	}
	
	@Override
	public FullConfidenceAnalysisResult runConfidenceAnalysis() {
		RunningProcess proc = LocalProcessMonitor.get().startProcess();
		initialize();
		atomEvents.setGroundingMode(GroundingMode.Forward);
		reasoner.mapInference();
		
		MarginalSampler sampler = new MarginalSampler(proc,config.getSamplingSteps());
		Collection<Atom> toActivate;
		do {
			toActivate = sampler.sample(groundkernels.getGroundKernels(), config.getActivationThreshold(), 1);
			if (!toActivate.isEmpty()) {
				for (Atom atom : toActivate) {
					atomEvents.activateAtom(atom);
				}
				atomEvents.workOffJobQueue();
			}
		} while (!toActivate.isEmpty());

		log.debug("Got {} samples.",sampler.getNoSamples());
		
		//Write mean to database
		HashSet<Atom> atoms = new HashSet<Atom>();
		for (Map.Entry<AtomFunctionVariable, DoubleDist> entry : sampler.getDistributions().entrySet()) {
			AtomFunctionVariable atomvar = entry.getKey();
			assert atomvar.getAtom().isRandomVariable();
			double value = entry.getValue().mean();
			double confidence = entry.getValue().stdDev();
			
			atomvar.setValue(value);
			atomvar.setConfidence(confidence);
			atoms.add(atomvar.getAtom());
		}
		for (Atom atom: atoms) {
			log.trace("Atom: {} | confidences: {}",atom,atom.getConfidenceValues());
			store.store(atom);
		}

		proc.terminate();
		return new MemoryFullConfidenceAnalysisResult(proc,sampler.getDistributions());

	}
	
	//####### Interface to other components #####
	
	@Override
	public DatabaseAtomStoreQuery getDatabase() {
		return dbProxy;
	}
	
	@Override
	public AtomEventFramework getAtomManager() {
		return atomEvents;
	}	

	
	@Override
	public void close() {
		reasoner.close();
	}



	
}
