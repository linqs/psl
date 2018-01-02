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
package org.linqs.psl.evaluation.statistics;

import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.filter.AtomFilter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;

/**
 * Compares {@link GroundAtom GroundAtoms} in a results {@link Database} with those
 * in a baseline Database.
 * <p>
 * The RandomVariableAtoms in the results Database used for comparison can be filtered
 * via {@link #setResultFilter(AtomFilter)}. Initially there is no filter.
 */
public interface ResultComparator {
	/**
	 * Sets the baseline with which to compare.
	 *
	 * @param db  the baseline Database
	 */
	public void setBaseline(Database db);

	/**
	 * Sets a filter on the {@link RandomVariableAtom RandomVariableAtoms}
	 * in the results Database that will be compared with the baseline.
	 *
	 * @param af  the filter
	 */
	public void setResultFilter(AtomFilter af);
}
