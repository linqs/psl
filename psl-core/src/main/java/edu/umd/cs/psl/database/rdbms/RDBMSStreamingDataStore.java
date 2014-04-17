/**
 * This file is part of the PSL software.
 * Copyright 2011-2014 University of Maryland
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

package edu.umd.cs.psl.database.rdbms;

import java.util.Set;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Stream;
import edu.umd.cs.psl.database.StreamingDataStore;
import edu.umd.cs.psl.database.rdbms.driver.DatabaseDriver;

/**
 * @author jay
 *
 */
public class RDBMSStreamingDataStore  extends RDBMSDataStore implements
		StreamingDataStore {

	/**
	 * @param dbDriver
	 * @param config
	 */
	public RDBMSStreamingDataStore(DatabaseDriver dbDriver,
			ConfigBundle config) {
		super(dbDriver, config);
	}


	public Stream getNewStream(){
		int streamnum = getNextStream();
		return new RDBMSStream(streamnum,"AnonymousStream"+Integer.toString(streamnum));
	}
	
	private int getNextStream() {
		int maxStream = 0;
		maxStream = metadata.getMaxStream();
		return maxStream+1;
	}
	
	public Stream getStream(String streamName) {
		RDBMSStream s= metadata.getStreamByName(streamName);
		if(s == null){
			s = new RDBMSStream(getNextStream(), streamName);
			metadata.addStream(s);
		}		
		return s;
	}
}
