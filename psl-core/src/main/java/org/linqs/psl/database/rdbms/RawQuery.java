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

import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A container for the information necessary to perform a raw SQL query.
 */
public class RawQuery {
    private String sql;

    /**
     * A mapping of variables to their index in the result set.
     */
    private Map<Variable, Integer> projectionMap;

    /**
     * Typing information,
     */
    private VariableTypeMap variableTypes;

    public RawQuery(RDBMSDatabase database, Formula formula) {
        this(database, formula, false);
    }

    public RawQuery(RDBMSDatabase database, Formula formula, boolean isDistinct) {
        this(database, new DatabaseQuery(formula, isDistinct));
    }

    public RawQuery(RDBMSDatabase database, DatabaseQuery query) {
        Formula formula = query.getFormula();

        VariableTypeMap variableTypes = formula.collectVariables(new VariableTypeMap());
        Set<Variable> projectTo = new HashSet<Variable>(variableTypes.getVariables());

        projectTo.removeAll(query.getIgnoreVariables());

        // Construct query from formula
        Formula2SQL sqler = new Formula2SQL(projectTo, database, query.getDistinct());

        this.sql = sqler.getSQL(formula);
        this.projectionMap = sqler.getProjectionMap();
        this.variableTypes = variableTypes;
    }

    public RawQuery(String sql, Map<Variable, Integer> projectionMap, VariableTypeMap variableTypes) {
        this.sql = sql;
        this.projectionMap = projectionMap;
        this.variableTypes = variableTypes;
    }

    public String getSQL() {
        return sql;
    }

    public Map<Variable, Integer> getProjectionMap() {
        return projectionMap;
    }

    public VariableTypeMap getVariableTypes() {
        return variableTypes;
    }
}
