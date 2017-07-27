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
	private static final String TABLE_ALIAS_PREFIX = "T";

	private final Set<Variable> projection;
	private final VariableAssignment partialGrounding;
	private final RDBMSDatabase database;

	/**
	 * Maps a variable to the first column (table alias and column) that we see it in.
	 */
	private final Map<Variable, String> joins;

	private final List<Atom> functionalAtoms;

	private final SelectQuery query;

	private int tableCounter;

	public Formula2SQL(VariableAssignment partialGrounding, Set<Variable> projection, RDBMSDatabase database) {
		this(partialGrounding, projection, database, true);
	}

	public Formula2SQL(VariableAssignment partialGrounding, Set<Variable> projection, RDBMSDatabase database, boolean isDistinct) {
		this.partialGrounding = partialGrounding;
		this.projection = projection;
		this.database = database;

		joins = new HashMap<Variable, String>();
		functionalAtoms = new ArrayList<Atom>();
		tableCounter = 0;

		query = new SelectQuery();
		query.setIsDistinct(isDistinct);

		if (projection.isEmpty()) {
			query.addAllColumns();
		}
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

	private void visitFunctionalAtom(Atom atom) {
		assert(atom.getPredicate() instanceof FunctionalPredicate);

		Term[] arguments = atom.getArguments();
		Object[] convert = convertArguments(arguments);

		if (atom.getPredicate() instanceof ExternalFunctionalPredicate) {
			ExternalFunctionalPredicate predicate = (ExternalFunctionalPredicate)atom.getPredicate();
			FunctionCall fun = new FunctionCall(ExternalFunctions.ALIAS_FUNCTION_NAME);

			fun.addCustomParams(RDBMSDataStore.getDatabaseID(database));
			fun.addCustomParams(ExternalFunctions.getExternalFunctionID(predicate.getExternalFunction()));
			fun.addCustomParams(convert);

			query.addCondition(BinaryCondition.greaterThan(fun, 0.0, false));
		} else {
			FunctionalPredicate predicate = (FunctionalPredicate)atom.getPredicate();

			if (predicate == SpecialPredicate.NotEqual) {
				query.addCondition(BinaryCondition.notEqualTo(convert[0], convert[1]));
			} else if (predicate == SpecialPredicate.Equal) {
				query.addCondition(BinaryCondition.equalTo(convert[0], convert[1]));
			} else if (predicate == SpecialPredicate.NonSymmetric) {
				query.addCondition(BinaryCondition.lessThan(convert[0], convert[1], false));
			} else {
				throw new UnsupportedOperationException("Unrecognized functional Predicate: " + predicate);
			}
		}
	}

	private Object[] convertArguments(Term[] arguments) {
		Object[] convert = new Object[arguments.length];

		for (int i = 0; i < arguments.length; i++) {
			Term arg = arguments[i];

			// If the variable is not in the argument map, just query for that variable.
			// If it is in the mapping, then pull out the mapped value and convert that.
			if (arg instanceof Variable) {
				if (partialGrounding.hasVariable((Variable)arg)) {
					arg = partialGrounding.getVariable((Variable)arg);
				} else {
					assert(joins.containsKey((Variable)arg));
					convert[i] = new CustomSql(joins.get((Variable)arg));
					continue;
				}
			}

			if (arg instanceof Attribute) {
				convert[i] = ((Attribute)arg).getValue();
			} else if (arg instanceof UniqueID) {
				convert[i] = ((UniqueID)arg).getInternalID();
			} else {
				throw new IllegalArgumentException("Unknown argument type: " + arg.getClass().getName());
			}
		}

		return convert;
	}

	@Override
	public void visitAtom(Atom atom) {
		if (atom.getPredicate() instanceof FunctionalPredicate) {
			functionalAtoms.add(atom);
			return;
		}

		// Each standard atom brings a new table join.
		assert(atom.getPredicate() instanceof StandardPredicate);
		PredicateInfo predicateInfo = database.getPredicateInfo(atom.getPredicate());

		String tableAlias = String.format("%s_%03d", TABLE_ALIAS_PREFIX, tableCounter);

		query.addCustomFromTable(predicateInfo.tableName() + " " + tableAlias);

		Term[] arguments = atom.getArguments();
		List<String> columnNames = predicateInfo.argumentColumns();
		assert(arguments.length == columnNames.size());

		for (int i = 0; i < arguments.length; i++) {
			Term arg = arguments[i];
			String columnReference = tableAlias + "." + columnNames.get(i);

			if (arg instanceof Variable) {
				Variable var = (Variable)arg;
				if (partialGrounding.hasVariable(var)) {
					arg = partialGrounding.getVariable(var);
				} else {
					if (joins.containsKey(var)) {
						query.addCondition(BinaryCondition.equalTo(
								new CustomSql(columnReference),
								new CustomSql(joins.get(var))));
					} else {
						if (projection.contains(var)) {
							query.addAliasedColumn(new CustomSql(columnReference), var.getName());
						}

						joins.put(var, columnReference);
					}
				}
			}

			if (arg instanceof Attribute || arg instanceof UniqueID) {
				Object value = null;
				if (arg instanceof Attribute) {
					value = ((Attribute)arg).getValue();
				} else {
					value = ((UniqueID)arg).getInternalID();
				}

				if (value instanceof String) {
					value = escapeSingleQuotes((String)value);
				}

				query.addCondition(BinaryCondition.equalTo(new CustomSql(columnReference), value));
			} else {
				assert(arg instanceof Variable);
			}
		}

		// Query all of the read (and the write) partition(s) belonging to the database
		ArrayList<Integer> partitions = new ArrayList<Integer>(database.getReadPartitions().size());
		for (int i = 0; i < database.getReadPartitions().size(); i++) {
			partitions.add(database.getReadPartitions().get(i).getID());
		}
		partitions.add(database.getWritePartition().getID());
		query.addCondition(new InCondition(new CustomSql(tableAlias + "." + PredicateInfo.PARTITION_COLUMN_NAME), partitions));

		tableCounter++;
	}

	public String getSQL(Formula formula) {
		AbstractFormulaTraverser.traverse(formula, this);
		for (Atom atom : functionalAtoms) {
			visitFunctionalAtom(atom);
		}

		return query.validate().toString();
	}

	private String escapeSingleQuotes(String s) {
		return s.replaceAll("'", "''");
	}
}
