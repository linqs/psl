package edu.umd.cs.psl.application.topicmodel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.admm.ADMMObjectiveTerm;
import edu.umd.cs.psl.reasoner.admm.LinearConstraintTerm;
import edu.umd.cs.psl.reasoner.admm.NegativeLogLossTerm;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner.VariableLocation;

/** Wrapper around the ADMM reasoner which adds a little functionality needed for latent topic networks.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 *
 */
public class LatentTopicNetworkADMMReasoner extends ADMMReasoner {

	/**
	 * Key for positive double property. Minimum value that theta and phi parameters are allowed to take, enforced by clipping the consensus variables to this.
	 */
	public static final String LOWER_BOUND_EPSILON_KEY = CONFIG_PREFIX + ".lowerboundepsilon";
	/** Default value for LOWER_BOUND_EPSILON_KEY property */
	public static final double LOWER_BOUND_EPSILON_DEFAULT = 1e-6; 														   	
	protected double lowerBoundEpsilon; //smallest allowable value for variables. Keeps parameters in the interior of the space.
										//This is necessary for topic modeling as zero-valued entries in theta or phi will cause EM to fail.
	public LatentTopicNetworkADMMReasoner(ConfigBundle config) {
		super(config);
		lowerBoundEpsilon = config.getDouble(LOWER_BOUND_EPSILON_KEY, LOWER_BOUND_EPSILON_DEFAULT);
	}
	
	protected void buildGroundModel() {
		super.buildGroundModel();
		//TODO only the LDA variables!
		for (int i = 0; i < lb.size(); i++) {
			lb.set(i, new Double(lowerBoundEpsilon));
		}
		initDirichletTerms();
	}
	
	/* Initialize ADMM parameters to the optimal value for vanilla LDA.
	 */
	public void initDirichletTerms() {
		System.out.println("Init Dirichlet terms");
		Map<NegativeLogLossTerm, LinearConstraintTerm> dirichletTerms = new HashMap<NegativeLogLossTerm, LinearConstraintTerm>();
		//Find NegativeLogLossTerm and LinearConstraintTerm pairs, and store them in a HashMap.
		for (int i = 0; i < varLocations.size(); i++) {
			List<VariableLocation> vlList = varLocations.get(i);
			NegativeLogLossTerm NLLterm = null;
			LinearConstraintTerm linearConstraintTerm = null;
			for (int j = 0; j < vlList.size(); j++) {
				VariableLocation vl = vlList.get(j);
				ADMMObjectiveTerm term = vl.getTerm();
				if (term instanceof NegativeLogLossTerm) {
					assert (NLLterm == null); //this code currently assumes only one of these per var
					NLLterm = (NegativeLogLossTerm)term;
				}
				if (term instanceof LinearConstraintTerm) {
					assert (linearConstraintTerm == null); //this code currently assumes only one of these per var.
					linearConstraintTerm = (LinearConstraintTerm)term;
				}
			}
			if ((NLLterm != null) && (linearConstraintTerm != null)) {
				//found a Dirichlet potential.
				dirichletTerms.put(NLLterm, linearConstraintTerm);
			}
		}
		
		//Perform the initialization
		for (NegativeLogLossTerm nll : dirichletTerms.keySet()) {
			LinearConstraintTerm lct = dirichletTerms.get(nll);
			double dirichletCoefficientSum = nll.initAsDirichlet();
			lct.initDualVariablesAsDirichlet(dirichletCoefficientSum);
		}
		
	}

}
