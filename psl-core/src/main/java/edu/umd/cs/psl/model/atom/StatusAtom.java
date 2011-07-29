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
package edu.umd.cs.psl.model.atom;

import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.predicate.Predicate;

public class StatusAtom extends TemplateAtom {

	private AtomStatus status;
	
	public StatusAtom(Predicate p, Term[] args, AtomStatus status) {
		super(p, args);
		this.status=status;
	}

	@Override
	public AtomStatus getStatus() {
		return status;
	}

	@Override
	public boolean isDefined() {
		return status.isDefined();
	}
	
	
	@Override
	public void activate() {
		status = status.activate();
	}

	@Override
	public void makeCertain() {
		status = status.makeCertain();
	}

	@Override
	public void consider() {
		status = status.consider();
	}

	@Override
	public void deactivate() {
		status = status.deactivate();
	}

	@Override
	public void delete() {
		status = status.delete();
	}

	@Override
	public void revokeCertain() {
		status = status.revokeCertain();
	}

	@Override
	public void unconsider() {
		status = status.unconsider();
	}


	@Override
	public boolean isActive() {
		return status.isActive();
	}

	@Override
	public boolean isCertainty() {
		return status.isCertainty();
	}

	@Override
	public boolean isConsidered() {
		return status.isConsidered();
	}
	
	@Override
	public boolean isConsideredOrActive() {
		return status.isConsidered() || status.isActive();
	}

	@Override
	public boolean isFactAtom() {
		return status.isFact();
	}
	
	@Override
	public boolean isKnownAtom() {
		return status.isKnowledge();
	}

	@Override
	public boolean isInferenceAtom() {
		return status.isInference();
	}

	@Override
	public boolean isRandomVariable() {
		return status.isRV();
	}

	@Override
	public boolean isUnconsidered() {
		return status.isUnonsidered();
	}



	
}
