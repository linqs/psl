package edu.umd.cs.psl.database.rdbms.streaming;

import java.util.regex.Pattern;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.driver.DatabaseDriver;
import edu.umd.cs.psl.model.predicate.Predicate;

public class RDBMSConcurrentStreamingDataStore extends RDBMSDataStore {
	protected RDBMSStream stream = null;

	public RDBMSConcurrentStreamingDataStore(DatabaseDriver dbDriver, ConfigBundle config, String streamName){
		super(dbDriver, config); } }
/*		this.stream = new RDBMSStream(this.metadata.getMaxStream(), streamName);
		metadata.addStream(stream);
	}
	@Override
	protected Pattern getPredicateTablePattern(){ return Pattern.compile("STREAM_(\\w+)_PREDICATE"); }
	
	@Override
	protected RDBMSPredicateInfo getDefaultPredicateDBInfo(Predicate predicate) {
		String[] argNames = new String[predicate.getArity()];
		for (int i = 0; i < argNames.length; i ++)
			argNames[i] = predicate.getArgumentType(i).getName() + "_" + i;
		String tablePrefix = this.metadata.getStreamTable(stream);
		if (tablePrefix == null)
			return null; 
		return new RDBMSPredicateInfo(predicate, argNames, tablePrefix+"STREAM_"+predicate.getName(),
				valueColumn, confidenceColumn, partitionColumn);
	}
	
	
}
*/