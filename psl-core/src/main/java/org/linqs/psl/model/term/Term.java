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
package org.linqs.psl.model.term;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;

/**
 * An argument to a {@link Predicate}.
 * All terms are immutable.
 *
 * @author Matthias Broecheler
 */
public interface Term extends SummationVariableOrTerm {
	/**
	 * @return a human-friendly String identifier
	 */
	public String toString();

	/**
	 * @return hash code
	 */
	public int hashCode();

	/**
	 * Checks equality with an Object.
	 * <p>
	 * Equality semantics are implementation specific.
	 *
	 * @param oth  Object to compare to
	 * @return TRUE if equal, FALSE otherwise
	 */
	public boolean equals(Object oth);
}
