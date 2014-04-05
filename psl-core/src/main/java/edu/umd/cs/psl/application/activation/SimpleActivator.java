package edu.umd.cs.psl.application.activation;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.config.Factory;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.*;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.reasoner.ReasonerFactory;
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory;

public class SimpleActivator implements Activator {
	
	private static final Logger log = LoggerFactory.getLogger(MPEInference.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "activator";
	
	/**
	 * Key for {@link Factory} or String property.
	 * <p>
	 * Should be set to a {@link ReasonerFactory} or the fully qualified
	 * name of one. Will be used to instantiate a {@link Reasoner}.
	 */
	public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
	/**
	 * Default value for REASONER_KEY.
	 * <p>
	 * Value is instance of {@link ADMMReasonerFactory}. 
	 */
	public static final ReasonerFactory REASONER_DEFAULT = new ADMMReasonerFactory();
	
	private GroundKernelStore gks = null;
	
	
	public SimpleActivator(ConfigBundle config) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		gks = ((ReasonerFactory) config.getFactory(REASONER_KEY, REASONER_DEFAULT)).getReasoner(config);
		
	}
	
	@Override
	public void ingest(DataStore ds, Partition p, IngestModel m) {
		
	}

	@Override
	public void link(DataStore ds, Set<Partition> inPartitions, LinkModel m) {
		
	}

	@Override
	public GroundKernelStore ground(DataStore ds, Partition groundPartition) {
		return gks;
	}

}
