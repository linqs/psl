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
package edu.umd.cs.psl.reasoner;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

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
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;
import edu.umd.cs.psl.optimizer.conic.program.SecondOrderCone;
import edu.umd.cs.psl.optimizer.conic.program.Variable;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.reasoner.function.FunctionSingleton;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.FunctionVariable;
import edu.umd.cs.psl.reasoner.function.MaxFunction;

/**
 * Performs probabilistic inference over {@link edu.umd.cs.psl.model.atom.Atom Atoms}
 * based on a set of {@link edu.umd.cs.psl.model.kernel.GroundKernel GroundKernels}.
 * 
 * The (unnormalized) probability density function is an exponential model of the
 * following form: P(X) = exp(-sum(w_i * pow(k_i, l))), where w_i is the weight of
 * the ith {@link edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel}, k_i is
 * its incompatibility value, and l is an exponent with value 1 (linear model)
 * or 2 (quadratic model). A state X has zero density if any
 * {@link edu.umd.cs.psl.model.kernel.GroundConstraintKernel} is unsatisfied.
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
	
	/**
	 * Distribution types supported by ConicReasoner.
	 * 
	 * A linear distribution does not modify the incompatibility values,
	 * and a quadratic distribution squares them.
	 */
	public static enum DistributionType {linear, quadratic};
	
	/** Key for {@link DistributionType} property. */
	public static final String DISTRIBUTION_KEY = CONFIG_PREFIX + ".distribution";
	
	/** Default value for DISTRIBUTION_KEY property. */
	public static final DistributionType DISTRIBUTION_DEFAULT = DistributionType.linear;
	
	/** Key for int property for the maximum number of rounds of inference. */
	public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxrounds";
	
	/** Default value for MAX_ROUNDS_KEY property */
	public static final int MAX_ROUNDS_DEFAULT = 500;

	private ConicProgram program;
	private ConicProgramSolver solver;
	private final AtomEventFramework atomFramework;
	private final DistributionType type;
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
		type = (DistributionType) config.getEnum(DISTRIBUTION_KEY, DISTRIBUTION_DEFAULT);
		maxMapRounds = config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);
		gkRepresentation = new HashMap<GroundKernel, ConicProgramProxy>();
		vars = new HashMap<AtomFunctionVariable, VariableConicProgramProxy>();
		
		atomFramework.registerAtomEventObserver(AtomEventSets.MadeRevokedCertainty, this);
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
			proxy = new FunctionConicProgramProxy((GroundCompatibilityKernel) gk);
		} else if (gk instanceof GroundConstraintKernel) {
			proxy = new ConstraintConicProgramProxy(((GroundConstraintKernel)gk).getConstraintDefinition());
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
		solver.solve(program);
		
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
	}
	
	protected VariableConicProgramProxy getVarProxy(AtomFunctionVariable v) {
		VariableConicProgramProxy p = vars.get(v);
		if (p == null) {
			p = new VariableConicProgramProxy();
			vars.put(v, p);
		}
		return p;
	}
	
	abstract private class ConicProgramProxy {
		abstract void remove();
	}
	
	private class VariableConicProgramProxy extends ConicProgramProxy {
		protected Variable v;
		protected ConstraintConicProgramProxy upperBound;
		
		VariableConicProgramProxy() {
			v = program.createNonNegativeOrthantCone().getVariable();
			FunctionSummand summand = new FunctionSummand(1.0, new ConicReasonerSingleton(v));
			ConstraintTerm con = new ConstraintTerm(summand, FunctionComparator.SmallerThan, 1.0);
			upperBound = new ConstraintConicProgramProxy(con);
		}
		
		Variable getVariable() {
			return v;
		}
		
		@Override
		void remove() {
			Map<? extends Variable, Double> vars;
			double coeff;
			
			upperBound.remove();
			
			for (LinearConstraint lc : v.getLinearConstraints()) {
				vars = lc.getVariables();
				if (vars.size() == 1) {
					lc.delete();
				}
				else {
					coeff = vars.get(v);
					lc.removeVariable(v);
					lc.setConstrainedValue(lc.getConstrainedValue() - coeff * v.getValue());
				}
			}
			v.getCone().delete();
		}
	}
	
	private class FunctionConicProgramProxy extends ConicProgramProxy {
		protected Variable featureVar;
		protected Variable squaredFeatureVar, innerFeatureVar, innerSquaredVar, outerSquaredVar;
		protected LinearConstraint innerFeatureCon, innerSquaredCon, outerSquaredCon;
		protected Vector<ConstraintConicProgramProxy> constraints;
		protected boolean initialized = false;
		
		FunctionConicProgramProxy(GroundCompatibilityKernel gk) {
			if (gk.getWeight().getWeight() != 0.0) {
				initialize();
				addFunctionTerm(gk.getFunctionDefinition());
				setWeight(gk.getWeight().getWeight());
			}
		}
		
		protected void initialize() {
			if (!initialized) {
				constraints = new Vector<ConstraintConicProgramProxy>(1);
				
				switch (type) {
				case linear:
					featureVar = program.createNonNegativeOrthantCone().getVariable();
					break;
				case quadratic:
					featureVar = program.createNonNegativeOrthantCone().getVariable();
					featureVar.setObjectiveCoefficient(0.0);
					squaredFeatureVar = program.createNonNegativeOrthantCone().getVariable();
					SecondOrderCone soc = program.createSecondOrderCone(3);
					outerSquaredVar = soc.getNthVariable();
					for (Variable v : soc.getVariables()) {
						if (!v.equals(outerSquaredVar))
							if (innerFeatureVar == null)
								innerFeatureVar = v;
							else
								innerSquaredVar = v;
					}
					
					innerFeatureCon = program.createConstraint();
					innerFeatureCon.addVariable(featureVar, 1.0);
					innerFeatureCon.addVariable(innerFeatureVar, -1.0);
					innerFeatureCon.setConstrainedValue(0.0);
					
					innerSquaredCon = program.createConstraint();
					innerSquaredCon.addVariable(innerSquaredVar, 1.0);
					innerSquaredCon.addVariable(squaredFeatureVar, 0.5);
					innerSquaredCon.setConstrainedValue(0.5);
					
					outerSquaredCon = program.createConstraint();
					outerSquaredCon.addVariable(outerSquaredVar, 1.0);
					outerSquaredCon.addVariable(squaredFeatureVar, -0.5);
					outerSquaredCon.setConstrainedValue(0.5);
					break;
				}
				
				initialized = true;
			}
			else {
				throw new IllegalStateException("ConicProgramProxy has already been initialized.");
			}
		}
		
		protected void setWeight(double weight) {
			switch (type) {
			case linear:
				featureVar.setObjectiveCoefficient(weight);
				break;
			case quadratic:
				squaredFeatureVar.setObjectiveCoefficient(weight);
			}
		}
		
		void updateGroundKernel(GroundCompatibilityKernel gk) {
			if (gk.getWeight().getWeight() == 0) {
				if (initialized) {
					remove();
				}
			}
			else {
				if (!initialized) {
					initialize();
				}
				else {
					deleteConstraints();
				}
				addFunctionTerm(gk.getFunctionDefinition());
				setWeight(gk.getWeight().getWeight());
			}
		}
		
		protected void addFunctionTerm(FunctionTerm fun) {
			if (fun instanceof MaxFunction) {
				for (FunctionTerm t : (MaxFunction)fun)
					addFunctionTerm(t);
			}
			else if (!fun.isConstant() || fun.getValue() != 0.0 || !(featureVar.getCone() instanceof NonNegativeOrthantCone)) {
				ConstraintTerm con;
				FunctionSummand featureSummand;
				
				featureSummand = new FunctionSummand(-1.0, new ConicReasonerSingleton(featureVar));
				
				if (fun.isConstant()) {
					con = new ConstraintTerm(featureSummand, FunctionComparator.SmallerThan, -1*fun.getValue());
				}
				else if (fun instanceof FunctionSum) {
					((FunctionSum)fun).add(featureSummand);
					con = new ConstraintTerm(fun, FunctionComparator.SmallerThan, 0.0);
				}
				else if (fun instanceof FunctionSummand) {
					FunctionSum sum = new FunctionSum();
					sum.add((FunctionSummand)fun);
					sum.add(featureSummand);
					con = new ConstraintTerm(sum, FunctionComparator.SmallerThan, 0.0);
				}
				else
					throw new IllegalArgumentException("Unsupported FunctionTerm: " + fun);
				
				constraints.add(new ConstraintConicProgramProxy(con));
			}
		}

		@Override
		void remove() {
			if (initialized) {
				deleteConstraints();
				switch (type) {
				case linear:
					featureVar.getCone().delete();
					featureVar = null;
					break;
				case quadratic:
					innerFeatureCon.delete();
					innerFeatureCon = null;
					innerSquaredCon.delete();
					innerSquaredCon = null;
					outerSquaredCon.delete();
					outerSquaredCon = null;
					featureVar.getCone().delete();
					featureVar = null;
					squaredFeatureVar.getCone().delete();
					squaredFeatureVar = null;
					outerSquaredVar.getCone().delete();
					outerSquaredVar = null;
				default:
					throw new IllegalArgumentException("Unsupported distance norm.");
				}
				
				initialized = false;
			}
		}
		
		protected void deleteConstraints() {
			for (ConstraintConicProgramProxy con : constraints)
				con.remove();
			constraints.clear();
		}
	}
	
	private class ConstraintConicProgramProxy extends ConicProgramProxy {
		protected LinearConstraint lc = null;
		protected Variable slackVar = null;
		
		ConstraintConicProgramProxy(ConstraintTerm con) {
			updateConstraint(con);
		}

		void updateConstraint(ConstraintTerm con) {
			Variable v;
			
			if (lc != null) lc.delete();
			lc = program.createConstraint();
			FunctionTerm fun = con.getFunction();
			double constrainedValue = con.getValue();
			
			if (fun instanceof FunctionSum) {
				FunctionSum sum = (FunctionSum)fun;
				for (FunctionSummand summand : sum) {
					if (summand.getTerm() instanceof ConicReasonerSingleton) {
						v = ((ConicReasonerSingleton)summand.getTerm()).getVariable();
						lc.addVariable(v, summand.getCoefficient());
					}
					else if (summand.getTerm().isConstant()) {
						constrainedValue -= summand.getTerm().getValue() * summand.getCoefficient();
					}
					else if (summand.getTerm() instanceof AtomFunctionVariable) {
						v = getVarProxy((AtomFunctionVariable)summand.getTerm()).getVariable();
						lc.addVariable(v, summand.getCoefficient());
					}
					else
						throw new IllegalArgumentException("Unsupported FunctionSingleton: " + summand.getTerm());
				}
			}
			else if (fun instanceof FunctionSummand) {
				FunctionSummand summand = (FunctionSummand)fun;
				if (summand.getTerm() instanceof ConicReasonerSingleton) {
					v = ((ConicReasonerSingleton)summand.getTerm()).getVariable();
					lc.addVariable(v, summand.getCoefficient());
				}
				else if (summand.getTerm() instanceof AtomFunctionVariable) {
					v = getVarProxy((AtomFunctionVariable) summand.getTerm()).getVariable();
					lc.addVariable(v, summand.getCoefficient());
				}
				else
					throw new IllegalArgumentException("Unsupported FunctionSingleton: " + summand.getTerm());
			}
			else
				throw new IllegalArgumentException("Currently, only sums and summands are supported!");
			
			lc.setConstrainedValue(constrainedValue);
			if (!con.getComparator().equals(FunctionComparator.Equality)) {
				if (slackVar == null) {
					slackVar = program.createNonNegativeOrthantCone().getVariable();
					slackVar.setObjectiveCoefficient(0.0);
				}
				if (con.getComparator().equals(FunctionComparator.LargerThan))
					lc.addVariable(slackVar, -1.0);
				else
					lc.addVariable(slackVar, 1.0);
			}
			else if (slackVar != null) {
				slackVar.getCone().delete();
				slackVar = null;
			}
		}

		@Override
		void remove() {
			if (lc != null) {
				lc.delete();
				lc = null;
			}
			if (slackVar != null) {
				slackVar.getCone().delete();
				slackVar = null;
			}
		}
	}
	
	private class ConicReasonerSingleton implements FunctionSingleton {
		Variable var;
		
		protected ConicReasonerSingleton(Variable v) {
			var = v;
		}
		
		protected Variable getVariable() {
			return var;
		}
		
		@Override
		public double getValue() {
			return var.getValue();
		}

		@Override
		public double getValue(Map<? extends FunctionVariable, Double> values,
				boolean assumeDefaultValue) {
			return getValue();
		}

		@Override
		public boolean isConstant() {
			return false;
		}

		@Override
		public boolean isLinear() {
			return true;
		}
		
	}
}
