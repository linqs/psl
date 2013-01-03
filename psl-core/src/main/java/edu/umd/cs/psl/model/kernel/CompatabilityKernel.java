package edu.umd.cs.psl.model.kernel;

import edu.umd.cs.psl.model.parameters.Weight;

public interface CompatabilityKernel extends Kernel {
	public Weight getWeight();
	
	public void setWeight(Weight w);
}
