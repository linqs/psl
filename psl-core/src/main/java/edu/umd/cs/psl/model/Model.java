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
package edu.umd.cs.psl.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.ConstraintKernel;
import edu.umd.cs.psl.model.kernel.Kernel;

/**
 * A probabilistic soft logic model.
 * <p>
 * Encapsulates a set of {@link Kernel Kernels}. A {@link ModelApplication}
 * can be used to combine a Model with data to perform inference or learn.
 * <p>
 * Objects which use a Model should register with it to listen
 * for {@link ModelEvent ModelEvents}.
 */
public class Model {

	protected final List<Kernel> kernels;
	/** Redundant set for fast membership checks */
	protected final Set<Kernel> kernelSet;
	protected final Set<ModelEvent.Listener> modelObservers;
	
	/**
	 * Sole constructor.
	 */
	public Model() {
		kernels = new LinkedList<Kernel>();
		kernelSet = new HashSet<Kernel>();
		modelObservers = new HashSet<ModelEvent.Listener>();
	}
	
	/**
	 * Registers an observer to receive {@link ModelEvent ModelEvents}.
	 * 
	 * @param observer  object to notify of events
	 */
	public void registerModelObserver(ModelEvent.Listener observer) {
		if (!modelObservers.contains(observer)) modelObservers.add(observer);
	}
	
	/**
	 * Unregisters an observer so it will no longer receive {@link ModelEvent ModelEvents}
	 * from this model.
	 * 
	 * @param observer  object to stop notifying
	 */
	public void unregisterModelObserver(ModelEvent.Listener observer) {
		if  (!modelObservers.contains(observer))
			throw new IllegalArgumentException("Object is not a registered observer of this model.");
		modelObservers.remove(observer);
	}
	
	/**
	 * @return the {@link Kernel Kernels} contained in this model
	 */
	public Iterable<Kernel> getKernels() {
		return Collections.unmodifiableList(kernels);
	}
	
	/**
	 * Adds a Kernel to this Model.
	 * <p>
	 * All observers of this Model will receive a {@link ModelEvent#KernelAdded} event.
	 * 
	 * @param k  Kernel to add
	 * @throws IllegalArgumentException  if the Kernel is already in this Model
	 */
	public void addKernel(Kernel k) {
		if (kernelSet.contains(k))
			throw new IllegalArgumentException("Kernel already added to this model.");
		else {
			kernels.add(k);
			kernelSet.add(k);
			broadcastModelEvent(new ModelEvent(ModelEvent.Type.KernelAdded, this, k));
		}
	}
	
	/**
	 * Removes a Kernel from this Model.
	 * <p>
	 * All observers of this Model will receive a {@link ModelEvent#KernelRemoved} event.
	 * 
	 * @param k  Kernel to remove
	 * @throws IllegalArgumentException  if the Kernel is not in this Model
	 */
	public void removeKernel(Kernel k) {
		if (!kernelSet.contains(k))
			throw new IllegalArgumentException("Kernel not in this model.");
		else {
			kernels.remove(k);
			kernelSet.remove(k);
			
			broadcastModelEvent(new ModelEvent(ModelEvent.Type.KernelRemoved, this, k));
		}
	}
	
	/**
	 * Notifies this Model that a Kernel's parameters were modified.
	 * <p>
	 * All observers of this model will receive a {@link ModelEvent#KernelParametersModified} event.
	 * 
	 * @param k  the Kernel that was modified
	 * @throws IllegalArgumentException  if the Kernel is not in this Model
	 */
	public void notifyKernelParametersModified(Kernel k) {
		if (!kernelSet.contains(k))
			throw new IllegalArgumentException("Kernel not in this model.");
		broadcastModelEvent(new ModelEvent(ModelEvent.Type.KernelParametersModified, this, k));
	}
	
	/**
	 * Returns a String representation of this Model.
	 * <p>
	 * The String will start with "Model:", followed by a newline, then
	 * the String representations of each Kernel in this Model (each followed
	 * by a newline). Constraint Kernels will come before compatibility kernels.
	 * 
	 * @return the String representation 
	 * @see Kernel#isCompatibilityKernel()
	 */
	@Override
	public String toString() {
		List<Kernel> constraintKernels = new LinkedList<Kernel>();
		List<Kernel> compatibilityKernels = new LinkedList<Kernel>();
		for (Kernel kernel : kernels)
			if (kernel instanceof CompatibilityKernel)
				compatibilityKernels.add(kernel);
			else if (kernel instanceof ConstraintKernel)
				constraintKernels.add(kernel);
			else
				throw new IllegalStateException("Unrecognized kernel: " + kernel);

		StringBuilder s = new StringBuilder();
		s.append("Model:\n");
		for (Kernel kernel : constraintKernels)
			s.append(kernel.toString()).append("\n");
		for (Kernel kernel : compatibilityKernels)
			s.append(kernel.toString()).append("\n");
		
		return s.toString();
	}
	
	protected void broadcastModelEvent(ModelEvent event) {
		for (ModelEvent.Listener observer : modelObservers) {
			observer.notifyModelEvent(event);
		}
	}
	
}
