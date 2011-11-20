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
package edu.umd.cs.psl.model.kernel.datacertainty;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Parameters;

public class DataCertaintyKernel implements Kernel {

	
	private DataCertaintyKernel() {
	}
	
	@Override
	public boolean isCompatibilityKernel() {
		return false;
	}
	
	@Override
	public Parameters getParameters() {
		return Parameters.NoParameters;
	}
	
	@Override
	public void setParameters(Parameters para) {
		throw new UnsupportedOperationException("Aggregate Predicates have no parameters!");
	}
	
	
	@Override
	public String toString() {
		return "{constraint} DataCertainty";
	}
	
	@Override
	public int hashCode() {
		return super.hashCode();
	}
	
	@Override
	public boolean equals(Object oth) {
		return super.equals(oth);
	}
	
	@Override
	public Kernel clone() {
		return this;
	}

	@Override
	public void groundAll(ModelApplication app) {
		// Do nothing
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework framework,
			DatabaseAtomStoreQuery db) {
		framework.registerAtomEventObserver(AtomEventSets.ReleasedCertainty, this);
		
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework framework,
			DatabaseAtomStoreQuery db) {
		framework.unregisterAtomEventObserver(AtomEventSets.ReleasedCertainty, this);
	}

	@Override
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode,
			ModelApplication app) {
		Preconditions.checkArgument(event==AtomEvent.ReleasedCertainty);
		GroundDataCertainty dc = (GroundDataCertainty)Iterables.getOnlyElement(atom.getRegisteredGroundKernels(this));
		app.removeGroundKernel(dc);		
	}
	
	public boolean hasDataCertainty(Atom atom) {
		return atom.getRegisteredGroundKernels(this).size()>0;
	}
	
	public void addDataCertainty(Atom atom, ModelApplication app, double[] values) {
		Preconditions.checkArgument(atom.getRegisteredGroundKernels(this).isEmpty());
		//Preconditions.checkArgument(atom.isCertainty());
		GroundDataCertainty dc = new GroundDataCertainty(atom,values);
		app.addGroundKernel(dc);
	}
	
	public void updateDataCertainty(Atom atom, ModelApplication app, double[] values) {
		Preconditions.checkArgument(atom.getRegisteredGroundKernels(this).size()==1);
		GroundDataCertainty dc = (GroundDataCertainty)Iterables.getOnlyElement(atom.getRegisteredGroundKernels(this));
		dc.updateValues(values);
		app.changedGroundKernel(dc);
	}
	
	public void removeDataCertainty(Atom atom, ModelApplication app) {
		Preconditions.checkArgument(atom.getRegisteredGroundKernels(this).size()==1);
		GroundDataCertainty dc = (GroundDataCertainty)Iterables.getOnlyElement(atom.getRegisteredGroundKernels(this));
		app.removeGroundKernel(dc);
	}
	
	private static final DataCertaintyKernel certaintyKernel = new DataCertaintyKernel();

	public static DataCertaintyKernel get() {
		return certaintyKernel;
	}
	
}
