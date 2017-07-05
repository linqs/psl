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
package org.linqs.psl.model.predicate;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.term.ConstantType;

/**
 * Predicate of {@link GroundAtom GroundAtoms} that can be persisted in a
 * {@link Database}.
 */
public class StandardPredicate extends Predicate {
	
	/**
	 * Sole constructor.
	 * 
	 * @param name  name for this predicate
	 * @param types  types for each of the predicate's arguments
	 * @see PredicateFactory
	 */
	StandardPredicate(String name, ConstantType[] types) {
		super(name, types);
	}
	
}
