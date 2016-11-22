/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.optimizer.conic.ipm;

import java.util.List;
import java.util.Vector;

import org.linqs.psl.config.EmptyBundle;
import org.linqs.psl.experimental.optimizer.conic.ConicProgramSolver;
import org.linqs.psl.experimental.optimizer.conic.ipm.IPM;
import org.linqs.psl.optimizer.conic.ConicProgramSolverContractTest;

public class IPMTest extends ConicProgramSolverContractTest {

	@Override
	protected List<? extends ConicProgramSolver> getConicProgramSolverImplementations() {
		Vector<IPM> solvers = new Vector<IPM>(1);
		solvers.add(new IPM(new EmptyBundle()));
		return solvers;
	}

}
