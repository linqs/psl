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

import java.util.*;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.SelectQuery;

import edu.umd.cs.psl.database.PSLValue;
import edu.umd.cs.psl.model.argument.Attribute;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.*;
import edu.umd.cs.psl.model.formula.traversal.FormulaTraverser;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.ExternalFunctionPredicate;
import edu.umd.cs.psl.model.predicate.SpecialPredicates;

public class Formula2SQL extends FormulaTraverser {

	private static final String tablePrefix = "t";
	
	private final List<Variable> projection;
	private final VariableAssignment partialGrounding;
	private final RDBMSDatabase database;
	
	private final Map<Variable,String> joins;
	
	private final List<Atom> functionalAtoms;

	private final SelectQuery query;
	
	private int tableCounter;
	
	
	public Formula2SQL(VariableAssignment pg, List<Variable> proj, RDBMSDatabase db) {
		partialGrounding = pg;
		projection = proj;
		joins = new HashMap<Variable,String>();
		database = db;
		query = new SelectQuery();
		query.setIsDistinct(true);
		functionalAtoms = new ArrayList<Atom>(4);
		tableCounter = 1;
		if (projection.isEmpty()) query.addAllColumns(); //query.addAllTableColumns(tablePrefix+tableCounter);
	}
	
	public List<Atom> getFunctionalAtoms() {
		return functionalAtoms;
	}
	
	@Override
	public void afterConjunction(int noFormulas) {
		//Supported
	}

	@Override
	public void afterDisjunction(int noFormulas) {
		throw new AssertionError("Disjunction is currently not supported by database");
	}

	@Override
	public void afterNegation() {
		throw new AssertionError("Negation is currently not supported by database");
	}

	
	private void visitFunctionalAtom(Atom atom) {
		assert atom.getPredicate() instanceof FunctionalPredicate;
		Term[] arguments = atom.getArguments();
		assert arguments.length==2;
		Object[] convert = convertArguments(arguments);
		
		if (atom.getPredicate() instanceof ExternalFunctionPredicate) {
			ExternalFunctionPredicate predicate = (ExternalFunctionPredicate)atom.getPredicate();
			FunctionCall fun = new FunctionCall(RDBMSDatabase.aliasFunctionName);
			fun.addCustomParams(RDBMSDatabase.getSimilarityFunctionID(predicate.getExternalFunction()));
			for (int i=0;i<arguments.length;i++) fun.addCustomParams(convert[i]);
			query.addCondition(BinaryCondition.greaterThan(fun, 0.0, false));
		} else {
			FunctionalPredicate predicate = (FunctionalPredicate)atom.getPredicate();
			if (predicate==SpecialPredicates.Unequal) {
				query.addCondition(BinaryCondition.notEqualTo(convert[0], convert[1]));
			} else if (predicate==SpecialPredicates.Equal) {
				query.addCondition(BinaryCondition.equalTo(convert[0], convert[1]));
			} else if (predicate==SpecialPredicates.NonSymmetric) {
				query.addCondition(BinaryCondition.lessThan(convert[0], convert[1],false));
			} else throw new UnsupportedOperationException("Unrecognized functional Predicate: " + predicate);
		}
		
		
	}
	
	private Object[] convertArguments(Term[] arguments) {
		Object[] convert = new Object[arguments.length];
		
		for (int i=0;i<arguments.length;i++) {
			Term arg = arguments[i];
			if (arg instanceof Variable) {
				if (partialGrounding.hasVariable((Variable)arg)) {
					arg = partialGrounding.getVariable((Variable)arg);
				} else {
					assert joins.containsKey((Variable)arg) : arg;
					convert[i]=new CustomSql(joins.get((Variable)arg));
				}
			}
			if (arg instanceof Attribute) {
				convert[i] = ((Attribute)arg).getAttribute();
			} else if (arg instanceof Entity) {
				Entity e = (Entity)arg;
				convert[i] = e.getID().getDBID();
			} else assert arg instanceof Variable;
		}
		return convert;
	}

	
	
	@Override
	public void visitAtom(Atom atom) {
		if (atom.getPredicate() instanceof FunctionalPredicate) {
			functionalAtoms.add(atom);
		} else {
			assert atom.getPredicate() instanceof StandardPredicate;
			RDBMSPredicateHandle ph = database.getHandle(atom.getPredicate());
			
			String tableName = tablePrefix+tableCounter;
			String tableDot = tableName+".";
			query.addCustomFromTable(ph.tableName()+" "+tableName);
			Term[] arguments = atom.getArguments();
			for (int i=0;i<ph.argumentColumns().length;i++) {
				Term arg = arguments[i];
	
				if (arg instanceof Variable) {
					Variable var = (Variable)arg;
					if (partialGrounding.hasVariable(var)) {
						//assert !projection.contains(var);
						arg = partialGrounding.getVariable(var);
					} else {
						if (joins.containsKey(var)) {
							query.addCondition(BinaryCondition.equalTo(new CustomSql(tableDot+ph.argumentColumns()[i]),  new CustomSql(joins.get(var)) ));
						} else {
							if (projection.contains(var)) {
								query.addAliasedColumn(new CustomSql(tableDot+ph.argumentColumns()[i]), var.getName());
							}
							joins.put(var, tableDot+ph.argumentColumns()[i]);
						}
					}
				}
				
				if (arg instanceof Attribute) {
					query.addCondition(BinaryCondition.equalTo(new CustomSql(tableDot+ph.argumentColumns()[i]),  ((Attribute)arg).getAttribute() ));
				} else if (arg instanceof Entity) { //Entity
					Entity e = (Entity)arg;
					query.addCondition(BinaryCondition.equalTo(new CustomSql(tableDot+ph.argumentColumns()[i]),  e.getID().getDBID() ));
				} else assert arg instanceof Variable;
			}
			query.addCondition(new InCondition(new CustomSql(tableDot+ph.partitionColumn()),database.getReadIDs()));
			if (!ph.isClosed()) {
				query.addCondition(BinaryCondition.lessThan(new CustomSql(tableDot+ph.pslColumn()), 
										PSLValue.getNonDefaultUpperBound(), false) );
			}
			tableCounter++;
		}
	}
	
	
	public String getSQL(Formula f) {
		FormulaTraverser.traverse(f, this);
		for (Atom atom : functionalAtoms) visitFunctionalAtom(atom);
		return query.validate().toString();
	}
	
		
}
