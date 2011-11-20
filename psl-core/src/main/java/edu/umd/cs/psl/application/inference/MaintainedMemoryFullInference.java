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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.psl.application.FullInference;
import edu.umd.cs.psl.application.GroundKernelStore;
import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.groundkernelstore.MemoryGroundKernelStore;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.config.PSLCoreConfiguration;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.evaluation.debug.AtomPrinter;
import edu.umd.cs.psl.evaluation.process.RunningProcess;
import edu.umd.cs.psl.evaluation.process.local.LocalProcessMonitor;
import edu.umd.cs.psl.evaluation.result.FullInferenceResult;
import edu.umd.cs.psl.evaluation.result.memory.MemoryFullInferenceResult;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.ModelEvent;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.atom.memory.MemoryAtomEventFramework;
import edu.umd.cs.psl.model.atom.memory.MemoryAtomStore;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.ConicReasoner;
import edu.umd.cs.psl.reasoner.Reasoner;

public class MaintainedMemoryFullInference implements ModelApplication, FullInference {
	
	private static final Logger log = LoggerFactory.getLogger(MaintainedMemoryFullInference.class);

	private final Database database;
	private final DatabaseAtomStoreQuery dbProxy;
	private final AtomEventFramework atomEvents;
	private final AtomStore store;
	private final Reasoner reasoner;
	private final Model model;
	private final GroundKernelStore groundkernels;
	
	private boolean hasChanged;
	private boolean isInitialized;
	
//	private final Proxy defaultProxy;
	
	public MaintainedMemoryFullInference(Model m, Database db, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		model = m;
		database = db;
		store = new MemoryAtomStore(database);
		dbProxy = new DatabaseAtomStoreQuery(store);
		groundkernels = new MemoryGroundKernelStore();
		atomEvents = new MemoryAtomEventFramework(m,this,store);
		database.registerDatabaseEventObserver(atomEvents);
		reasoner = new ConicReasoner(atomEvents, config);
		model.registerModelObserver(this);
		model.registerModelObserver(atomEvents);
		
		hasChanged=false;
		isInitialized=false;
	}
	
	public MaintainedMemoryFullInference(Model m, Database db)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		this(m,db, new EmptyBundle());
	}
	
	//####### Evidence Handling #####
	
	@Override
	public void addGroundKernel(GroundKernel e) {
		groundkernels.addGroundKernel(e);
		reasoner.addGroundKernel(e);
		hasChanged=true;
	}
	
	@Override
	public void changedGroundKernel(GroundKernel e) {
		groundkernels.changedGroundKernel(e);
		reasoner.updateGroundKernel(e);
		hasChanged=true;
	}
	
	@Override
	public void removeGroundKernel(GroundKernel e) {
		groundkernels.removeGroundKernel(e);
		reasoner.removeGroundKernel(e);
		hasChanged=true;
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
		switch(event) {
		case KernelAdded:
			throw new UnsupportedOperationException();
		case KernelRemoved:
			throw new UnsupportedOperationException();
		case KernelParametersModified:
			for (GroundKernel e : groundkernels.getGroundKernels(event.getKernel())) {
				if (e.updateParameters()) {
					reasoner.updateGroundKernel(e);
				}
			}
			hasChanged=true;
			break;
		default: throw new IllegalArgumentException("Unrecognized model event type: " + event);
		}
		
	}
	
	//####### Inference #####
	
	@Override
	public void initialize() {
		if (!isInitialized) {
			atomEvents.setGroundingMode(GroundingMode.ForwardInitial);
			Grounding.groundAll(model, this);
			while (atomEvents.checkToActivate() > 0)
				atomEvents.workOffJobQueue();
			isInitialized=true;
		}
	}
	
	@Override
	public FullInferenceResult runInference() {
		RunningProcess proc = LocalProcessMonitor.get().startProcess();
		initialize();
		if (hasChanged) {
			atomEvents.setGroundingMode(GroundingMode.Forward);
			reasoner.mapInference();
			//Update truth values
			for (Atom atom : store.getAtoms(ImmutableSet.of(AtomStatus.ActiveRV, AtomStatus.ConsideredRV))) {
				log.trace("Atom: {}",AtomPrinter.atomDetails(atom, false, false));
				if (atom.hasNonDefaultValues()) {
					if (!atom.isActive()) {
						throw new AssertionError("Encountered a non-active atom with non-default value! " + atom);
					}
					store.store(atom);
				}
			}
			hasChanged=false;
		}
		proc.terminate();
		return new MemoryFullInferenceResult(proc,groundkernels.getTotalIncompatibility(),
						store.getNumAtoms(ImmutableSet.of(AtomStatus.ConsideredCertainty,AtomStatus.ActiveRV)),
						groundkernels.size());

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
		model.unregisterModelObserver(this);
		model.unregisterModelObserver(atomEvents);
		reasoner.close();
	}



	
}
