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
package edu.umd.cs.psl.reasoner.conic;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventObserver;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolverFactory;
import edu.umd.cs.psl.optimizer.conic.ipm.HomogeneousIPMFactory;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

/**
 * Performs probabilistic inference over {@link Atom Atoms} based on a set of
 * {@link GroundKernel GroundKernels}.
 * 
 * The (unnormalized) probability density function is an exponential model of the
 * following form: P(X) = exp(-sum(w_i * pow(k_i, l))), where w_i is the weight of
 * the ith {@link GroundCompatibilityKernel}, k_i is its incompatibility value,
 * and l is an exponent with value 1 (linear model) or 2 (quadratic model).
 * A state X has zero density if any {@link GroundConstraintKernel} is unsatisfied.
 * 
 * Uses a {@link ConicProgramSolver} to maximize the density.
 */
public class ConicReasoner implements Reasoner, AtomEventObserver {

	private static final Logger log = LoggerFactory.getLogger(ConicReasoner.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "conicreasoner";
	
	/**
	 * Key for {@link edu.umd.cs.psl.config.Factory} or String property.
	 * 
	 * Should be set to a {@link edu.umd.cs.psl.optimizer.conic.ConicProgramSolverFactory}
	 * (or the binary name of one). The ConicReasoner will use this
	 * {@link edu.umd.cs.psl.optimizer.conic.ConicProgramSolverFactory} to
	 * instantiate a {@link edu.umd.cs.psl.optimizer.conic.ConicProgramSolver},
	 * which will then be used for inference.
	 */
	public static final String CPS_KEY = CONFIG_PREFIX + ".conicprogramsolver";
	/**
	 * Default value for CPS_KEY property.
	 * 
	 * Value is instance of {@link edu.umd.cs.psl.optimizer.conic.ipm.HomogeneousIPMFactory}.
	 */
	public static final ConicProgramSolverFactory CPS_DEFAULT = new HomogeneousIPMFactory();
	
	/** Key for {@link DistributionType} property. */
	public static final String DISTRIBUTION_KEY = CONFIG_PREFIX + ".distribution";
	/** Default value for DISTRIBUTION_KEY property. */
	public static final DistributionType DISTRIBUTION_DEFAULT = DistributionType.linear;
	
	/** Key for int property for the maximum number of rounds of inference. */
	public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxrounds";
	
	/** Default value for MAX_ROUNDS_KEY property */
	public static final int MAX_ROUNDS_DEFAULT = 500;

	ConicProgram program;
	ConicProgramSolver solver;
	private final AtomEventFramework atomFramework;
	final DistributionType type;
	private final int maxMapRounds;
	private final Map<GroundKernel, ConicProgramProxy> gkRepresentation;
	private final Map<AtomFunctionVariable, VariableConicProgramProxy> vars;
	
	/**
	 * Constructs a ConicReasoner.
	 * 
	 * @param framework  the AtomEventFramework that manages the {@link edu.umd.cs.psl.model.atom.Atom Atoms}
	 *                     being reasoned over
	 * @param config     configuration for the ConicReasoner
	 */
	public ConicReasoner(AtomEventFramework framework, ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		atomFramework = framework;
		program = new ConicProgram();
		ConicProgramSolverFactory cpsFactory = (ConicProgramSolverFactory) config.getFactory(CPS_KEY, CPS_DEFAULT);
		solver = cpsFactory.getConicProgramSolver(config);
		solver.setConicProgram(program);
		type = (DistributionType) config.getEnum(DISTRIBUTION_KEY, DISTRIBUTION_DEFAULT);
		maxMapRounds = config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);
		gkRepresentation = new HashMap<GroundKernel, ConicProgramProxy>();
		vars = new HashMap<AtomFunctionVariable, VariableConicProgramProxy>();
		
		atomFramework.registerAtomEventObserver(AtomEventSets.MadeRevokedCertainty, this);
	}
	
	@Override
	public DistributionType getDistributionType() {
		return type;
	}
	
	@Override
	public void notifyAtomEvent(AtomEvent event, Atom atom, GroundingMode mode, ModelApplication app) {
		/*We only need to listen for atom that were made certain, because only in this case is it possible
		 * that the atom might have already introduced variables into the program that we should remove
		 * for efficiency.
		 * On the other hand, we do not need to listen for revoking certainty, since variables will automatically
		 * get reintroduced as needed.
		 */
		switch(event) {
		case MadeCertainty:
			for (int i=0;i<atom.getNumberOfValues();i++) {
				vars.get(atom.getVariable(i)).remove();
				vars.remove(atom.getVariable(i));
			}
			break;
		case RevokedCertainty:
			//Don't have to do anything
			break;
		default: throw new IllegalArgumentException("Unsupported event type: " + event);
		}
	}

	
	@Override
	public void addGroundKernel(GroundKernel gk) {
		if (gkRepresentation.containsKey(gk)) throw new IllegalArgumentException("Provided evidence has already been added to the reasoner: " + gk);
		ConicProgramProxy proxy;
		if (gk instanceof GroundCompatibilityKernel) {
			proxy = new FunctionConicProgramProxy(this, (GroundCompatibilityKernel) gk);
		} else if (gk instanceof GroundConstraintKernel) {
			proxy = new ConstraintConicProgramProxy(this, ((GroundConstraintKernel)gk).getConstraintDefinition());
		} else throw new AssertionError("Unrecognized evidence type provided: " + gk);
		gkRepresentation.put(gk, proxy);
	}
	
	@Override
	public boolean containsGroundKernel(GroundKernel gk) {
		return gkRepresentation.containsKey(gk);
	}
	
	@Override
	public void updateGroundKernel(GroundKernel gk) {
		if (!gkRepresentation.containsKey(gk)) throw new IllegalArgumentException("Provided evidence has never been added to the reasoner: " + gk);
		ConicProgramProxy proxy = gkRepresentation.get(gk);
		if (gk instanceof GroundCompatibilityKernel) {
			assert proxy instanceof FunctionConicProgramProxy;
			((FunctionConicProgramProxy)proxy).updateGroundKernel((GroundCompatibilityKernel) gk);
		} else if (gk instanceof GroundConstraintKernel) {
			assert proxy instanceof ConstraintConicProgramProxy;
			((ConstraintConicProgramProxy)proxy).updateConstraint(((GroundConstraintKernel)gk).getConstraintDefinition());
		} else throw new AssertionError("Unrecognized evidence type provided: " + gk);
	}
	
	@Override
	public void removeGroundKernel(GroundKernel gk) {
		if (!gkRepresentation.containsKey(gk)) throw new IllegalArgumentException("Provided evidence has never been added to the reasoner: " + gk);
		ConicProgramProxy proxy = gkRepresentation.get(gk);
		gkRepresentation.remove(gk);
		proxy.remove();
	}
	
	private void inferenceStep() {
		solver.solve();
		
		for (Map.Entry<AtomFunctionVariable, VariableConicProgramProxy> e : vars.entrySet()) {
			e.getKey().setValue(e.getValue().getVariable().getValue());
		}
	}
	
	@Override
	public void mapInference() {
		int rounds = 0;
		int numActivated = 0;
		do {
			rounds++;
			log.debug("Starting round {} optimization",rounds);
			inferenceStep();
			//Only activate if there is another iteration
			if (rounds<maxMapRounds) {
				numActivated = atomFramework.checkToActivate();
				atomFramework.workOffJobQueue();
			}
			log.debug("Completed Round {} and activated {} atoms",rounds,numActivated);
		} while (numActivated>0 && rounds<maxMapRounds);
	}
	
	@Override
	public void close() {
		atomFramework.unregisterAtomEventObserver(AtomEventSets.MadeRevokedCertainty, this);
		program = null;
		solver = null;
	}
	
	protected VariableConicProgramProxy getVarProxy(AtomFunctionVariable v) {
		VariableConicProgramProxy p = vars.get(v);
		if (p == null) {
			p = new VariableConicProgramProxy(this);
			vars.put(v, p);
		}
		return p;
	}
}
