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
package edu.umd.cs.psl.model;

import edu.umd.cs.psl.model.kernel.Kernel;

/**
 * An event related to a {@link Model}.
 */
public enum ModelEvent {
	
	/** A kernel was added to a model */
	KernelAdded,
	/** A kernel was removed from a model */
	KernelRemoved,
	/** A kernel's parameters were modified */
	KernelParametersModified;
	
	/** A listener for ModelEvents */
	public interface Listener {
		/**
		 * Notifies this object of a ModelEvent.
		 * 
		 * @param event  event information
		 */
		public void notifyModelEvent(ModelEvent event);
	}
	
	private Model model;
	private Kernel kernel;
	
	private ModelEvent() {
		model = null;
		kernel = null;
	}

	/**
	 * @return the associated Model, or null if no Model is associated
	 */
	public Model getModel() {
		return model;
	}

	/**
	 * Associates a Model with this event.
	 * 
	 * @param model  the model to associate
	 * @return this event, for convenience
	 */
	public ModelEvent setModel(Model model) {
		this.model = model;
		return this;
	}

	/**
	 * @return the associated Kernel, or null if no Kernel is associated
	 */
	public Kernel getKernel() {
		return kernel;
	}

	/**
	 * Associates a Kernel with this event.
	 * 
	 * @param kernel  the kernel to associate
	 * @return this event, for convenience
	 */
	public ModelEvent setKernel(Kernel kernel) {
		this.kernel = kernel;
		return this;
	}
	
}
