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

import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;

/**
 * Computes statistics by comparing the truth value of each {@link RandomVariableAtom}
 * in the results Database with that of the corresponding {@link ObservedAtom} in a baseline.
 * Any GroundAtoms that are not ObservedAtoms in the baseline are not counted towards
 * the statistics.
 */
public interface PredictionComparator extends ResultComparator {
	public PredictionStatistics compare(StandardPredicate predicate);
}
