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
package edu.umd.cs.psl.database.RDBMS;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UpdateQuery;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseEventObserver;
import edu.umd.cs.psl.database.PSLValue;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.PredicatePosition;
import edu.umd.cs.psl.database.ResultAtom;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.database.ResultListValues;
import edu.umd.cs.psl.database.UniqueID;
import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.argument.ArgumentFactory;
import edu.umd.cs.psl.model.argument.Attribute;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;
import edu.umd.cs.psl.model.argument.type.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.TemplateAtom;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.model.predicate.Predicate;

public class RDBMSDatabase implements Database {
	
	private static final boolean defaultisPriorInitialized = true;
	private static final boolean defaultPersistDefaultRVValues = true;
	
	private static final Logger log = LoggerFactory.getLogger(RDBMSDatabase.class);

	/**
	 * Connection to the JDBC database
	 */
	private final Connection db;
	/**
	 * RDBMSPredicateHandle for each predicate defined in this database. The RDBMSPredicateHandle
	 * define how to map predicates onto relational tables.
	 */
	private final Map<Predicate,RDBMSPredicateHandle> predicateHandles;
	
	/**
	 * The partition ID in which this database instance writes. It is
	 * assumed that each database has a unique writeID.
	 */
	private final int writeID;
	private final Partition writePartition;
	/**
	 * The partition IDs from which this database instance reads the data.
	 * These partition IDs can overlap with other database instances.
	 */
	private final Partition[] readPartitions;
	/**
	 * The readIDs are the partition and write IDs of this database instance.
	 */
	private final List<Integer> readIDs;
	
	/**
	 * This variable defines whether the database instance has been initialized
	 * at some prior point, i.e., whether the writeID partition contains any atom data
	 * from previous reasoning.
	 */
	private final boolean isPriorInitialized;
	/**
	 * This flag specifies whether the database instance also persists atoms representing random variables (RV)
	 * whose values are the default values or whether to ignore those.
	 */
	private final boolean persistDefaultRVValues;
	
	/**
	 * The parent RDBMSDataStore from which this database instance was derived.
	 */
	private final RDBMSDataStore parentDataStore;
	
	/**
	 * An optional AtomEventObserver to which the database relays changes to its data.
	 * May be NULL.
	 */
	private DatabaseEventObserver dbObserver;
	
	/**
	 * Set of entities for a given {@link ArgumentType} or null/empty if not yet initialized
	 * TODO: This data structure needs to be updated when facts in the database are changed and
	 * it has been initialized.
	 */
	private final SetMultimap<ArgumentType,Entity> allEntities;
	
	private final ArgumentFactory argFactory;
	
	public RDBMSDatabase(RDBMSDataStore parent, Connection con, Partition write, Partition[] reads) {
		parentDataStore = parent;
		db = con;
		writePartition = write;
		writeID = writePartition.getID();
		readPartitions = reads;
		readIDs = new ArrayList<Integer>(readPartitions.length+1);
		for (int i=0;i<readPartitions.length;i++) readIDs.add(readPartitions[i].getID());
		if (!readIDs.contains(writeID)) readIDs.add(writeID);
		predicateHandles = new HashMap<Predicate,RDBMSPredicateHandle>();
		argFactory = new ArgumentFactory();
		allEntities = HashMultimap.create();
		dbObserver=null;
		isPriorInitialized = defaultisPriorInitialized;
		persistDefaultRVValues = defaultPersistDefaultRVValues;
		registerFunctionAlias();
	}

	@Override
	public void deregisterDatabaseEventObserver(DatabaseEventObserver atomEvents) {
		if (dbObserver!=atomEvents) throw new IllegalStateException("Specified observer has not been registered for this database!");
		dbObserver=null;
	}
	
	@Override
	public void registerDatabaseEventObserver(DatabaseEventObserver atomEvents) {
		if (dbObserver!=null) throw new IllegalStateException("An observer has already been registered for this database!");
		dbObserver=atomEvents;
	}

	
	public void registerPredicate(RDBMSPredicateHandle ph) {
		if (predicateHandles.containsKey(ph.predicate())) throw new IllegalArgumentException("Predicate has already been registered!");
		predicateHandles.put(ph.predicate(), ph);
	}

	
	RDBMSPredicateHandle getHandle(Predicate p) {
		RDBMSPredicateHandle ph = predicateHandles.get(p);
		if (ph==null) throw new IllegalArgumentException("Predicate has not been registered: " + p);
		return ph;
	}
	
	Collection<Integer> getReadIDs() {
		return readIDs;
	}

	@Override
	public boolean isClosed(Predicate p) {
		return getHandle(p).isClosed();
	}
	
	@Override
	public void persist(Atom atom) {
		assert atom.isGround();
		assert atom.isConsidered() || atom.isActive() : atom;
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		Preconditions.checkArgument(!ph.isClosed(),"Cannot write atom for closed predicate");
		Preconditions.checkArgument(ph.hasSoftValues(),"Cannot set truth value since respective column does not exist!");

		
		SelectQuery q = queryAtom(atom.getPredicate(),atom.getArguments());
		q.addCondition(new InCondition(new CustomSql(ph.partitionColumn()),readIDs));

		String query = q.validate().toString();
		try {
			Statement stmt = db.createStatement();
			try {
			    ResultSet rs = stmt.executeQuery(query);
			    try {
				    if (rs.last()) {
				    	assert rs.getRow()==1;
				    	//Only update if the atom is in the write partition
				    	if (rs.getInt(ph.partitionColumn())==writeID)
				    		updateAtom(atom);
				    } else {
				    	insertAtom(atom);
				    }
			    } finally {
			        rs.close();
			    }
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			log.error("SQL error: {}",e.getMessage());
			throw new AssertionError(e);
		}	
	}
	
	private PSLValue getPersistencePSLValue(Atom atom) {
		switch(atom.getStatus()) {
		case UnconsideredCertainty:
		case ConsideredCertainty:
		case ActiveCertainty:
			if (atom.hasNonDefaultValues()) {
				return PSLValue.Fact;
			} else {
				return PSLValue.DefaultFact;
			}
		case UnconsideredRV:
		case ConsideredRV:
			return PSLValue.DefaultRV;
		case ActiveRV:
			return  PSLValue.ActiveRV;
		default: throw new IllegalStateException("Atom has an illegal status: " + atom);
		}
	}
	
	private void insertAtom(Atom atom) {
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		//Build SQL statement
		InsertQuery q = new InsertQuery(ph.tableName());
		Term[] arguments = atom.getArguments();
		for (int i=0;i<ph.argumentColumns().length;i++) {
			assert arguments[i] instanceof GroundTerm;
			if (arguments[i] instanceof Attribute) {
				q.addCustomColumn(ph.argumentColumns()[i], ((Attribute)arguments[i]).getAttribute());
			} else { //Entity
				Entity e = (Entity)arguments[i];
				q.addCustomColumn(ph.argumentColumns()[i], e.getID().getDBID());
			}
		}
		q.addCustomColumn(ph.partitionColumn(), writeID);
		PSLValue pslvalue = getPersistencePSLValue(atom);
		q.addCustomColumn(ph.pslColumn(), pslvalue.getIntValue());
		
		assert ph.valueColumns().length==ph.confidenceColumns().length;
		assert ph.valueColumns().length==atom.getPredicate().getNumberOfValues();
		for (int i=0;i<ph.valueColumns().length;i++) {
			q.addCustomColumn(ph.valueColumns()[i], atom.getSoftValue(i));
		}
		for (int i=0;i<ph.confidenceColumns().length;i++) {
			double conf = atom.getConfidenceValue(i);
			if (ConfidenceValues.isDefaultConfidence(conf)) continue;
			assert ConfidenceValues.isValidValue(conf);
			q.addCustomColumn(ph.confidenceColumns()[i], conf);
		}
		String query = q.validate().toString();
		log.trace(query);
		try {
			Statement stmt = db.createStatement();
			try {
			    int code = stmt.executeUpdate(query);
			    if (code != 1) throw new AssertionError("Return code indicates that insertion failed: " + code);
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			log.error("SQL error: {}",e.getMessage());
			throw new AssertionError(e);
		}
	}
	
	private void updateAtom(Atom atom) {
		RDBMSPredicateHandle ph = getHandle(atom.getPredicate());
		//Build SQL statement
		UpdateQuery q = new UpdateQuery(ph.tableName());
		Term[] arguments = atom.getArguments();
		for (int i=0;i<ph.argumentColumns().length;i++) {
			assert arguments[i] instanceof GroundTerm;
			if (arguments[i] instanceof Attribute) {
				q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]),  ((Attribute)arguments[i]).getAttribute() ));
			} else { //Entity
				Entity e = (Entity)arguments[i];
				q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]),  e.getID().getDBID() ));
			}
		}
		q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.partitionColumn()), writeID ));
		PSLValue pslvalue = getPersistencePSLValue(atom);
		q.addCustomSetClause(ph.pslColumn(), pslvalue.getIntValue());
		
		assert ph.valueColumns().length==ph.confidenceColumns().length;
		assert ph.valueColumns().length==atom.getPredicate().getNumberOfValues();
		for (int i=0;i<ph.valueColumns().length;i++) {
			q.addCustomSetClause(ph.valueColumns()[i], atom.getSoftValue(i));
		}
		for (int i=0;i<ph.confidenceColumns().length;i++) {
			double conf = atom.getConfidenceValue(i);
			if (ConfidenceValues.isDefaultConfidence(conf)) {
				q.addCustomSetClause(ph.confidenceColumns()[i], null);
			} else {
				assert ConfidenceValues.isValidValue(conf);
				q.addCustomSetClause(ph.confidenceColumns()[i], conf);
			}
		}
		
		String query = q.validate().toString();
		log.trace(query);
		try {
			Statement stmt = db.createStatement();
			try {
			    int code = stmt.executeUpdate(query);
			    if (code != 1) throw new AssertionError("Return code indicates that insertion failed: " + code);
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			log.error("SQL error: {}",e.getMessage());
			throw new AssertionError(e);
		}
	}

	
	private SelectQuery queryAtom(Predicate p, Term[] arguments) {
		RDBMSPredicateHandle ph = getHandle(p);
		
		SelectQuery q = new SelectQuery();
		q.addAllColumns().addCustomFromTable(ph.tableName());
		for (int i=0;i<ph.argumentColumns().length;i++) {
			if (arguments[i]==null) continue;
			assert arguments[i] instanceof GroundTerm;
			if (arguments[i] instanceof Attribute) {
				q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]),  ((Attribute)arguments[i]).getAttribute() ));
			} else { //Entity
				Entity e = (Entity)arguments[i];
				q.addCondition(BinaryCondition.equalTo(new CustomSql(ph.argumentColumns()[i]),  e.getID().getDBID() ));
			}
		}
		return q;
	}
	
	@Override
	public ResultAtom getAtom(Predicate p, GroundTerm[] arguments) {
		RDBMSPredicateHandle ph = getHandle(p);
		boolean notFound = false;
		if (!isPriorInitialized && !ph.isClosed()) notFound=true;
		ResultAtom atom=null;
		
		if (!notFound) {
			SelectQuery q = queryAtom(p,arguments);
			q.addCondition(new InCondition(new CustomSql(ph.partitionColumn()),readIDs));
			String query = q.validate().toString();
			log.trace(query);
	
			try {
				Statement stmt = db.createStatement();
				try {
				    ResultSet rs = stmt.executeQuery(query);
				    try {
				    	if (rs.next()) { //Exists in database
				    		notFound=false;
				    		double[] values = new double[p.getNumberOfValues()];
				    		double[] confidences = new double[p.getNumberOfValues()];
				    		for (int i=0;i<ph.valueColumns().length;i++) {
				    			values[i]=rs.getDouble(ph.valueColumns()[i]);
				    		}
				    		for (int i=0;i<ph.confidenceColumns().length;i++) {
				    			double conf = rs.getDouble(ph.confidenceColumns()[i]);
				    			if (ConfidenceValues.isValidValue(conf)) 
				    				confidences[i]=conf;
				    			else
				    				confidences[i]=ConfidenceValues.defaultConfidence;
				    		}
				    		
				    		if (ph.isClosed()) {
				    			if (!ph.hasSoftValues()) {
				    				values = p.getStandardValues();
				    			}
				    			if (!ph.hasConfidenceValues()) {
				    				confidences = ConfidenceValues.getMaxConfidence(p.getNumberOfValues());
				    			}
				    			atom = new ResultAtom(values,confidences,ResultAtom.Status.FACT);
				    		} else {
				    			assert ph.hasSoftValues();
				    			assert ph.hasConfidenceValues();
				    			PSLValue pslval = PSLValue.parse(rs.getInt(ph.pslColumn()));
				    			atom = new ResultAtom(values,confidences,ResultAtom.Status.RV);
				    			switch(pslval) {
				    			case Fact:
				    				atom.setStatus(ResultAtom.Status.CERTAINTY);
				    				break;
				    			case DefaultFact:
				    				atom.setStatus(ResultAtom.Status.CERTAINTY);
				    				break;
				    			case DefaultRV:
				    				atom.setStatus(ResultAtom.Status.RV);
				    				break;
				    			case ActiveRV:
				    				atom.setStatus(ResultAtom.Status.RV);
				    				break;
				    			default: throw new IllegalArgumentException("Unknown psl value: " + pslval); 
				    			}
				    		}
				    	} else { //Not found
				    		notFound = true;
				    	}
	
				    } finally {
				        rs.close();
				    }
				} finally {
				    stmt.close();
				}
			} catch(SQLException e) {
				log.error("SQL error: {}",e.getMessage());
				throw new AssertionError(e);
			}
		}
		if (notFound) {
			assert atom==null;
    		if (ph.isClosed()) {
    			atom = new ResultAtom(p.getDefaultValues(),ConfidenceValues.getMaxConfidence(p.getNumberOfValues()),ResultAtom.Status.FACT);
    		} else {
    			atom = new ResultAtom(ResultAtom.Status.RV);
    		}
		}
		assert atom!=null;
		return atom;

	}
	
	@Override
	public Map<PredicatePosition,ResultListValues> getAllFactsWith(GroundTerm e) {
		
		Map<PredicatePosition,ResultListValues> result = new HashMap<PredicatePosition,ResultListValues>();
		
		for (RDBMSPredicateHandle ph : predicateHandles.values()) {
			if (ph.isClosed()) {
				Predicate p = ph.predicate();
				for (int pos=0;pos<p.getArity();pos++) {
					if (e.getType().isSubTypeOf(p.getArgumentType(pos))) {
						PredicatePosition pp = new PredicatePosition(p,pos);
						GroundTerm[] args = new GroundTerm[p.getArity()];
						args[pos]=e;
						result.put(pp, getFacts(p,args));
					}
				}
			}
		}
		return result;
	}
	
	
	@SuppressWarnings("static-access")
	@Override
	public ResultListValues getFacts(Predicate p, Term[] arguments) {
		RDBMSPredicateHandle ph = getHandle(p);
		Preconditions.checkArgument(ph.isClosed());
		Preconditions.checkArgument(arguments.length==p.getArity());
		SelectQuery q = queryAtom(p,arguments);
		q.addCondition(new InCondition(new CustomSql(ph.partitionColumn()),readIDs));
		String query = q.validate().toString();

		int arity=0;
		for (int i=0;i<p.getArity();i++) if (arguments[i]==null) arity++;
		

		RDBMSResultListValues results = new RDBMSResultListValues(arity);
		log.trace(query);
		try {
			Statement stmt = db.createStatement();
			try {
			    ResultSet rs = stmt.executeQuery(query);
			    ResultSetMetaData rsmd = rs.getMetaData();
			    boolean[] isInteger = new boolean[arity];
				String[] cols2retrieve = new String[arity];
				ArgumentType[] types = new ArgumentType[arity];
				int pos = 0;
				for (int i=0;i<p.getArity();i++) {
					if (arguments[i]==null) {
						cols2retrieve[pos]=ph.argumentColumns()[i];
						types[pos]=p.getArgumentType(i);
						isInteger[pos]=rsmd.getColumnType(i+1)==java.sql.Types.INTEGER;
						pos++;
					}
				}
			    
			    try {
			    	while (rs.next()) { //Exists in database
			    		GroundTerm[] res = new GroundTerm[arity];
			    		for (int i=0;i<arity;i++) {
			    			String col = cols2retrieve[i];

			    			ArgumentType type = types[i];
			    			if (type==ArgumentTypes.Number) {
			    				res[i] = argFactory.getAttribute(rs.getDouble(col));
			    			} else if (type==ArgumentTypes.Text) {
			    				res[i] = argFactory.getAttribute(rs.getString(col));
			    			} else if (type.isEntity()) {
			    				if (!isInteger[i]) {
			    					res[i] = argFactory.getEntity(new RDBMSUniqueStringID(rs.getString(col)), type);
			    				} else {
			    					res[i] = argFactory.getEntity(new RDBMSUniqueIntID(rs.getInt(col)), type );
			    				}
			    			} else throw new IllegalArgumentException("Unsupported type encountered: " + type);

			    		}
 
			    		double[] values = new double[p.getNumberOfValues()];
			    		for (int i=0;i<ph.valueColumns().length;i++) {
			    			values[i]=rs.getDouble(ph.valueColumns()[i]);
			    		}
			    		
		    			if (!ph.hasSoftValues()) {
		    				values = p.getStandardValues();
		    			}
			    		results.addResult(res,values);
			    	}
			    } finally {
			        rs.close();
			    }
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			log.error("SQL error: {}",e.getMessage());
			e.printStackTrace();
			throw new AssertionError(e);
		}
		return results;
	}
	
	@Override
	public ResultList query(Formula f, VariableAssignment partialGrounding) {
		return query(f,partialGrounding, null);
	}

	@Override
	public ResultList query(Formula f, List<Variable> projectTo) {
		return query(f,new VariableAssignment(0), projectTo);
	}

	@Override
	public ResultList query(Formula f) {
		return query(f,new VariableAssignment(0), null);
	}

	@SuppressWarnings("static-access")
	@Override
	public ResultList query(Formula f, VariableAssignment partialGrounding, List<Variable> projectTo) {
		assert f!=null;
		assert partialGrounding!=null;
		
		
		VariableTypeMap varTypes = f.getVariables(new VariableTypeMap());
		if (projectTo==null) {
			projectTo = new ArrayList<Variable>(varTypes.getVariables());
			projectTo.removeAll(partialGrounding.getVariables());
		}
		
		Formula2SQL sqler = new Formula2SQL(partialGrounding, projectTo,this);
		String query = sqler.getSQL(f);
		log.trace(query);
		RDBMSResultList results = new RDBMSResultList(projectTo.size());
		for (int i=0;i<projectTo.size();i++) results.setVariable(projectTo.get(i), i);
		
		try {
			Statement stmt = db.createStatement();
			try {
			    ResultSet rs = stmt.executeQuery(query);
			    ResultSetMetaData rsmd = rs.getMetaData();
			    try {
			    	while (rs.next()) {
			    		GroundTerm[] res = new GroundTerm[projectTo.size()];
			    		for (int i=0;i<projectTo.size();i++) {
			    			Variable var = projectTo.get(i);
			    			if (partialGrounding.hasVariable(var)) {
			    				res[i]=partialGrounding.getVariable(var);
			    			} else {
				    			ArgumentType type = varTypes.getType(var);
				    			if (type==ArgumentTypes.Number) {
				    				res[i] = argFactory.getAttribute(rs.getDouble(var.getName()));
				    			} else if (type==ArgumentTypes.Text) {
				    				res[i] = argFactory.getAttribute(rs.getString(var.getName()));
				    			} else if (type.isEntity()) {
				    				int col = rs.findColumn(var.getName());
				    				if (rsmd.getColumnType(col)==java.sql.Types.VARCHAR) {
				    					res[i] = argFactory.getEntity(new RDBMSUniqueStringID(rs.getString(var.getName())), type);
				    				} else {
				    					assert rsmd.getColumnType(col)==java.sql.Types.INTEGER;
				    					res[i] = argFactory.getEntity(new RDBMSUniqueIntID(rs.getInt(var.getName())), type );
				    				}
				    			} else throw new IllegalArgumentException("Unsupported type encountered: " + type);
			    			}
			    		}
			    		results.addResult(res);
			    	}
			    } finally {
			        rs.close();
			    }
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			log.error("SQL error: {}",e.getMessage());
			throw new AssertionError(e);
		}
		log.trace("Number of results: {}",results.size());
		return results;
	}

	@Override
	public Entity getEntity(Object entity, ArgumentType type) {
		return argFactory.getEntity(getUniqueID(entity),type);
	}
	
	private void initializeEntities(ArgumentType type) {
		if (allEntities.containsKey(type)) return;
		Variable queryvar = new Variable("A");
		for (Predicate p : predicateHandles.keySet()) {
			for (int i=0;i<p.getArity();i++) {
				if (p.getArgumentType(i).equals(type)) {
					Term[] args = new Term[p.getArity()];
					for (int j=0;j<p.getArity();j++) {
						args[j]=new Variable("X"+j);
					}
					args[i]=queryvar;
					ResultList res = query(new TemplateAtom(p,args), ImmutableList.of(queryvar));
					for (int k=0;k<res.size();k++) {
						assert res.get(k).length==1;
						allEntities.put(type, (Entity)res.get(k)[0]);
					}
				}
			}
		}

	}

	@Override
	public Set<Entity> getEntities(ArgumentType type) {
		initializeEntities(type);
		assert allEntities.containsKey(type);
		return Collections.unmodifiableSet(allEntities.get(type));
	}
	
	@Override
	public int getNumEntities(ArgumentType type) {
		initializeEntities(type);
		assert allEntities.containsKey(type);
		return allEntities.get(type).size();
	}
	
	public static UniqueID getUniqueID(Object entity) {
		UniqueID id;
		if (entity instanceof String) {
			id = new RDBMSUniqueStringID((String)entity) ;
		} else if (entity instanceof Integer) {
			id = new RDBMSUniqueIntID((Integer)entity);
		} else {
			throw new IllegalArgumentException("Unsupported entity type: " + entity);
		}
		return id;
	}

	@Override
	public void close() {
		parentDataStore.closeDatabase(this,writePartition,readPartitions);
		allEntities.clear();
		dbObserver=null;
	}
	
		
	/*
	 * ########### Handling function calls
	 */
	
	private void registerFunctionAlias() {
		try {
			Statement stmt = db.createStatement();
			try {
			    stmt.executeUpdate("CREATE ALIAS IF NOT EXISTS "+aliasFunctionName+" FOR \""+getClass().getCanonicalName()+".registeredExternalFunctionCall\" ");
			} finally {
			    stmt.close();
			}
		} catch(SQLException e) {
			log.error("SQL error: {}",e.getMessage());
			throw new AssertionError(e);
		}
	}
	
	public static final String aliasFunctionName = "extFunctionCall";
	
	private static final BiMap<ExternalFunction, String> externalFunctions = HashBiMap.create();
	private static int externalFunctionCounter=0;
	
	public static final String getSimilarityFunctionID(ExternalFunction extFun) {
		if (externalFunctions.containsKey(extFun)) {
			return externalFunctions.get(extFun);
		} else {
			String id = "extFun"+(externalFunctionCounter++);
			externalFunctions.put(extFun, id);
			return id;
		}
	}
	
	public static final double registeredExternalFunctionCall(String functionID, String... args) {
		ExternalFunction extFun = externalFunctions.inverse().get(functionID);
		if (extFun==null) throw new IllegalArgumentException("Unknown external function alias: " + functionID);
		if (args.length!=extFun.getArgumentTypes().length) throw new IllegalArgumentException("Number of arguments does not match arity of external function!");
		GroundTerm[] arguments = new GroundTerm[args.length];
		for (int i=0;i<args.length;i++) {
			if (args[i]==null) throw new IllegalArgumentException("Argument cannot be null!");
			ArgumentType t = extFun.getArgumentTypes()[i];
			if (t==ArgumentTypes.Number) {
				try {
					double no = Double.parseDouble(args[i]);
					arguments[i]=ArgumentFactory.getAttribute(no);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Expected argument to be a number but was given: " + args[i]);
				}
			} else if (t==ArgumentTypes.Text) {
				arguments[i]=ArgumentFactory.getAttribute(args[i]);
			} else if (t.isEntity()) {
				try {
					int id = Integer.parseInt(args[i]);
					arguments[i] = ArgumentFactory.getNonCachedEntity(getUniqueID(id),t);
				} catch (NumberFormatException e) {
					arguments[i] = ArgumentFactory.getNonCachedEntity(getUniqueID(args[i]),t);
				}
				
			} else throw new IllegalArgumentException("Unsupported type encountered: " + t);
		}
		double[] result =  extFun.getValue(arguments);
		if (result.length!=1) 
			throw new UnsupportedOperationException("Currently, only external functions with a single return value are supported!");
		return result[0];
	}



	
}
