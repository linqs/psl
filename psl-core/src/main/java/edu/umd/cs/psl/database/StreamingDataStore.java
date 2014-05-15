package edu.umd.cs.psl.database;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.rdbms.driver.DatabaseDriver;

public interface StreamingDataStore extends DataStore {
	public Stream getStream(String streamName);
	public Stream getNewStream();
}
