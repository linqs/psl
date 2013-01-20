package edu.umd.cs.psl.application.learning.weight;

import java.util.HashSet;
import java.util.Set;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;

/**
 * Special ground kernel that penalizes being close to a fixed value
 * 
 * @author Bert Huang <bert@cs.umd.edu>
 */
public class LossAugmentingGroundKernel implements GroundCompatibilityKernel {
	
	/**
	 * 
	 * @param atom 
	 * @param truthValue
	 */
	public LossAugmentingGroundKernel(GroundAtom atom, double truthValue) {
		this.atom = atom;
		this.groundTruth = truthValue;
	}

	@Override
	public boolean updateParameters() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public CompatibilityKernel getKernel() {
		return null;
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		Set<GroundAtom> ret = new HashSet<GroundAtom>();
		ret.add(atom);
		return ret;
	}

	@Override
	public double getIncompatibility() {
		return Math.abs(atom.getValue() - this.groundTruth);
	}

	@Override
	public BindingMode getBinding(Atom atom) {
		// TODO Auto-generated method stub (uh what?)
		return null;
	}

	@Override
	public Weight getWeight() {
		return new PositiveWeight(1.0);
	}

	@Override
	public double getIncompatibilityDerivative(int parameterNo) {
		return 1.0;
	}

	@Override
	public double getIncompatibilityHessian(int parameterNo1, int parameterNo2) {
		return 0.0;
	}

	@Override
	public FunctionTerm getFunctionDefinition() {
		return null;
	}

	
	private GroundAtom atom;
	private double groundTruth;
}
