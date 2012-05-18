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
package edu.umd.cs.psl.reasoner.admm;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;
import edu.umd.cs.psl.util.collection.HashList;

public class ADMMReasoner implements Reasoner {
	
	private static final Logger log = LoggerFactory.getLogger(ADMMReasoner.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "admmreasoner";
	
	/**
	 * Key for int property for the maximum number of iterations of ADMM to
	 * perform in a round of inference
	 */
	public static final String MAX_ITER_KEY = CONFIG_PREFIX + ".maxiterations";
	/** Default value for MAX_ITER_KEY property */
	public static final int MAX_ITER_DEFAULT = 1000;
	
	/**
	 * Key for non-negative double property. Controls step size. Higher
	 * values result in larger steps.
	 */
	public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";
	/** Default value for STEP_SIZE_KEY property */
	public static final double STEP_SIZE_DEFAULT = 0.1;
	
	/**
	 * Key for positive double property. A round of ADMM will terminate
	 * when both the primal and dual residuals are less than this threshold.
	 */
	public static final String MAX_RESIDUAL_KEY = CONFIG_PREFIX + ".maxresidual";
	/** Default value for MAX_RESIDUAL_KEY property */
	public static final double MAX_RESIDUAL_DEFAULT = 10e-2;
	
	/** Key for int property for the maximum number of rounds of inference */
	public static final String MAX_ROUNDS_KEY = CONFIG_PREFIX + ".maxrounds";
	/** Default value for MAX_ROUNDS_KEY property */
	public static final int MAX_ROUNDS_DEFAULT = 500;
	
	private final AtomEventFramework atomFramework;
	private final int maxIter;
	/* Sometimes called rho or eta */
	final double stepSize;
	private final double maxResidual;
	private final int maxMapRounds;
	
	/** Ground kernels defining the density function */
	Set<GroundKernel> groundKernels;
	/** Ground kernels wrapped to be objective functions for ADMM */
	Vector<GroundKernelWrapper> factors;
	/** Ordered list of variables for looking up indices */
	HashList<AtomFunctionVariable> variables;
	/** Consensus vector */
	Vector<Double> z;
	/** Lists of local variable locations for updating consensus variables */
	Vector<Vector<VariableLocation>> varLocations;
	
	public ADMMReasoner(AtomEventFramework framework, ConfigBundle config) {
		atomFramework = framework;
		maxIter = config.getInt(MAX_ITER_KEY, MAX_ITER_DEFAULT);
		stepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		maxResidual = config.getDouble(MAX_RESIDUAL_KEY, MAX_RESIDUAL_DEFAULT);
		maxMapRounds = config.getInt(MAX_ROUNDS_KEY, MAX_ROUNDS_DEFAULT);
		
		groundKernels = new HashSet<GroundKernel>();
	}

	@Override
	public void addGroundKernel(GroundKernel gk) {
		groundKernels.add(gk);
	}

	@Override
	public void updateGroundKernel(GroundKernel gk) {
		groundKernels.add(gk);
	}

	@Override
	public void removeGroundKernel(GroundKernel gk) {
		groundKernels.remove(gk);
	}

	@Override
	public boolean containsGroundKernel(GroundKernel gk) {
		return groundKernels.contains(gk);
	}

	@Override
	public void mapInference() {
		int rounds = 0;
		int numActivated = 0;
		// TODO: Change this loop so it runs if ground kernels are added, not
		//       just if atoms are activated
		do {
			rounds++;
			log.debug("Starting round {} optimization", rounds);
			inferenceStep();
			// Only activate if there is another iteration
			if (rounds < maxMapRounds) {
				numActivated = atomFramework.checkToActivate();
				atomFramework.workOffJobQueue();
			}
			log.debug("Completed Round {} and activated {} atoms.", rounds, numActivated);
		} while (numActivated > 0 && rounds < maxMapRounds);
	}
	
	private void inferenceStep() {
		log.debug("Initializing optimization.");
		/* Initializes data structures */
		factors = new Vector<GroundKernelWrapper>(groundKernels.size());
		variables = new HashList<AtomFunctionVariable>(groundKernels.size() * 2);
		z = new Vector<Double>(groundKernels.size() * 2);
		varLocations = new Vector<Vector<VariableLocation>>(groundKernels.size() * 2);
		
		GroundKernel groundKernel;
		
		/* Initializes factors */
		for (Iterator<GroundKernel> itr = groundKernels.iterator(); itr.hasNext(); ) {
			groundKernel = itr.next();
			if (groundKernel instanceof GroundCompatibilityKernel)
				factors.add(new ConvexCompatibilityWrapper(this, (GroundCompatibilityKernel) groundKernel));
			else if (groundKernel instanceof GroundConstraintKernel)
				factors.add(new LinearConstraintWrapper(this, (GroundConstraintKernel) groundKernel));
			else
				throw new IllegalStateException("Unsupported ground kernel: " + groundKernel);
		}
		
		log.debug("Performing optimization with {} variables and {} factors.", z.size(), factors.size());
		
		/* Performs inference */
		double primalRes = Double.POSITIVE_INFINITY;
		double dualRes = Double.POSITIVE_INFINITY;
		int iter = 1;
		while ((primalRes > maxResidual || dualRes > maxResidual) && iter <= maxIter) {
			
			/* Solves each local function */
			for (Iterator<GroundKernelWrapper> itr = factors.iterator(); itr.hasNext(); )
				itr.next().updateLagrange().minimize();
			
			/* Updates consensus variables and computes residuals */
			double total, newZ, diff;
			VariableLocation location;
			primalRes = 0.0;
			dualRes = 0.0;
			for (int i = 0; i < z.size(); i++) {
				total = 0.0;
				/* First pass computes newZ and dual residual */
				for (Iterator<VariableLocation> itr = varLocations.get(i).iterator(); itr.hasNext(); ) {
					location = itr.next();
					total += location.factor.x.get(location.localIndex);
				}
				newZ = total / varLocations.get(i).size();
				diff = z.get(i) - newZ;
				dualRes += diff * diff;
				z.set(i, newZ);
				
				/* Second pass computes primal residuals */
				for (Iterator<VariableLocation> itr = varLocations.get(i).iterator(); itr.hasNext(); ) {
					location = itr.next();
					diff = location.factor.x.get(location.localIndex) - newZ;
					primalRes += diff * diff;
				}
			}
			
			/* Finishes computing the residuals */
			primalRes = Math.sqrt(primalRes);
			dualRes = Math.sqrt(dualRes);
			
			if ((iter-1) % 100 == 0)
				log.debug("Residuals at iter {} -- Primal: {} -- Dual: {}", new Object[] {iter, primalRes, dualRes});
			
			iter++;
		}
		
		/* Updates variables */
		for (int i = 0; i < variables.size(); i++)
			variables.get(i).setValue(z.get(i));
		
		log.debug("Optimization complete.");
	}
	
	int getConsensusIndex(GroundKernelWrapper factor, AtomFunctionVariable var, int localIndex) {
		int consensusIndex = variables.indexOf(var);
		
		/* If the variable has not been given a consensus variable yet */
		if (consensusIndex == -1) {
			consensusIndex = variables.size();
			variables.add(var);
			z.add(var.getValue());
			
			/* Creates a list of local variable locations for the new variable */
			varLocations.add(new Vector<ADMMReasoner.VariableLocation>());
		}
		
		/* Adds a new entry to the list of local variable locations */
		VariableLocation varLocation = new VariableLocation(factor, localIndex);
		varLocations.get(consensusIndex).add(varLocation);
		
		return consensusIndex;
	}

	@Override
	public void close() {
		groundKernels = null;
		factors = null;
		variables = null;
		z = null;
	}
	
	private class VariableLocation {
		private final GroundKernelWrapper factor;
		private final int localIndex;
		
		private VariableLocation(GroundKernelWrapper factor, int localIndex) {
			this.factor = factor;
			this.localIndex = localIndex;
		}
	}

}
