package edu.umd.cs.psl.optimizer.conic.mosek;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolverFactory;

public class MOSEKFactory implements ConicProgramSolverFactory {

	@Override
	public ConicProgramSolver getConicProgramSolver(ConfigBundle config) {
		return new MOSEK(config);
	}

}
