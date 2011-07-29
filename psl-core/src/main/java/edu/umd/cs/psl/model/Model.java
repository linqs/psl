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

import java.util.*;

import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.kernel.datacertainty.DataCertaintyKernel;
import edu.umd.cs.psl.model.predicate.PredicateFactory;

public class Model {

	private final PredicateFactory predicateFac;
	private final Set<Kernel> modelEvidence;
	
	private final List<ModelObserver> modelObservers;
	
	public Model() {
		this(new PredicateFactory());
	}
	
	public Model(PredicateFactory fac) {
		predicateFac = fac;
		modelEvidence = new HashSet<Kernel>();
		modelObservers = new ArrayList<ModelObserver>();
		addDefaultKernels();
	}
	
	private void addDefaultKernels() {
		addKernel(DataCertaintyKernel.get());
	}
	
	public void registerModelObserver(ModelObserver app) {
		if (!modelObservers.contains(app)) modelObservers.add(app);
	}
	
	public void unregisterModelObserver(ModelObserver app) {
		if  (!modelObservers.contains(app)) throw new IllegalArgumentException("Application is not a registered observer of this model!");
		modelObservers.remove(app);
	}
	
	public Iterable<Kernel> getKernelTypes() {
		return modelEvidence;
	}
	
	public PredicateFactory getPredicateFactory() {
		return predicateFac;
	}
	
	private void broadcastModelEvent(ModelEvent event) {
		for (ModelObserver app : modelObservers) {
			app.notifyModelEvent(event);
		}
	}
	
	public boolean addKernel(Kernel me) {
		if (modelEvidence.contains(me)) throw new IllegalArgumentException("Evidence type already exists!");
		else {
			modelEvidence.add(me);
			broadcastModelEvent(ModelEvent.addition(me));
			return true;
		}
	}
	
	public boolean removeKernel(Kernel me) {
		if (!modelEvidence.contains(me)) throw new IllegalArgumentException("Evidence type is not part of model!");
		else {
			modelEvidence.remove(me);
			broadcastModelEvent(ModelEvent.addition(me));
			return true;
		}
	}
	
	public void changedKernelParameters(Kernel me) {
		if (!modelEvidence.contains(me)) throw new IllegalArgumentException("Model does not contain specified evidence type: " + me);
		broadcastModelEvent(ModelEvent.parameterUpate(me));
	}
	
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		//s.append("Predicates:\n").append(predicateFac.toString()).append("\n");
		s.append("Model Evidence:\n");
		for (Kernel et : modelEvidence) {
			s.append(et.toString()).append("\n");
		}
		return s.toString();
	}
	
}
