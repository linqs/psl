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
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.psl.application.FullInference;
import edu.umd.cs.psl.application.GroundKernelStore;
import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.groundkernelstore.MemoryGroundKernelStore;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.PSLCoreConfiguration;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
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
import edu.umd.cs.psl.model.kernel.datacertainty.GroundDataCertainty;
import edu.umd.cs.psl.model.kernel.rule.AbstractGroundRule;
import edu.umd.cs.psl.model.kernel.setdefinition.GroundSetDefinition;

public class FixpointModelApplication implements ModelApplication, FullInference {
	
	private static final Logger log = LoggerFactory.getLogger(FixpointModelApplication.class);

	private final DatabaseAtomStoreQuery dbProxy;
	private final AtomEventFramework atomEvents;
	private final AtomStore store;
	private final PSLCoreConfiguration config;
	private final Model model;
	private final Database database;
	private final GroundKernelStore groundkernels;

	private int fixpointIteration;
	private Collection<GroundKernel> nextGKernels;
	
	public FixpointModelApplication(Model m, Database db, PSLCoreConfiguration configuration) {
		config = configuration;
		model = m;
		database = db;
		store = new MemoryAtomStore(database);
		groundkernels = new MemoryGroundKernelStore();
		dbProxy = new DatabaseAtomStoreQuery(store);
		atomEvents = new MemoryAtomEventFramework(m,this,store);
		//database.registerDatabaseEventObserver(atomEvents);
		nextGKernels = new HashSet<GroundKernel>();
		fixpointIteration=-1;
	}
	
	public FixpointModelApplication(Model m, Database db) {
		this(m,db,new PSLCoreConfiguration());
	}
	
	//####### Evidence Handling #####
	
	@Override
	public void addGroundKernel(GroundKernel e) {
		groundkernels.addGroundKernel(e);
		if (fixpointIteration>0) nextGKernels.add(e);
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
	
	@Override
	public void initialize() {
		if (fixpointIteration==-1) {
			atomEvents.setGroundingMode(GroundingMode.ForwardInitial);
			Grounding.groundAll(model, this);
			atomEvents.workOffJobQueue();
			fixpointIteration=0;
		}
	}
	
	@Override
	public FullInferenceResult runInference() {
		RunningProcess proc = LocalProcessMonitor.get().startProcess();
		initialize();
		atomEvents.setGroundingMode(GroundingMode.Forward);
		
		//double activeThreshold = config.getActivationThreshold();
		Iterator<GroundKernel> iter = groundkernels.getGroundKernels().iterator();
		while (iter.hasNext()) {
			fixpointIteration++;
			while (iter.hasNext()) {
				//Update truth values
				GroundKernel e = iter.next();
				Atom atom=null;
				double deltavalue=0.0;
				if (e instanceof AbstractGroundRule) {
					AbstractGroundRule rule = (AbstractGroundRule)e;
					//if (rule.getHeadAtoms().length!=1) throw new IllegalArgumentException("Only support rules with a single atom in the head! " + e);
					//atom = rule.getHeadAtoms()[0];
					//log.trace("Head {} has value {}",atom,atom.getSoftValue(0));
					//TODO: We assume implicitly that the multiplier in front of the head atom is 1
					deltavalue = (1.0-rule.getTruthValue());
					//log.trace("Delta value {} on rule {}",deltavalue,rule);
				} else if (e instanceof GroundSetDefinition) {
					GroundSetDefinition set = (GroundSetDefinition)e;
					atom = set.getSetAtom();
					double setval = set.getAggregateValue();
					assert setval>= atom.getSoftValue(0);
					//TODO: We assume implicitly that the multiplier in front of the setAtom is 1
					deltavalue = setval-atom.getSoftValue(0);
				} else if (e instanceof GroundDataCertainty) {
					//Ignore
				} else {
					throw new IllegalArgumentException("The fixpoint operator can only handle rules and constraints but was given: " + e);
				}

				assert deltavalue>=0.0 : deltavalue + " " + e;	
				assert atom!=null : e;
				if (deltavalue>0.0) {
					//log.trace("Atom {}",atom);
					//log.trace("Delta value: {} | New truth value {}",deltavalue, atom.getSoftValue(0)+deltavalue);
					atom.setSoftValue(0, atom.getSoftValue(0)+deltavalue);
					
					if (atom.hasNonDefaultValues()) {
						assert atom.isRandomVariable() : atom;
						if (!atom.isActive()) {
							atomEvents.activateAtom(atom);
							//log.trace("Activated {}",atom);
						}
						//Add affected rules for next iteration
						//TODO: Filter out those pieces of evidence where the current head atom occurs in the head (and not in the body!) for efficiency
						nextGKernels.addAll(atom.getAllRegisteredGroundKernels());
					}
				}
				

				
			}
			
			atomEvents.workOffJobQueue();
			
			iter = nextGKernels.iterator();
			nextGKernels = new HashSet<GroundKernel>();
		}
		
		for (Atom atom : store.getAtoms(ImmutableSet.of(AtomStatus.ActiveRV, AtomStatus.ConsideredRV))) {
			//log.trace("Atom: {}",AtomPrinter.atomDetails(atom, false, false));
			if (atom.hasNonDefaultValues()) {
				if (!atom.isActive()) {
					throw new AssertionError("Encountered a non-active atom with non-zero truth value! " + atom);
				}
				store.store(atom);
			}
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
		
	}

	
}
