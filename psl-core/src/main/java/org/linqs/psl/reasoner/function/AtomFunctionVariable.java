/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.reasoner.function;

import java.util.Map;

import org.linqs.psl.model.atom.GroundAtom;

/**
 * Encapsulates the value of a {@link GroundAtom}
 * for use in numeric functions.
 */
public abstract class AtomFunctionVariable implements FunctionVariable {
	protected final GroundAtom atom;

	public AtomFunctionVariable(GroundAtom atom) {
		this.atom = atom;
	}

	@Override
	public boolean isLinear() {
		return true;
	}

	public GroundAtom getAtom() {
		return atom;
	}

	@Override
	public double getValue() {
		return atom.getValue();
	}

	@Override
	public int hashCode() {
		return atom.hashCode() + 97;
	}

	@Override
	public boolean equals(Object oth) {
		if (oth == this) {
			return true;
		}

		if (oth == null || !(getClass().isInstance(oth))) {
			return false;
		}

		AtomFunctionVariable other = (AtomFunctionVariable)oth;
		return getAtom().equals(other.getAtom());
	}

	@Override
	public String toString() {
		return atom.toString();
	}
}
