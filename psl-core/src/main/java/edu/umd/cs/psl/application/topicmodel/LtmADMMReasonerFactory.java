package edu.umd.cs.psl.application.topicmodel;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;

/** A factory for creating the latent topic networks wrapper for the ADMM reasoner.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 *
 */
public class LtmADMMReasonerFactory implements ReasonerFactory {
	
	@Override
	public Reasoner getReasoner(ConfigBundle config)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		return new LatentTopicNetworkADMMReasoner(config);
	}
}
