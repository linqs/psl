/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.database.atom;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A persisted atom manager that will keep track of atoms that it returns, but that
 * don't actually exist.
 * Then, these atoms can be lazily instantiated.
 */
public class LazyAtomManager extends PersistedAtomManager  {
	/**
	 * All the ground atoms that have been seen, but not instantiated.
	 */
	protected final Set<RandomVariableAtom> lazyAtoms;

	public LazyAtomManager(Database db) {
		super(db);
		this.lazyAtoms = new HashSet<RandomVariableAtom>();
	}

	@Override
	public GroundAtom getAtom(Predicate predicate, Constant... arguments) {
		RandomVariableAtom lazyAtom = null;

		try {
			return super.getAtom(predicate, arguments);
		} catch (PersistedAtomManager.PersistedAccessException ex) {
			lazyAtom = ex.atom;
		}

		lazyAtoms.add(lazyAtom);
		return lazyAtom;
	}

	public Set<RandomVariableAtom> getLazyAtoms() {
		return Collections.unmodifiableSet(lazyAtoms);
	}
}
