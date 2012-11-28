/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import com.healthmarketscience.sqlbuilder.QueryPreparer;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UpdateQuery;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.Attribute;
import edu.umd.cs.psl.model.argument.DoubleAttribute;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.IntegerAttribute;
import edu.umd.cs.psl.model.argument.StringAttribute;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.VariableTypeMap;
import edu.umd.cs.psl.model.atom.AtomCache;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * 
 * @author Eric Norris (enorris@cs.umd.edu)
 *
 */
public class RDBMSDatabase implements Database {
	private static final Logger log = LoggerFactory.getLogger(RDBMSDatabase.class);
	/**
	 * The backing data store that created this database.
	 */
	private final RDBMSDataStore parentDataStore;
	
	/**
	 * The connection to the JDBC database
	 */
	private final Connection dbConnection;
	
	/**
	 * The partition ID in which this database writes.
	 */
	protected final Partition writePartition;
	private final int writeID;
	
	/**
	 * The partition IDs that this database reads from.
	 */
	protected final Partition[] readPartitions;
	private final List<Integer> readIDs;
	
	/**
	 * Predicates that, for the purpose of this database, are closed.
	 */
	private final Set<StandardPredicate> closedPredicates;
	
	/** 
	 * Mapping from a predicate to its database handle.
	 */
	private final Map<Predicate, RDBMSPredicateHandle> predicateHandles;

	/**
	 * The atom cache for this database.
	 */
	private final AtomCache cache;
	
	/**
	 * The following map predicates to pre-compiled SQL statements.
	 */
	private final Map<Predicate, PreparedStatement> queryStatement;
	private final Map<Predicate, PreparedStatement> updateStatement;
	private final Map<Predicate, PreparedStatement> insertStatement;
	/**
	 * The following keeps track of statements in need of execution.
	 */
	private final Set<PreparedStatement> pendingStatements;
	private int pendingOperationCount;
	
	/*
	 * Keeps track of the open / closed status of this database.
	 */
	private boolean closed;

	/**
	 * The constructor for the RDBMSDatabase. Note: This assumes the parent
	 * {@link RDBMSDataStore} will register predicates with this database.
	 * @param parent
	 * @param con
	 * @param write
	 * @param reads
	 * @param closed
	 */
	public RDBMSDatabase(RDBMSDataStore parent, Connection con,
			Partition write, Partition[] reads, Set<StandardPredicate> closed) {
		// Store the connection / DataStore information
		this.parentDataStore = parent;
		this.dbConnection = con;
		
		// Store the partition this class has write access to
		this.writePartition = write;
		this.writeID = write.getID();
		
		// Store the partitions this class has read access to
		this.readPartitions = reads;
		this.readIDs = new ArrayList<Integer>(reads.length);
		for (int i = 0; i < reads.length; i ++)
			this.readIDs.add(reads[i].getID());
		if (!this.readIDs.contains(writeID))
			this.readIDs.add(writeID);
		
		// Add the set of predicates to treat as closed
		this.closedPredicates = new HashSet<StandardPredicate>();
		if (closed != null)
			this.closedPredicates.addAll(closed);
		
		// Initialize internal variables
		this.predicateHandles = new HashMap<Predicate, RDBMSPredicateHandle>();
		this.cache = new AtomCache(this);
		this.queryStatement = new HashMap<Predicate, PreparedStatement>();
		this.updateStatement = new HashMap<Predicate, PreparedStatement>();
		this.insertStatement = new HashMap<Predicate, PreparedStatement>();
		this.pendingStatements = new HashSet<PreparedStatement>();
		this.pendingOperationCount = 0;
		this.closed = false;
	}
	
	public void registerPredicate(RDBMSPredicateHandle ph) {
		if (predicateHandles.containsKey(ph.predicate()))
			throw new IllegalArgumentException("Predicate has already been registered!");
		predicateHandles.put(ph.predicate(), ph);

		// Create PreparedStatement for predicate
		createQueryStatement(ph);
		if (!closedPredicates.contains(ph.predicate())) {
			createUpdateStatement(ph);
			createInsertStatement(ph);
		}
	}
	
	private void createQueryStatement(RDBMSPredicateHandle ph) {
		SelectQuery q = new SelectQuery();
		QueryPreparer preparer = new QueryPreparer();
		QueryPreparer.MultiPlaceHolder placeHolder = preparer.getNewMultiPlaceHolder();
		
		q.addAllColumns().addCustomFromTable(ph.tableName());
		q.addCondition(new InCondition(new CustomSql(ph.partitionColumn()), readIDs));
		for (int i = 0; i< ph.argumentColumns().length; i++) {
			q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]), placeHolder));
		}
		
		try {
			PreparedStatement ps = dbConnection.prepareStatement(q.toString());
			queryStatement.put(ph.predicate(), ps);
		} catch (SQLException e) {
			throw new RuntimeException("Could not create prepared statement.", e);
		}
	}
	
	private void createUpdateStatement(RDBMSPredicateHandle ph) {
		UpdateQuery q = new UpdateQuery(ph.tableName());
		QueryPreparer preparer = new QueryPreparer();
		QueryPreparer.MultiPlaceHolder placeHolder = preparer.getNewMultiPlaceHolder();
		
		// First set placeholders for the arguments
		for (int i=0; i<ph.argumentColumns().length; i++) {
			q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]), placeHolder));
		}
		
		// Set the partition equal to the write partition
		q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.partitionColumn()), writeID ));
		
		// Set a placeholder for the value
		q.addCustomSetClause(ph.valueColumn(), placeHolder);
		
		// Set a placeholder for the confidence
		q.addCustomSetClause(ph.confidenceColumn(), placeHolder);
		
		try {
			PreparedStatement ps = dbConnection.prepareStatement(q.toString());
			updateStatement.put(ph.predicate(), ps);
		} catch (SQLException e) {
			throw new RuntimeException("Could not create prepared statement.", e);
		}
	}
	
	private void createInsertStatement(RDBMSPredicateHandle ph) {
		InsertQuery q = new InsertQuery(ph.tableName());
		QueryPreparer preparer = new QueryPreparer();
		QueryPreparer.MultiPlaceHolder placeHolder = preparer.getNewMultiPlaceHolder();
		
		// First set placeholders for the arguments
		for (int i=0; i<ph.argumentColumns().length; i++) {
			q.addCustomColumn(ph.argumentColumns()[i], placeHolder);
		}
		
		// Set the partition equal to the write partition
		q.addCustomColumn(ph.partitionColumn(), writeID);
		
		// Set a placeholder for the value
		q.addCustomColumn(ph.valueColumn(), placeHolder);
		
		// Set a placeholder for the confidence
		q.addCustomColumn(ph.confidenceColumn(), placeHolder);
		
		try {
			PreparedStatement ps = dbConnection.prepareStatement(q.toString());
			insertStatement.put(ph.predicate(), ps);
		} catch (SQLException e) {
			throw new RuntimeException("Could not create prepared statement.", e);
		}
	}
	
	/**
	 * Helper method for getting a predicate handle
	 * @param p	The predicate to lookup
	 * @return	The handle associated with the predicate
	 */
	protected RDBMSPredicateHandle getHandle(Predicate p) {
		RDBMSPredicateHandle ph = predicateHandles.get(p);
		if (ph == null)
			throw new IllegalArgumentException("Predicate not registered with database.");
		
		return ph;
	}
	
	private ResultSet queryDBForAtom(QueryAtom a) {
		if (closed)
			throw new IllegalStateException("Cannot query atom from closed database.");
		
		PreparedStatement ps = queryStatement.get(a.getPredicate());
		Term[] arguments = a.getArguments();
		try {
			for (int i = 0; i < arguments.length; i++) {
				int paramIndex = i + 1;
				Term argument = arguments[i];
				
				if (argument instanceof IntegerAttribute)
					ps.setInt(paramIndex, ((IntegerAttribute)argument).getValue());
				else if (argument instanceof DoubleAttribute)
					ps.setDouble(paramIndex, ((DoubleAttribute)argument).getValue());
				else if (argument instanceof StringAttribute)
					ps.setString(paramIndex, ((StringAttribute)argument).getValue());
				else if (argument instanceof RDBMSUniqueIntID)
					ps.setInt(paramIndex, ((RDBMSUniqueIntID)argument).getID());
				else if (argument instanceof RDBMSUniqueStringID)
					ps.setString(paramIndex, ((RDBMSUniqueStringID)argument).getID());
			}
			return ps.executeQuery();
		} catch (SQLException e) {
			throw new RuntimeException("Error querying DB for atom.", e);
		}
	}
	
	@Override
	public GroundAtom getAtom(Predicate p, GroundTerm... arguments) {
		/*
		 * First, check cache to see if the atom exists.
		 * Yes, return atom.
		 * No, continue.
		 * 
		 * Next, query database for atom.
		 * What partition is it in?
		 * Read?
		 * 		- Then instantiate as a persisted ObservedAtom
		 * Write?
		 * 		- Is the predicate closed?
		 * 		- Yes, instantiate as ObservedAtom.
		 * 		- No, instantiate as RandomVariableAtom.
		 * None?
		 * 		- Is the predicate standard?
		 * 		- Yes, is the predicate closed?
		 * 			- Yes, instantiate as ObservedAtom
		 * 			- No, instantiate as RandomVariableAtom
		 * 		- No, instantiate as ObservedAtom.
		 */
		if (p instanceof StandardPredicate)
			return getAtom((StandardPredicate)p, arguments);
		else if (p instanceof FunctionalPredicate)
			return getAtom((FunctionalPredicate)p, arguments);
		else
			throw new IllegalArgumentException("Unknown predicate type: " + p.getClass().toString());
	}

	private GroundAtom getAtom(StandardPredicate p, GroundTerm... arguments) {
		RDBMSPredicateHandle ph = getHandle(p);
		QueryAtom qAtom = new QueryAtom(p, arguments);
		GroundAtom result = cache.getCachedAtom(qAtom);
		if (result != null)
			return result;
		
		executePendingStatements();
		ResultSet rs = queryDBForAtom(qAtom);
		try {
			if (rs.next()) {
				double value = rs.getDouble(ph.valueColumn());
	    		double confidence = rs.getDouble(ph.confidenceColumn());
	    		int partition = rs.getInt(ph.partitionColumn());
	    		
	    		if (partition == writeID) {
	    			// Found in the write partition
	    			if (isClosed((StandardPredicate) p)) {
	    				// Predicate is closed, instantiate as ObservedAtom
	    				result = cache.instantiateObservedAtom(p, arguments, value, confidence);
	    			} else {
	    				// Predicate is open, instantiate as RandomVariableAtom
	    				result = cache.instantiateRandomVariableAtom((StandardPredicate) p, arguments, value, confidence);
	    			}
	    		} else {
	    			// Must be in a read partition, instantiate as ObservedAtom
	    			result = cache.instantiateObservedAtom(p, arguments, value, confidence);
	    		}
	    		if (rs.next())
	    			throw new IllegalStateException("Atom cannot exist in more than one partition.");
			}
			rs.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error analyzing results from atom query.", e);
		}
		
		if (result == null) {
			if (isClosed((StandardPredicate) p))
				result = cache.instantiateObservedAtom(p, arguments, 0.0, Double.NaN);
			else
				result = cache.instantiateRandomVariableAtom((StandardPredicate) p, arguments, 0.0, Double.NaN);
		}
		
		return result;
	}
	
	private GroundAtom getAtom(FunctionalPredicate p, GroundTerm... arguments) {
		QueryAtom qAtom = new QueryAtom(p, arguments);
		GroundAtom result = cache.getCachedAtom(qAtom);
		if (result != null)
			return result;
		
		// TODO should this be computed here? Or should this be a SQL function call?
		double value = p.computeValue(new ReadOnlyDatabase(this), arguments);
		return cache.instantiateObservedAtom(p, arguments, value, Double.NaN);
	}
	
	@Override
	public void commit(RandomVariableAtom atom) {
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		QueryAtom qAtom = new QueryAtom(atom.getPredicate(), atom.getArguments());
		
		boolean foundAtom = false;
		ResultSet rs = queryDBForAtom(qAtom);
		try {
			if (rs.next()) {
				// Found atom, only update it if it is in write partition
				foundAtom = true;
				int partition = rs.getInt(ph.partitionColumn());
				if (partition == writeID)
					updateAtom(atom);
			}
			rs.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error analyzing results from query.", e);
		}
		
		if (!foundAtom) {
			// Did not find atom, persist it now.
			insertAtom(atom);
		}
	}
	
	/**
	 * Helper method to fill in the fields of a PreparedStatement
	 * @param atom
	 */
	private void updateAtom(RandomVariableAtom atom) {
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		PreparedStatement update = updateStatement.get(atom.getPredicate());
		int sqlIndex = 1;
		
		Term[] arguments = atom.getArguments();
		try {
			// First, fill in arguments
			for (int i = 0; i < ph.argumentColumns().length; i++) {
				if (arguments[i] instanceof Attribute) {
					update.setObject(sqlIndex, ((Attribute)arguments[i]).getValue());
				} else if (arguments[i] instanceof UniqueID) {
					update.setObject(sqlIndex, ((UniqueID)arguments[i]).getInternalID());
				} else
					throw new IllegalArgumentException("Unknown argument type: " + arguments[i].getClass());
				sqlIndex++;
			}
			
			// Update the value for the atom
			update.setDouble(sqlIndex, atom.getValue());
			sqlIndex ++;
			
			// Update the confidence value
			update.setDouble(sqlIndex, atom.getConfidenceValue());
			
			// Batch the command for later execution
			update.addBatch();
			
			// Record keeping
			pendingOperationCount ++;
			if (!pendingStatements.contains(update))
				pendingStatements.add(update);
		} catch (SQLException e) {
			throw new RuntimeException("Error updating atom.", e);
		}
	}
	
	private void insertAtom(RandomVariableAtom atom) {
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		PreparedStatement insert = insertStatement.get(atom.getPredicate());
		int sqlIndex = 1;
		
		Term[] arguments = atom.getArguments();
		try {
			// First, fill in arguments
			for (int i = 0; i < ph.argumentColumns().length; i++) {
				if (arguments[i] instanceof Attribute) {
					insert.setObject(sqlIndex, ((Attribute)arguments[i]).getValue());
				} else if (arguments[i] instanceof UniqueID) {
					insert.setObject(sqlIndex, ((UniqueID)arguments[i]).getInternalID());
				} else
					throw new IllegalArgumentException("Unknown argument type: " + arguments[i].getClass());
				sqlIndex++;
			}
			
			// Update the value for the atom
			insert.setDouble(sqlIndex, atom.getValue());
			sqlIndex ++;
			
			// Update the confidence value
			insert.setDouble(sqlIndex, atom.getConfidenceValue());
			
			// Batch the command for later execution
			insert.addBatch();
			
			// Record keeping
			pendingOperationCount ++;
			if (!pendingStatements.contains(insert))
				pendingStatements.add(insert);
		} catch (SQLException e) {
			throw new RuntimeException("Error inserting atom.", e);
		}
	}

	private void executePendingStatements() {
		log.trace("Executing a batch of {} statements.", pendingOperationCount);
		int success = 0;

		try {
			for (PreparedStatement ps : pendingStatements) {
				int[] changes = ps.executeBatch();
				for (int change : changes)
					success += change;
			}
			if (success != pendingOperationCount)
				throw new RuntimeException("Return code indicates that not all " +
						"statements were executed successfully. [code: " + 
						success + ", pending: " + pendingOperationCount + "]");
			pendingOperationCount = 0;
			pendingStatements.clear();
		} catch (SQLException e) {
			throw new RuntimeException("Error when executing batched statements.", e);
		}
	}
	
	@Override
	public ResultList executeQuery(DatabaseQuery query) {
		if (closed)
			throw new IllegalStateException("Cannot perform query on database that was closed.");
		
		executePendingStatements();
		
		Formula f = query.getFormula();
		VariableAssignment partialGrounding = query.getPartialGrounding();
		Set<Variable> projectTo = query.getProjectionSubset();
		
		VariableTypeMap varTypes = f.collectVariables(new VariableTypeMap());
		if (projectTo.size() == 0) {
			projectTo.addAll(varTypes.getVariables());
			projectTo.removeAll(partialGrounding.getVariables());
		}
		
		// Construct query from formula
		Formula2SQL sqler = new Formula2SQL(partialGrounding, projectTo, this);
		String queryString = sqler.getSQL(f);
		log.trace(queryString);
		
		// Create and initialize ResultList
		int i = 0;
		RDBMSResultList results = new RDBMSResultList(projectTo.size());
		for (Variable var : projectTo) {
			results.setVariable(var, i);
			i ++;
		}
		
		try  {
			Statement stmt = dbConnection.createStatement();
			try {
				ResultSet rs = stmt.executeQuery(queryString);
				try {
					while (rs.next()) {
						GroundTerm[] res = new GroundTerm[projectTo.size()];
						i = 0;
						for (Variable var : projectTo) {
							if (partialGrounding.hasVariable(var)) {
								res[i] = partialGrounding.getVariable(var);
							} else {
								ArgumentType type = varTypes.getType(var);
								switch (type) {
								case Double:
									res[i] = new DoubleAttribute(rs.getDouble(var.getName()));
									break;
								case Integer:
									res[i] = new IntegerAttribute(rs.getInt(var.getName()));
									break;
								case String:
									res[i] = new StringAttribute(rs.getString(var.getName()));
									break;
								case UniqueID:
									res[i] = getUniqueID(rs.getObject(var.getName()));
									break;
								default:
									throw new IllegalArgumentException("Unknown argument type: " + type);
								}
							}
							i ++;
						}
						results.addResult(res);
					}
				} finally {
					rs.close();
				}
			} finally {
				stmt.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error executing database query.", e);
		}
		log.trace("Number of results: {}",results.size());
		return results;
	}

	@Override
	public boolean isClosed(StandardPredicate predicate) {
		return closedPredicates.contains(predicate);
	}

	@Override
	public UniqueID getUniqueID(Object key) {
		return parentDataStore.getUniqueID(key);
	}

	@Override
	public DataStore getDataStore() {
		return parentDataStore;
	}

	@Override
	public AtomCache getAtomCache() {
		return cache;
	}

	@Override
	public void close() {
		if (closed)
			throw new IllegalStateException("Cannot closed database after it has been closed.");
		
		executePendingStatements();
		parentDataStore.releasePartitions(this);
		closed = true;
		
		// Close all prepared statements
		try {
			for (PreparedStatement ps : queryStatement.values())
				ps.close();
			for (PreparedStatement ps : updateStatement.values())
				ps.close();
			for (PreparedStatement ps : insertStatement.values())
				ps.close();
		} catch (SQLException e) {
			throw new RuntimeException("Error closing prepared statements.", e);
		}
	}
}
