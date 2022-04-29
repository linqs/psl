/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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

import org.linqs.psl.database.Partition;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Attribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.SelectQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Formula2SQL {
    private static final String TABLE_ALIAS_PREFIX = "T";

    private final Set<Variable> projection;
    private final RDBMSDatabase database;

    /**
     * Maps a variable to the first column (table alias and column) that we see it in.
     */
    private final Map<Variable, String> joins;

    /**
     * Maps each atom to the table (alias) it is drawn from.
     */
    private final Map<Atom, String> tableAliases;

    private final List<Atom> functionalAtoms;

    private final SelectQuery query;

    /**
     * The order of the variables as they appear in the select clause.
     */
    private final Map<Variable, Integer> projectionMap;

    private final List<Integer> partitions;
    private final List<Atom> partialTargets;

    private int tableCounter;

    /**
     * Convert a formula to a query that will fetch all possible combinations of constants used in that
     * formual (aka grounding).
     * This variant will enforce unqiue results (DISTINCT).
     * @param projection the collection of variables (columns) to return (the variable's name will be used
     * as the column alias). If not set, all columns (*) will be retutned.
     * @param database the database to query over. The read and write partitions will be picked up from here.
     */
    public Formula2SQL(Set<Variable> projection, RDBMSDatabase database) {
        this(projection, database, true);
    }

    /**
     * See above description.
     * @param isDistinct true if you want to enforce unique results (DISTINCT), false otherwise.
     *  Warning: this can be a costly operation.
     */
    public Formula2SQL(Set<Variable> projection, RDBMSDatabase database, boolean isDistinct) {
        this(projection, database, isDistinct, null);
    }

    /**
     * See above description.
     * @param partialTargets if this is non-null, then this formula will be treated as a partial grounding query.
     * This means that we will treat special partitions (with a negative id) as valid partitions,
     * and these atoms will be exclusively drawn from the special partitions.
     * We will do a DIRECT REFERENCE comparison against atoms in the formula to check for this specific one.
     */
    public Formula2SQL(Set<Variable> projection, RDBMSDatabase database, boolean isDistinct, List<Atom> partialTargets) {
        this.projection = projection;
        this.database = database;
        this.partialTargets = partialTargets;

        joins = new HashMap<Variable, String>();
        tableAliases = new HashMap<Atom, String>();
        projectionMap = new HashMap<Variable, Integer>();
        functionalAtoms = new ArrayList<Atom>();
        tableCounter = 0;

        query = new SelectQuery();
        query.setIsDistinct(isDistinct);

        if (projection.isEmpty()) {
            query.addAllColumns();
        }

        // Query all of the read (and the write) partition(s) belonging to the database.
        partitions = new ArrayList<Integer>(database.getReadPartitions().size() + 1);
        for (Partition partition : database.getReadPartitions()) {
            partitions.add(partition.getID());
        }
        partitions.add(database.getWritePartition().getID());
    }

    public List<Atom> getFunctionalAtoms() {
        return functionalAtoms;
    }

    public Map<Variable, Integer> getProjectionMap() {
        return Collections.unmodifiableMap(projectionMap);
    }

    public Map<Atom, String> getTableAliases() {
        return Collections.unmodifiableMap(tableAliases);
    }

    public SelectQuery getQuery(Formula formula) {
        traverse(formula);
        // Visit all the functional atoms at the end.
        for (Atom atom : functionalAtoms) {
            visitFunctionalAtom(atom);
        }

        return query.validate();
    }

    public String getSQL(Formula formula) {
        return getQuery(formula).toString();
    }

    private void visitFunctionalAtom(Atom atom) {
        assert(atom.getPredicate() instanceof FunctionalPredicate);

        Object[] convert = convertArguments(atom.getArguments());

        if (atom.getPredicate() instanceof ExternalFunctionalPredicate) {
            // Skip. All external functions are called when ground rules are instantiated.
        } else if (atom.getPredicate() instanceof GroundingOnlyPredicate) {
            GroundingOnlyPredicate predicate = (GroundingOnlyPredicate)atom.getPredicate();

            if (predicate.equals(GroundingOnlyPredicate.NotEqual)) {
                query.addCondition(BinaryCondition.notEqualTo(convert[0], convert[1]));
            } else if (predicate.equals(GroundingOnlyPredicate.Equal)) {
                query.addCondition(BinaryCondition.equalTo(convert[0], convert[1]));
            } else if (predicate.equals(GroundingOnlyPredicate.NonSymmetric)) {
                query.addCondition(BinaryCondition.lessThan(convert[0], convert[1], false));
            } else {
                throw new UnsupportedOperationException("Unrecognized GroundingOnlyPredicate: " + predicate);
            }
        } else {
            throw new UnsupportedOperationException("Unrecognized FunctionalPredicate: " + atom.getPredicate());
        }
    }

    private Object[] convertArguments(Term[] arguments) {
        Object[] convert = new Object[arguments.length];

        for (int i = 0; i < arguments.length; i++) {
            Term arg = arguments[i];

            // If the variable is not in the argument map, just query for that variable.
            // If it is in the mapping, then pull out the mapped value and convert that.
            if (arg instanceof Variable) {
                assert(joins.containsKey((Variable)arg));
                convert[i] = new CustomSql(joins.get((Variable)arg));
                continue;
            }

            if (arg instanceof Attribute) {
                convert[i] = ((Attribute)arg).getValue();
            } else if (arg instanceof UniqueIntID) {
                convert[i] = Integer.valueOf(((UniqueIntID)arg).getID());
            } else if (arg instanceof UniqueStringID) {
                convert[i] = ((UniqueStringID)arg).getID();
            } else {
                throw new IllegalArgumentException("Unknown argument type: " + arg.getClass().getName());
            }
        }

        return convert;
    }

    private void visitAtom(Atom atom) {
        if (atom.getPredicate() instanceof FunctionalPredicate) {
            functionalAtoms.add(atom);
            return;
        }

        // Each standard atom brings a new table join.
        assert(atom.getPredicate() instanceof StandardPredicate);
        PredicateInfo predicateInfo = ((RDBMSDataStore)database.getDataStore()).getPredicateInfo(atom.getPredicate());

        String tableAlias = String.format("%s_%03d", TABLE_ALIAS_PREFIX, tableCounter);
        tableAliases.put(atom, tableAlias);

        query.addCustomFromTable(predicateInfo.tableName() + " " + tableAlias);

        Term[] arguments = atom.getArguments();
        List<String> columnNames = predicateInfo.argumentColumns();
        assert(arguments.length == columnNames.size());

        for (int i = 0; i < arguments.length; i++) {
            Term arg = arguments[i];
            String columnReference = tableAlias + "." + columnNames.get(i);

            if (arg instanceof Variable) {
                Variable var = (Variable)arg;
                if (joins.containsKey(var)) {
                    query.addCondition(BinaryCondition.equalTo(
                            new CustomSql(columnReference),
                            new CustomSql(joins.get(var))));
                } else {
                    if (projection.contains(var)) {
                        query.addAliasedColumn(new CustomSql(columnReference), var.getName());
                        projectionMap.put(var, projectionMap.size());
                    }

                    joins.put(var, columnReference);
                }
            }

            if (arg instanceof Attribute || arg instanceof UniqueIntID || arg instanceof UniqueStringID) {
                Object value = null;
                if (arg instanceof Attribute) {
                    value = ((Attribute)arg).getValue();
                } else if (arg instanceof UniqueIntID) {
                    value = Integer.valueOf(((UniqueIntID)arg).getID());
                } else {
                    value = ((UniqueStringID)arg).getID();
                }

                if (value instanceof String) {
                    value = escapeSingleQuotes((String)value);
                }

                query.addCondition(BinaryCondition.equalTo(new CustomSql(columnReference), value));
            } else {
                assert(arg instanceof Variable);
            }
        }

        // Make sure to limit the partitions.
        // Most atoms get to choose from anywhere, partial target atoms can only come from a special partition.
        CustomSql partitionColumn = new CustomSql(tableAlias + "." + PredicateInfo.PARTITION_COLUMN_NAME);
        if ((partialTargets != null) && (partialTargets.contains(atom))) {
            query.addCondition(BinaryCondition.lessThan(partitionColumn, 0));
        } else {
            query.addCondition(new InCondition(partitionColumn, partitions));
        }

        tableCounter++;
    }

    /**
     * Recursively traverse a formula to build a query from it.
     */
    private void traverse(Formula formula) {
        if (formula instanceof Conjunction) {
            Conjunction conjunction = (Conjunction)formula;
            for (int i=0; i < conjunction.length(); i++) {
                traverse(conjunction.get(i));
            }
        } else if (formula instanceof Atom) {
            visitAtom((Atom)formula);
        } else if (formula instanceof Negation) {
            throw new IllegalArgumentException("Negations in formula are not supported in database queries.");
        } else if (formula instanceof Disjunction) {
            throw new IllegalArgumentException("Disjunctions in formula are not supported in database queries.");
        } else {
            throw new IllegalArgumentException("Unsupported Formula: " + formula.getClass().getName());
        }
    }

    private String escapeSingleQuotes(String s) {
        return s.replaceAll("'", "''");
    }
}
