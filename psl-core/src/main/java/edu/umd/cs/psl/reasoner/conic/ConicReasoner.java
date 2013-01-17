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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
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
public class ConicReasoner implements Reasoner {

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

	ConicProgram program;
	ConicProgramSolver solver;
	final DistributionType type;
	private final Map<GroundKernel, ConicProgramProxy> gkRepresentation;
	private final Map<AtomFunctionVariable, VariableConicProgramProxy> vars;
	
	/**
	 * Constructs a ConicReasoner.
	 * 
	 * @param config     configuration for the ConicReasoner
	 */
	public ConicReasoner(ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		program = new ConicProgram();
		ConicProgramSolverFactory cpsFactory = (ConicProgramSolverFactory) config.getFactory(CPS_KEY, CPS_DEFAULT);
		solver = cpsFactory.getConicProgramSolver(config);
		solver.setConicProgram(program);
		type = (DistributionType) config.getEnum(DISTRIBUTION_KEY, DISTRIBUTION_DEFAULT);
		gkRepresentation = new HashMap<GroundKernel, ConicProgramProxy>();
		vars = new HashMap<AtomFunctionVariable, VariableConicProgramProxy>();
	}
	
	@Override
	public DistributionType getDistributionType() {
		return type;
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
	public GroundKernel getGroundKernel(GroundKernel gk) {
		// TODO: make this not a terrible solution
				for (GroundKernel candidate : gkRepresentation.keySet())
					if (gk.equals(candidate))
						return candidate;
				
				return null;
	}
	
	@Override
	public void changedGroundKernel(GroundKernel gk) {
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
	public void changedKernelWeight(CompatibilityKernel k) {
		for (GroundKernel gk : getGroundKernels(k)) {
			ConicProgramProxy proxy = gkRepresentation.get(gk);
			if (proxy instanceof FunctionConicProgramProxy)
				((FunctionConicProgramProxy) proxy).updateGroundKernelWeight((GroundCompatibilityKernel) gk);
			else
				throw new IllegalStateException("Expected a FunctionConicProgramProxy.");
		}
	}
	
	@Override
	public void removeGroundKernel(GroundKernel gk) {
		if (!gkRepresentation.containsKey(gk)) throw new IllegalArgumentException("Provided evidence has never been added to the reasoner: " + gk);
		ConicProgramProxy proxy = gkRepresentation.get(gk);
		gkRepresentation.remove(gk);
		proxy.remove();
	}
	
	@Override
	public void optimize() {
		solver.solve();
		
		for (Map.Entry<AtomFunctionVariable, VariableConicProgramProxy> e : vars.entrySet()) {
			e.getKey().setValue(e.getValue().getVariable().getValue());
		}
	}
	
	@Override
	public Iterable<GroundKernel> getGroundKernels() {
		return Collections.unmodifiableSet(gkRepresentation.keySet());
	}

	@Override
	public Iterable<GroundCompatibilityKernel> getCompatibilityKernels() {
		return Iterables.filter(gkRepresentation.keySet(), GroundCompatibilityKernel.class);
	}
	
	public Iterable<GroundConstraintKernel> getConstraintKernels() {
		return Iterables.filter(gkRepresentation.keySet(), GroundConstraintKernel.class);
	}

	@Override
	public Iterable<GroundKernel> getGroundKernels(final Kernel k) {
		return Iterables.filter(gkRepresentation.keySet(), new com.google.common.base.Predicate<GroundKernel>() {

			@Override
			public boolean apply(GroundKernel gk) {
				return gk.getKernel().equals(k);
			}
			
		});
	}

	@Override
	public double getTotalWeightedIncompatibility() {
		Kernel k;
		double weightedIncompatibility;
		double objective = 0.0;
		for (GroundKernel gk : gkRepresentation.keySet()) {
			weightedIncompatibility = gk.getIncompatibility();
			k = gk.getKernel();
			if (k instanceof CompatibilityKernel)
				weightedIncompatibility *= ((CompatibilityKernel) k).getWeight().getWeight();
			objective += weightedIncompatibility;
		}
		return objective;
	}

	@Override
	public int size() {
		return gkRepresentation.size();
	}
	
	@Override
	public void close() {
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
