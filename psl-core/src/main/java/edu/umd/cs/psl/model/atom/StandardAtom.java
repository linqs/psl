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

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * A GroundAtom with a {@link StandardPredicate}.
 * <p>
 * A StandardAtom has a truth value that is either observed or inferred, i.e.,
 * it is not defined by a function.
 */
abstract public class StandardAtom extends GroundAtom {

	protected StandardAtom(StandardPredicate p, GroundTerm[] args, Database db, double value) {
		super(p, args, db, value);
	}

}
