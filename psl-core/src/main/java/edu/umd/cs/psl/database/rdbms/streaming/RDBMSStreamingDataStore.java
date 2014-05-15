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

package edu.umd.cs.psl.database.rdbms.streaming;

import java.sql.Connection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.Stream;
import edu.umd.cs.psl.database.StreamingDataStore;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.driver.DatabaseDriver;

/**
 * @author jay
 *
 */
public class RDBMSStreamingDataStore  extends RDBMSDataStore implements
		StreamingDataStore {

	private final Set<RDBMSStream> openStreams;
	
	private RDBMSStreamingDataStoreMetadata metadata;
	/**
	 * @param dbDriver
	 * @param config
	 */
	public RDBMSStreamingDataStore(DatabaseDriver dbDriver,
			ConfigBundle config) {
		super(dbDriver, config);
		openStreams = new HashSet<RDBMSStream>();
	}

	@Override
	protected void initializeMetadata(Connection conn, String tblName){
		this.metadata = new RDBMSStreamingDataStoreMetadata(conn, tblName);
		metadata.createMetadataTable();
	}

	public Stream getNewStream(){
		int streamnum = getNextStream();
		Stream s = new RDBMSStream(streamnum,"AnonymousStream"+Integer.toString(streamnum), this);
		openStreams.add((RDBMSStream)s);
		return s;
	}
	
	private int getNextStream() {
		int maxStream = 0;
		maxStream = metadata.getMaxStream();
		return maxStream+1;
	}
	
	public Stream getStream(String streamName) {
		String idStr = metadata.getStreamIDByName(streamName);
		RDBMSStream s = null;
		if(idStr == null){
			s = new RDBMSStream(getNextStream(), streamName, this);
			metadata.addStream(s);
		} else {
			s = new RDBMSStream(Integer.parseInt(idStr),streamName, this);
		}
		openStreams.add(s);
		return s;
	}
	
	public void close() {
		if (!openStreams.isEmpty())
			throw new IllegalStateException("Cannot close data store when databases are still open!");
		super.close();
	}
	
	protected void closeStream(RDBMSStream s){
		if(openStreams.contains(s)){openStreams.remove(s);}
	}
	
	protected boolean addPartitionToStream(Stream s, Partition p){
		boolean success = metadata.addStreamPartition(s, p);
		return success;
	}
	
	protected Set<Partition> getPartitions(Stream s){
		return metadata.getStreamPartitions(s);
	}

	protected boolean removePartitionFromStream(Stream s, Partition p){
		return false;
	}
	
	protected boolean movePartition(Stream sSrc, Stream sDst, Partition p){
		return false;
	}
	
	protected Partition getStreamPartition(Stream s, String partitionName){
		Partition p = super.getPartition(s.getName()+"."+partitionName);
		addPartitionToStream(s,p);
		
		return p;
	
	}
}
