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
package org.linqs.psl.reasoner.conic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.optimizer.conic.ConicProgramSolver;
import org.linqs.psl.optimizer.conic.ConicProgramSolverFactory;
import org.linqs.psl.optimizer.conic.ipm.HomogeneousIPMFactory;
import org.linqs.psl.optimizer.conic.program.ConicProgram;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;

import com.google.common.collect.Iterables;

/**
 * Reasoner that uses a {@link ConicProgramSolver} to minimize the total weighted
 * incompatibility.
 */
public class ConicReasoner implements Reasoner {

	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "conicreasoner";
	
	/**
	 * Key for {@link org.linqs.psl.config.Factory} or String property.
	 * 
	 * Should be set to a {@link org.linqs.psl.optimizer.conic.ConicProgramSolverFactory}
	 * (or the binary name of one). The ConicReasoner will use this
	 * {@link org.linqs.psl.optimizer.conic.ConicProgramSolverFactory} to
	 * instantiate a {@link org.linqs.psl.optimizer.conic.ConicProgramSolver},
	 * which will then be used for inference.
	 */
	public static final String CPS_KEY = CONFIG_PREFIX + ".conicprogramsolver";
	/**
	 * Default value for CPS_KEY property.
	 * 
	 * Value is instance of {@link org.linqs.psl.optimizer.conic.ipm.HomogeneousIPMFactory}.
	 */
	public static final ConicProgramSolverFactory CPS_DEFAULT = new HomogeneousIPMFactory();

	ConicProgram program;
	ConicProgramSolver solver;
	private final Map<GroundRule, ConicProgramProxy> gkRepresentation;
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
		gkRepresentation = new HashMap<GroundRule, ConicProgramProxy>();
		vars = new HashMap<AtomFunctionVariable, VariableConicProgramProxy>();
	}
	
	@Override
	public void addGroundRule(GroundRule gk) {
		if (gkRepresentation.containsKey(gk)) throw new IllegalArgumentException("Provided evidence has already been added to the reasoner: " + gk);
		ConicProgramProxy proxy;
		if (gk instanceof WeightedGroundRule) {
			proxy = new FunctionConicProgramProxy(this, (WeightedGroundRule) gk);
		} else if (gk instanceof UnweightedGroundRule) {
			proxy = new ConstraintConicProgramProxy(this, ((UnweightedGroundRule)gk).getConstraintDefinition(), gk);
		} else throw new AssertionError("Unrecognized evidence type provided: " + gk);
		gkRepresentation.put(gk, proxy);
	}
	
	@Override
	public boolean containsGroundKernel(GroundRule gk) {
		return gkRepresentation.containsKey(gk);
	}
	
	@Override
	public GroundRule getGroundKernel(GroundRule gk) {
		ConicProgramProxy proxy = gkRepresentation.get(gk);
		return (proxy != null) ? proxy.getGroundKernel() : null;
	}
	
	@Override
	public void changedGroundRule(GroundRule gk) {
		if (!gkRepresentation.containsKey(gk)) throw new IllegalArgumentException("Provided evidence has never been added to the reasoner: " + gk);
		ConicProgramProxy proxy = gkRepresentation.get(gk);
		if (gk instanceof WeightedGroundRule) {
			assert proxy instanceof FunctionConicProgramProxy;
			((FunctionConicProgramProxy)proxy).updateGroundKernel((WeightedGroundRule) gk);
		} else if (gk instanceof UnweightedGroundRule) {
			assert proxy instanceof ConstraintConicProgramProxy;
			((ConstraintConicProgramProxy)proxy).updateConstraint(((UnweightedGroundRule)gk).getConstraintDefinition());
		} else throw new AssertionError("Unrecognized evidence type provided: " + gk);
	}
	
	@Override
	public void changedGroundKernelWeight(WeightedGroundRule gk) {
		ConicProgramProxy proxy = gkRepresentation.get(gk);
		if (proxy instanceof FunctionConicProgramProxy)
			((FunctionConicProgramProxy) proxy).updateGroundKernelWeight((WeightedGroundRule) gk);
		else
			throw new IllegalStateException("Expected a FunctionConicProgramProxy.");
	}
	
	@Override
	public void changedGroundKernelWeights() {
		for (Map.Entry<GroundRule, ConicProgramProxy> e : gkRepresentation.entrySet()) {
			if (e.getKey() instanceof WeightedGroundRule) {
				if (e.getValue() instanceof FunctionConicProgramProxy) {
					((FunctionConicProgramProxy) e.getValue()).updateGroundKernelWeight((WeightedGroundRule) e.getKey());
				}
				else {
					throw new IllegalStateException("Expected a FunctionConicProgramProxy.");
				}
			}
		}
	}
	
	@Override
	public void removeGroundKernel(GroundRule gk) {
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
	public Iterable<GroundRule> getGroundKernels() {
		return Collections.unmodifiableSet(gkRepresentation.keySet());
	}

	@Override
	public Iterable<WeightedGroundRule> getCompatibilityKernels() {
		return Iterables.filter(gkRepresentation.keySet(), WeightedGroundRule.class);
	}
	
	public Iterable<UnweightedGroundRule> getConstraintKernels() {
		return Iterables.filter(gkRepresentation.keySet(), UnweightedGroundRule.class);
	}

	@Override
	public Iterable<GroundRule> getGroundKernels(final Rule r) {
		return Iterables.filter(gkRepresentation.keySet(), new com.google.common.base.Predicate<GroundRule>() {

			@Override
			public boolean apply(GroundRule gr) {
				return gr.getRule().equals(r);
			}
			
		});
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
			p = new VariableConicProgramProxy(this, null);
			vars.put(v, p);
		}
		return p;
	}
}
