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

import org.linqs.psl.model.atom.RandomVariableAtom;

/**
 * Encapsulates the value of a {@link RandomVariableAtom}
 * for use in numeric functions.
 * <p>
 * This FunctionVariable can change the truth value of the RandomVariableAtom.
 */
public class MutableAtomFunctionVariable extends AtomFunctionVariable {
	public MutableAtomFunctionVariable(RandomVariableAtom atom) {
		super(atom);
	}

	@Override
	public boolean isConstant() {
		return false;
	}

	@Override
	public void setValue(double val) {
		((RandomVariableAtom) atom).setValue(val);
	}
}
