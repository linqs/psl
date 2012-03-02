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
package edu.umd.cs.psl.model.atom.memory;

import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.EmptyBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.ModelEvent;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.atom.AtomJob;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.optimizer.NumericUtilities;

public class MemoryAtomManager implements AtomManager {

	private static final Logger log = LoggerFactory.getLogger(MemoryAtomManager.class);
	
	public static final Predicate AllPredicates = null;
	
	private final MemoryAtomStore store;
	private final ModelApplication application;
	private final Database db;

	private final EnumMap<AtomEvent,SetMultimap<Predicate,AtomEvent.Listener>> atomObservers;
	
	private final Queue<AtomJob> atomjobs;
	
	private GroundingMode groundingMode;

	private final double activationThreshold;
	
	public MemoryAtomManager(ModelApplication application, Database db, ConfigBundle config) {
		this.application = application;
		this.db = db;
		store = new MemoryAtomStore(db);
		atomjobs= new LinkedList<AtomJob>();
		activationThreshold = config.getDouble("memoryatomeventframework.activationthreshold", 0.1);
		groundingMode = GroundingMode.defaultGroundingMode;
		atomObservers = new EnumMap<AtomEvent,SetMultimap<Predicate,AtomEvent.Listener>>(AtomEvent.class);
		for (AtomEvent ae : AtomEvent.values()) {
			SetMultimap<Predicate,AtomEvent.Listener> map = HashMultimap.create();
			atomObservers.put(ae,map);
		}
	}
	
	public MemoryAtomManager(Model model, ModelApplication application, Database db) {
		this(model,application, db, new EmptyBundle());
	}
	
	public MemoryAtomManager(Model model, ModelApplication application, Database db, ConfigBundle config) {
		this(application, db, config);
		for (Kernel k : model.getKernels()) {
			addKernel(k);
		}
	}

	@Override
	public Atom getAtom(Predicate p, GroundTerm[] arguments) {
		MemoryAtom atom = store.getAtom(p, arguments);
		if (atom.getStatus().isUnconsidered()) {
			if (atom.getStatus().isFixed()) {
				atom.setStatus(atom.getStatus().consider());
			}
			else if (atom.getStatus().isRandomVariable()) {
				addAtomJob(atom, AtomEvent.ConsideredRVAtom);
				atom.setStatus(atom.getStatus().consider());
			}
			else
				throw new IllegalStateException("Unknown atom status: " + atom.getStatus());
		}
		else if (!atom.getStatus().isActiveOrConsidered())
			throw new IllegalStateException("Unknown atom status: " + atom.getStatus());
		
		return atom;
	}

	@Override
	public void persist(Atom atom) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyModelEvent(ModelEvent event) {
		switch(event) {
		case KernelAdded:
			addKernel(event.getKernel());
			break;
		case KernelRemoved:
			removeKernel(event.getKernel());
			break;
		case KernelParametersModified:
			// Do nothing
			break;
		default:
			throw new IllegalArgumentException("Unrecognized model event type: " + event);
		}
	}
	
	private void addKernel(Kernel k) {
		k.registerForAtomEvents(this);
	}
	
	private void removeKernel(Kernel k) {
		k.unregisterForAtomEvents(this);
	}
	
	@Override
	public void registerAtomEventObserver(AtomEventSets events, AtomEvent.Listener listener) {
		registerAtomEventObserver(events, AllPredicates, listener);	
	}
	
	@Override
	public void registerAtomEventObserver(AtomEventSets events, Predicate p, AtomEvent.Listener listener) {
		Preconditions.checkNotNull(events);
		Preconditions.checkNotNull(listener);
		for (AtomEvent e : events.subsumes()) {
			atomObservers.get(e).put(p, listener);			
		}	
	}
	
	@Override
	public void unregisterAtomEventObserver(AtomEventSets events, AtomEvent.Listener listener) {
		unregisterAtomEventObserver(events, AllPredicates, listener);
	}

	@Override
	public void unregisterAtomEventObserver(AtomEventSets events, Predicate p, AtomEvent.Listener listener) {
		Preconditions.checkNotNull(events);
		Preconditions.checkNotNull(listener);
		for (AtomEvent e : events.subsumes()) {
			atomObservers.get(e).remove(p, listener);			
		}
	}

	@Override
	public int checkToActivate() {
		int noAffected=0;
		for (Atom atom : store.getAtoms(ImmutableSet.of(AtomStatus.ConsideredRV, AtomStatus.ActiveRV))) {
			if (atom.getStatus().isConsidered() && activateAtom(atom)) noAffected++;
		}
		return noAffected;
	}
	
	@Override
	public int checkToDeactivate() {
		return 0;
	}
	
	@Override
	public boolean activateAtom(Atom atom) {
		if (atom.getStatus().isConsidered() && atom.getStatus().isRandomVariable()) {
			//Should we activate it?
			if (isAboveActivationThreshold(atom)) {
				if (atom.isAtomGroup()) {
					log.debug("Retrieving entire atom group for: {}",atom);
					atom.getAtomsInGroup(this);
				} else {
					atom.activate();
					addAtomJob(atom,AtomEvent.ActivatedRV);
				}
				return true;
			}
		} else if (!atom.getStatus().isActive() && !atom.getStatus().isFixed()) {
			throw new IllegalStateException("Improper invocation of activateAtom on "+atom);
		}
		return false;
	}
	
	@Override
	public boolean deactivateAtom(Atom atom) {
		if (atom.isActive() && atom.isRandomVariable()) {
			//Should we de-activate it?
			if (activationMode==ActivationMode.NonDefault && !isAboveActivationThreshold(atom)) {
				assert !atom.isAtomGroup();
				atom.deactivate();
				addAtomJob(atom,AtomEvent.DeactivatedRV);
				return true;
			}
		} else if (!atom.isConsidered() && !atom.isCertainty()) {
			throw new IllegalStateException("Improper invocation of activateAtom on "+atom);
		}
		return false;
	}
	
	@Override
	public void changeCertainty(Atom atom, double value, double confidence) {
		Preconditions.checkArgument(atom.isInferenceAtom() && atom.isConsideredOrActive() && !atom.isAtomGroup());

		if (atom.isRandomVariable()) {
			atom.makeCertain();
			addAtomJob(atom,AtomEvent.MadeCertainty);
		} 
		
		if (atom.isActive() && value <= activationThreshold) {
			//Deactivate
			//TODO: This is currently not supported!!
//			atom.deactivate();
//			if (atom.isCertainty())
//				addAtomJob(atom,AtomEvent.DeactivatedCertainty);
//			else {
//				assert atom.isRandomVariable();
//				addAtomJob(atom,AtomEvent.DeactivatedRV);
//			}
		} else if (atom.isConsidered() && value > activationThreshold) {
			//Activate
			atom.activate();
			addAtomJob(atom,AtomEvent.ActivatedCertainty);
		}
		atom.setValue(value);
		atom.setConfidenceValue(confidence);
	}

	@Override
	public void release(Atom atom) {
		boolean hasCertaintyBinding=false, hasStrongBinding=false;
		for (GroundKernel gk : atom.getAllRegisteredGroundKernels()) {
			switch(gk.getBinding(atom)) {
			case WeakCertainty:
				hasCertaintyBinding=true;
				break;
			case StrongCertainty:
				hasCertaintyBinding=true;
			case StrongRV:
				hasStrongBinding=true;
				break;
			case WeakRV: break;
			case NoBinding: break;
			default: throw new IllegalStateException("Illegal binding mode");
			}
		}
		if (atom.isCertainty() && !hasCertaintyBinding) {
			atom.revokeCertain();
			addAtomJob(atom,AtomEvent.RevokedCertainty);
		} else if (!hasStrongBinding) {
			if (!atom.isActive()) {
				assert atom.isConsidered();
				atom.unconsider();
				if (atom.isRandomVariable())
					addAtomJob(atom,AtomEvent.ReleasedRV);
				else if (atom.isCertainty())
					addAtomJob(atom,AtomEvent.ReleasedCertainty);
				else throw new IllegalStateException();
			}
		}
	}
	
	private void addAtomJob(Atom atom, AtomEvent jobtype) {
		atomjobs.add(new AtomJob(atom,jobtype));
	}	
	
	@Override
	public void workOffJobQueue() {
		while (!atomjobs.isEmpty()) {
			AtomJob job = atomjobs.poll();
			Atom atom = job.getAtom();
			AtomEvent event = job.getEvent();
			switch (event) {
			case ChangedFactInDefault:
			case ChangedFactToDefault:
			case ChangedFactNonDefault:
			case ChangedFactFromDefault:
				store.store(atom);
				handleAtomEvent(atom,event);
				break;
			case IntroducedCertainty:
				store.store(atom);
				handleAtomEvent(atom,event);
				break;
			case ReleasedCertainty:
				assert atom.isInferenceAtom() && (atom.isConsidered() || atom.isUnconsidered());
				if (atom.isUnconsidered()) {
					handleAtomEvent(atom,event);
					store.free(atom);
					assert !atom.isDefinedAndGround();
				}
				break;
			case ActivatedCertainty:
				store.store(atom);
				if (atom.isActive()) {
					handleAtomEvent(atom,event);
				}
				break;
			case DeactivatedCertainty:
				store.store(atom);
				handleAtomEvent(atom,event);
				break;
			case IntroducedRV:
				handleAtomEvent(atom,event);
				break;
			case ReleasedRV:
				assert atom.isInferenceAtom() && (atom.isConsidered() || atom.isUnconsidered());
				if (atom.isUnconsidered()) {
					handleAtomEvent(atom,event);
					store.free(atom);
					assert !atom.isDefinedAndGround();
				}
				break;			
			case ActivatedRV:
				store.store(atom);
				assert atom.isActive() || (atom.isCertainty() && atom.isConsideredOrActive());
				if (atom.isActive()) {
					handleAtomEvent(atom,event);
				}
				break;
			case DeactivatedRV:
				store.store(atom);
				assert atom.isInferenceAtom() && (!atom.isActive() || atom.isCertainty());
				handleAtomEvent(atom,event);
				break;
			case MadeCertainty:
				store.store(atom);
				handleAtomEvent(atom,event);
				break;
			case RevokedCertainty:
				store.store(atom);
				handleAtomEvent(atom,event);
				release(atom);
				break;
			default: throw new IllegalArgumentException("Unsupported event type: " + event);
			}
			
		}
	}

	private void handleAtomEvent(Atom atom, AtomEvent event) {
		for (AtomEventObserver me : atomObservers.get(event).get(atom.getPredicate())) {
			me.notifyAtomEvent(event, atom, groundingMode, application);
		}
		for (AtomEventObserver me : atomObservers.get(event).get(AllPredicates)) {
			me.notifyAtomEvent(event, atom, groundingMode, application);
		}
	}
	
	/*
	 * ######## Database Observer
	 * Those only matter when grounding mode is forward (and not initial)
	 */

	@Override
	public void changedCertaintyDB(Atom atom, double[] values,
			double[] confidences) {
		Preconditions.checkArgument(atom.isInferenceAtom());

		if (atom.isUnconsidered() && !atom.getPredicate().isNonDefaultValues(values))
			return; //Nothing to do, since it does not affect this application
		
		DataCertaintyKernel dck = DataCertaintyKernel.get();
		if (dck.hasDataCertainty(atom)) {
			assert atom.isCertainty(); 
			if (!NumericUtilities.equals(atom.getSoftValues(), values)) {
				dck.updateDataCertainty(atom, application, values);
			} else throw new IllegalArgumentException("The new values are equal to the old ones!");
		} else {
			//New certainty from db, add database-bound certainty kernel to keep track of the fact
			//that the certainty in an atom is due to the database and be able to check when to revoke
			//certainty in an atom.
			dck.addDataCertainty(atom, application, values);		
		}
		
		if (atom.isRandomVariable()) {
			atom.makeCertain();
			if (atom.isConsideredOrActive())
				addAtomJob(atom,AtomEvent.MadeCertainty);
		}
		assert atom.isCertainty();
		
		if (atom.isActive() && !atom.getPredicate().isNonDefaultValues(values)) {
			//Deactivate
			atom.deactivate();
			addAtomJob(atom,AtomEvent.DeactivatedCertainty);
		} else if (atom.isConsidered() && atom.getPredicate().isNonDefaultValues(values)) {
			//Activate
			atom.activate();
			addAtomJob(atom,AtomEvent.ActivatedCertainty);
		} else if (atom.isUnconsidered()) {
			assert atom.getPredicate().isNonDefaultValues(values);
			atom.consider();
			atom.activate();
			addAtomJob(atom,AtomEvent.IntroducedCertainty);
			addAtomJob(atom,AtomEvent.ActivatedCertainty);
		}
		atom.setValue(values);
		atom.setConfidenceValues(confidences);
	}

	@Override
	public void changedFactDB(Atom atom, double[] newvalues, double[] newconfidences) {
		Preconditions.checkArgument(atom.isFactAtom());
		boolean newNondefault = atom.getPredicate().isNonDefaultValues(newvalues);
		if (atom.hasNonDefaultValues()) {
			if (newNondefault) addAtomJob(atom,AtomEvent.ChangedFactNonDefault);
			else addAtomJob(atom,AtomEvent.ChangedFactToDefault);
		} else {
			if (newNondefault) addAtomJob(atom,AtomEvent.ChangedFactFromDefault);
			else addAtomJob(atom,AtomEvent.ChangedFactInDefault);
		}
		atom.setValue(newvalues);
		atom.setConfidenceValues(newconfidences);
	}

	@Override
	public void removedCertaintyDB(Atom atom) {
		//Remove special database certainty kernel for this atom
		DataCertaintyKernel.get().removeDataCertainty(atom, application);
		release(atom);
	}
	
	private boolean isAboveActivationThreshold(Atom atom) {
		return atom.getValue() > activationThreshold;
	}

	@Override
	public void changeCertainty(Atom atom, double[] values, double[] confidences) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Atom getConsideredAtom(Predicate p, GroundTerm[] arguments) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Atom> getAtoms(AtomStatus status) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Atom> getAtoms(Set<AtomStatus> stati) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Atom> getConsideredAtoms(Predicate p) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Atom> getConsideredAtoms(Predicate p, Object... terms) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getNumAtoms(Set<AtomStatus> stati) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ResultList getNonzeroGroundings(Formula f,
			VariableAssignment partialGrounding, List<Variable> projectTo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultList getNonzeroGroundings(Formula f,
			VariableAssignment partialGrounding) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultList getNonzeroGroundings(Formula f, List<Variable> projectTo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultList getNonzeroGroundings(Formula f) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Entity getEntity(Object entity, ArgumentType type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Entity> getEntities(ArgumentType type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getNumEntities(ArgumentType type) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ResultList getNonfalseGroundings(Formula f) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultList getNonfalseGroundings(Formula f, List<Variable> projectTo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultList getNonfalseGroundings(Formula f,
			VariableAssignment partialGrounding) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResultList getNonfalseGroundings(Formula f,
			VariableAssignment partialGrounding, List<Variable> projectTo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Atom> getAtoms(Set<AtomStatus> stati, Predicate p) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Atom> getAtoms(Set<AtomStatus> stati, Predicate p,
			Object... terms) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getNumAtoms(Set<AtomStatus> stati, Predicate p) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getNumAtoms(Set<AtomStatus> stati, Predicate p, Object... terms) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void open(Predicate predicate) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close(Predicate predicate) {
		// TODO Auto-generated method stub
		
	}

}
