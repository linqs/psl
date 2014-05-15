package edu.umd.cs.psl.database.rdbms.streaming;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.Stream;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStoreMetadata;

public class RDBMSStreamingDataStoreMetadata extends RDBMSDataStoreMetadata {
	private static final Logger log = LoggerFactory.getLogger(RDBMSStreamingDataStoreMetadata.class);
	private HashMap<String,Integer> streamNames;
	private String streamingMDTableName; 

	public RDBMSStreamingDataStoreMetadata(Connection conn, String mdTableName) {
		super(conn, mdTableName);
		streamingMDTableName = super.getMetadataTableName() + "_streamPartitions";
		streamNames = new HashMap<String, Integer>();
	}
	
	
	public boolean checkIfMetadataTableExists(){
		boolean exists = super.checkIfMetadataTableExists();
		try {
			ResultSet rs = getConnection().getMetaData().getTables(null, null, streamingMDTableName, null);
			if(exists && rs.next()) { exists = true;}
		} catch (Exception e) {
			log.error(e.getMessage());
		}
		return exists;
	}
	
	public void createMetadataTable(){
		if(checkIfMetadataTableExists()) {return;}
		super.createMetadataTable();
		if(checkIfMetadataTableExists()) {return;}
		try {
			PreparedStatement stmt = getConnection().prepareStatement("CREATE TABLE "+streamingMDTableName+" (namespace VARCHAR(20), keytype VARCHAR(20), key VARCHAR(255), value VARCHAR(255), PRIMARY KEY(namespace,keytype,key,value))");
			stmt.execute();
		} catch (Exception e) { 
			log.error(e.getMessage());
		}
	}
	
	
	protected String[] getValues(String mdTableName, String space, String type, String key){
		ArrayList<String> retStrings = new ArrayList<String>();
		try {
			PreparedStatement stmt = super.getConnection().prepareStatement("SELECT value from "+mdTableName+" WHERE namespace = ? AND keytype = ? AND key = ?");
			stmt.setString(1, space);
			stmt.setString(2, type);
			stmt.setString(3, key);
			stmt.execute();
			ResultSet rs = stmt.getResultSet();
			while(rs.next()){
				retStrings.add(rs.getString(1));
			}
		} catch (Exception e) {
			log.error(e.getMessage());
			return null;
		}
		return (String[]) retStrings.toArray();
	}

	/**** Stream-related functions ****/
	protected String getStreamIDByName(String name){
		String idStr = getValue(super.getMetadataTableName(),"Stream","name",name);
		return idStr;
	}
	
	protected boolean addStream(Stream s){
		boolean success = false;
		if(getStreamIDByName(s.getName())!=null){
			log.error("Stream named"+s.getName()+" already exists");
		} else {
			success = super.addRow(super.getMetadataTableName(),"Stream","name",s.getName(),Integer.toString(s.getID()));
			streamNames.put(s.getName(), s.getID());			
		}
		return success;
	}
	
	protected boolean removeStream(Stream s){
		boolean success = false;
		success = removeRow(super.getMetadataTableName(),"Stream","name",s.getName());
		//TODO: possibly Remove the partitions associated with the stream!
		if(success)
			streamNames.remove(s.getName());
		return success;	
	}
	
	protected boolean addStreamPartition(Stream s, Partition partition){
		boolean success = false;
		success = addRow(streamingMDTableName,"Stream","partition",s.getName(),partition.getName());
		return success;
	}
	
	protected Set<Partition> getStreamPartitions(Stream s){
		HashSet<Partition> partitions = new HashSet<Partition>();
		String[] partStrings = getValues(streamingMDTableName,"Stream", "partition", s.getName());
		for(String pStr : partStrings){
			Partition p = getPartitionByName(pStr);
			if(p == null){return null;}
			partitions.add(p);
		}
		return partitions;
	}
	
	protected int getMaxStream(){
		int max = 0;
		try{
			PreparedStatement stmt = super.getConnection().prepareStatement("SELECT MAX(CAST(value as INT)) from "+super.getMetadataTableName()+" WHERE namespace = 'Stream' AND keytype = 'name'");
			stmt.execute();
			ResultSet rs = stmt.getResultSet();
			if(rs.next()){
				max = Integer.parseInt(rs.getString(1));
			}
		} catch (Exception e) {
			log.error(e.getMessage());
			return 0;
		}		
		return max;
	}
	
/*
 	public void loadStreamNames(){
		Map<String,String> vals = getAllValuesByType(super.getMetadataTableName(),"Stream","name");
		if(vals!=null){
			for(String name : vals.keySet()){
				streamNames.put(name,Integer.parseInt(vals.get(name)));
			}
		}
	}
 
 	public String getStreamTable(RDBMSStream s){
		return getValue(super.getMetadataTableName(),"Stream","table",s.getName());
	}

	public boolean addStreamTable(RDBMSStream s, String tableName){
		boolean success = false;
		if(getStreamTable(s)==null){
			success = addRow(super.getMetadataTableName(),"Stream","table",s.getName(),tableName);
		}
		return success;
	}
*/	

	
}
