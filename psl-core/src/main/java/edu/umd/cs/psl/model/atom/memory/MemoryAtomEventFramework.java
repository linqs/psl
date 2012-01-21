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
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.ModelEvent;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventObserver;
import edu.umd.cs.psl.model.atom.AtomJob;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.kernel.datacertainty.DataCertaintyKernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.optimizer.NumericUtilities;

public class MemoryAtomEventFramework implements AtomEventFramework {

	private static final Logger log = LoggerFactory.getLogger(MemoryAtomEventFramework.class);
	
	public static final Predicate AllPredicates = null;
	
	private final AtomStore store;
	private final ModelApplication application;

	private final EnumMap<AtomEvent,SetMultimap<Predicate,AtomEventObserver>> atomObservers;
	
	private final Queue<AtomJob> atomjobs;
	private final ActivationMode activationMode;
	
	private GroundingMode groundingMode;
	
	public MemoryAtomEventFramework(ModelApplication application, AtomStore store, ActivationMode activemode) {
		this.application = application;
		this.store = store;
		atomjobs= new LinkedList<AtomJob>();
		activationMode = activemode;
		groundingMode = GroundingMode.defaultGroundingMode;
		atomObservers = new EnumMap<AtomEvent,SetMultimap<Predicate,AtomEventObserver>>(AtomEvent.class);
		for (AtomEvent ae : AtomEvent.values()) {
			SetMultimap<Predicate,AtomEventObserver> map = HashMultimap.create();
			atomObservers.put(ae,map);
		}
	}
	
	public MemoryAtomEventFramework(Model model, ModelApplication application, AtomStore store) {
		this(model,application,store,AtomEventFramework.defaultActivationMode);
	}
	
	public MemoryAtomEventFramework(Model model, ModelApplication application, AtomStore store, ActivationMode activemode) {
		this(application,store,activemode);
		for (Kernel k : model.getKernels()) {
			addKernel(k);
		}
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
			//Do nothing
			break;
		default: throw new IllegalArgumentException("Unrecognized model event type: " + event);
		}
	}
	
	private void addKernel(Kernel k) {
		k.registerForAtomEvents(this,application.getDatabase());
	}
	
	private void removeKernel(Kernel k) {
		k.unregisterForAtomEvents(this,application.getDatabase());
	}
	
	@Override
	public void setGroundingMode(GroundingMode mode) {
		groundingMode = mode;
	}
	
	@Override
	public void registerAtomEventObserver(AtomEventSets event, AtomEventObserver me) {
		registerAtomEventObserver(AllPredicates,event,me);	
	}
	
	@Override
	public void registerAtomEventObserver(Predicate p, AtomEventSets event, AtomEventObserver me) {
		Preconditions.checkNotNull(event);
		Preconditions.checkNotNull(me);
		for (AtomEvent e : event.subsumes()) {
			atomObservers.get(e).put(p, me);			
		}	
	}
	
	@Override
	public void unregisterAtomEventObserver(AtomEventSets event, AtomEventObserver me) {
		unregisterAtomEventObserver(AllPredicates,event,me);	
	}
	
	@Override
	public void unregisterAtomEventObserver(Predicate p, AtomEventSets event, AtomEventObserver me) {
		Preconditions.checkNotNull(event);
		Preconditions.checkNotNull(me);
		for (AtomEvent e : event.subsumes()) {
			atomObservers.get(e).remove(p, me);			
		}	
	}
	

	@Override
	public int checkToActivate() {
		int noAffected=0;
		for (Atom atom : store.getAtoms(ImmutableSet.of(AtomStatus.ConsideredRV,AtomStatus.ActiveRV))) {
			if (atom.isConsidered() && activateAtom(atom))
				noAffected++;
		}
		return noAffected;
	}
	
	@Override
	public void checkToDeactivate(Atom atom) {
		throw new UnsupportedOperationException("Not yet implemented!");
	}
	
	@Override
	public boolean activateAtom(Atom atom) {
		if (atom.isConsidered() && atom.isRandomVariable()) {
			//Should we activate it?
			if (activationMode==ActivationMode.All || atom.hasNonDefaultValues()) {
				if (atom.isAtomGroup()) {
					log.debug("Retrieving entire atom group for: {}",atom);
					atom.getAtomsInGroup(this, application.getDatabase());
				} else {
					atom.activate();
					addAtomJob(atom,AtomEvent.ActivatedRV);
				}
				return true;
			}
		} else if (!atom.isActive() && !atom.isCertainty()) {
			throw new IllegalStateException("Improper invocation of activateAtom on "+atom);
		}
		return false;
	}
	
	@Override
	public boolean deactivateAtom(Atom atom) {
		if (atom.isActive() && atom.isRandomVariable()) {
			//Should we de-activate it?
			if (activationMode==ActivationMode.NonDefault && !atom.hasNonDefaultValues()) {
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
	public Atom getAtom(Predicate p, GroundTerm[] arguments) {
		Atom atom = store.getAtom(p, arguments);
		if (atom.isUnconsidered()) {
			if (atom.isFactAtom()) {
				atom.consider();
			} else if (atom.isCertainty()) {
				//add special database certainty kernel
				DataCertaintyKernel.get().addDataCertainty(atom, application, atom.getSoftValues());
				addAtomJob(atom,AtomEvent.IntroducedCertainty);
				atom.consider();
				if (atom.hasNonDefaultValues()) 
					atom.activate(); //No need for notification here, since status was known in database before
			} else if (atom.isRandomVariable()) {
				addAtomJob(atom,AtomEvent.IntroducedRV);
				atom.consider();
			} else throw new IllegalArgumentException("Unknown atom status: "+atom);
		} else assert atom.isConsideredOrActive();
		return atom;
	}
	
	@Override
	public void changeCertainty(Atom atom, double[] values, double[] confidences) {
		Preconditions.checkArgument(atom.isInferenceAtom() && atom.isConsideredOrActive() && !atom.isAtomGroup());

		if (atom.isRandomVariable()) {
			atom.makeCertain();
			addAtomJob(atom,AtomEvent.MadeCertainty);
		} 
		
		if (atom.isActive() && !atom.getPredicate().isNonDefaultValues(values)) {
			//Deactivate
			//TODO: This is currently not supported!!
//			atom.deactivate();
//			if (atom.isCertainty())
//				addAtomJob(atom,AtomEvent.DeactivatedCertainty);
//			else {
//				assert atom.isRandomVariable();
//				addAtomJob(atom,AtomEvent.DeactivatedRV);
//			}
		} else if (atom.isConsidered() && atom.getPredicate().isNonDefaultValues(values)) {
			//Activate
			atom.activate();
			addAtomJob(atom,AtomEvent.ActivatedCertainty);
		}
		atom.setSoftValues(values);
		atom.setConfidenceValues(confidences);
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
					assert !atom.isDefined();
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
					assert !atom.isDefined();
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
		atom.setSoftValues(values);
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
		atom.setSoftValues(newvalues);
		atom.setConfidenceValues(newconfidences);
	}

	@Override
	public void removedCertaintyDB(Atom atom) {
		//Remove special database certainty kernel for this atom
		DataCertaintyKernel.get().removeDataCertainty(atom, application);
		release(atom);
	}



}
