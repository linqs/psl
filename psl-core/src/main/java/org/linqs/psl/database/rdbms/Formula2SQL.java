/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.database.rdbms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.VariableAssignment;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.traversal.AbstractFormulaTraverser;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Attribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueID;
import org.linqs.psl.model.term.Variable;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.SelectQuery;

public class Formula2SQL extends AbstractFormulaTraverser {

	private static final String tablePrefix = "t";

	protected final Set<Variable> projection;
	protected final VariableAssignment partialGrounding;
	protected final RDBMSDatabase database;

	protected final Map<Variable, String> joins;

	protected final List<Atom> functionalAtoms;

	protected final SelectQuery query;

	protected int tableCounter;

	public Formula2SQL(VariableAssignment pg, Set<Variable> proj,
			RDBMSDatabase db) {
		partialGrounding = pg;
		projection = proj;
		joins = new HashMap<Variable, String>();
		database = db;
		query = new SelectQuery();
		query.setIsDistinct(true);
		functionalAtoms = new ArrayList<Atom>(4);
		tableCounter = 1;
		if (projection.isEmpty())
			query.addAllColumns(); // query.addAllTableColumns(tablePrefix+tableCounter);
	}

	public List<Atom> getFunctionalAtoms() {
		return functionalAtoms;
	}

	@Override
	public void afterConjunction(int noFormulas) {
		// Supported
	}

	@Override
	public void afterDisjunction(int noFormulas) {
		throw new AssertionError(
				"Disjunction is currently not supported by database");
	}

	@Override
	public void afterNegation() {
		throw new AssertionError(
				"Negation is currently not supported by database");
	}

	protected void visitFunctionalAtom(Atom atom) {
		assert atom.getPredicate() instanceof FunctionalPredicate;
		Term[] arguments = atom.getArguments();
		Object[] convert = convertArguments(arguments);

		if (atom.getPredicate() instanceof ExternalFunctionalPredicate) {
			ExternalFunctionalPredicate predicate = (ExternalFunctionalPredicate) atom
					.getPredicate();
			FunctionCall fun = new FunctionCall(
					RDBMSDataStore.aliasFunctionName);
			fun.addCustomParams(RDBMSDataStore.getDatabaseID(database));
			fun.addCustomParams(RDBMSDataStore
					.getSimilarityFunctionID(predicate.getExternalFunction()));
			for (int i = 0; i < arguments.length; i++)
				fun.addCustomParams(convert[i]);
			query.addCondition(BinaryCondition.greaterThan(fun, 0.0, false));
		} else {
			FunctionalPredicate predicate = (FunctionalPredicate) atom
					.getPredicate();
			if (predicate == SpecialPredicate.NotEqual) {
				query.addCondition(BinaryCondition.notEqualTo(convert[0],
						convert[1]));
			} else if (predicate == SpecialPredicate.Equal) {
				query.addCondition(BinaryCondition.equalTo(convert[0],
						convert[1]));
			} else if (predicate == SpecialPredicate.NonSymmetric) {
				query.addCondition(BinaryCondition.lessThan(convert[0],
						convert[1], false));
			} else
				throw new UnsupportedOperationException(
						"Unrecognized functional Predicate: " + predicate);
		}

	}

	protected Object[] convertArguments(Term[] arguments) {
		Object[] convert = new Object[arguments.length];

		for (int i = 0; i < arguments.length; i++) {
			Term arg = arguments[i];
			if (arg instanceof Variable) {
				if (partialGrounding.hasVariable((Variable) arg)) {
					arg = partialGrounding.getVariable((Variable) arg);
				} else {
					assert joins.containsKey((Variable) arg) : arg;
					convert[i] = new CustomSql(joins.get((Variable) arg));
					continue;
				}
			} 
			if (arg instanceof Attribute) {
				convert[i] = ((Attribute) arg).getValue();
			} else if (arg instanceof UniqueID) {
				convert[i] = ((UniqueID) arg).getInternalID();
			} else {
				throw new IllegalArgumentException("Unknown argument type: "
						+ arg.getClass().getName());
			}
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

			String tableName = tablePrefix + tableCounter;
			String tableDot = tableName + ".";
			query.addCustomFromTable(ph.tableName() + " " + tableName);
			Term[] arguments = atom.getArguments();
			for (int i = 0; i < ph.argumentColumns().length; i++) {
				Term arg = arguments[i];

				if (arg instanceof Variable) {
					Variable var = (Variable) arg;
					if (partialGrounding.hasVariable(var)) {
						// assert !projection.contains(var);
						arg = partialGrounding.getVariable(var);
					} else {
						if (joins.containsKey(var)) {
							query.addCondition(BinaryCondition.equalTo(
									new CustomSql(tableDot
											+ ph.argumentColumns()[i]),
									new CustomSql(joins.get(var))));
						} else {
							if (projection.contains(var)) {
								query.addAliasedColumn(new CustomSql(tableDot
										+ ph.argumentColumns()[i]),
										var.getName());
							}
							joins.put(var, tableDot + ph.argumentColumns()[i]);
						}
					}
				}

				if (arg instanceof Attribute) {
					Object value = ((Attribute) arg).getValue();
					if (value instanceof String)
						value = escapeSingleQuotes((String) value);
					query.addCondition(BinaryCondition.equalTo(new CustomSql(
							tableDot + ph.argumentColumns()[i]), value));
				} else if (arg instanceof UniqueID) { // Entity
					Object value = ((UniqueID) arg).getInternalID();
					if (value instanceof String)
						value = escapeSingleQuotes((String) value);
					query.addCondition(BinaryCondition.equalTo(new CustomSql(
							tableDot + ph.argumentColumns()[i]), value));
				} else
					assert arg instanceof Variable;
			}
			
			ArrayList<Integer> partitions;
			partitions = new ArrayList<Integer>(database.readPartitions.length);
			// Query all of the read (and the write) partition(s) belonging to the database
			for (int i = 0; i < database.readPartitions.length; i++)
			    partitions.add(database.readPartitions[i].getID());
			partitions.add(database.writePartition.getID());

			query.addCondition(new InCondition(new CustomSql(tableDot
					+ ph.partitionColumn()), partitions));
			tableCounter++;
		}
	}

	public String getSQL(Formula f) {
		AbstractFormulaTraverser.traverse(f, this);
		for (Atom atom : functionalAtoms)
			visitFunctionalAtom(atom);
		return query.validate().toString();
	}
	
	public String escapeSingleQuotes(String s) {
		return s.replaceAll("'", "''");
	}

}
