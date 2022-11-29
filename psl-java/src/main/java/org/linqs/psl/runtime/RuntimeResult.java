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
package org.linqs.psl.runtime;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * A struct containing the result of a PSL Runtime invocation.
 * Note that the contents will not be populated unless requested.
 */
public class RuntimeResult {
    private List<Rule> rules;
    private List<RandomVariableAtom> atoms;
    private List<String> evaluations;

    public RuntimeResult() {
        rules = new ArrayList<Rule>();
        atoms = new ArrayList<RandomVariableAtom>();
        evaluations = new ArrayList<String>();
    }

    public void addRule(Rule rule) {
        rules.add(rule);
    }

    public void addAtom(RandomVariableAtom atom) {
        atoms.add(atom);
    }

    public void addEvaluation(String evaluation) {
        evaluations.add(evaluation);
    }

    @Override
    public String toString() {
        return toJSON();
    }

    public String toJSON() {
        JSONRuntimeResult result = new JSONRuntimeResult(this);

        ObjectMapper mapper = new ObjectMapper();

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("    ", "\n"));

        try {
            return mapper.writer(printer).writeValueAsString(result);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static class JSONRuntimeResult {
        public String[] rules;
        public JSONAtom[] atoms;
        public String[] evaluations;

        public JSONRuntimeResult(RuntimeResult result) {
            rules = new String[result.rules.size()];
            for (int i = 0; i < rules.length; i++) {
                rules[i] = result.rules.get(i).toString();
            }

            atoms = new JSONAtom[result.atoms.size()];
            for (int i = 0; i < atoms.length; i++) {
                atoms[i] = new JSONAtom(result.atoms.get(i));
            }

            evaluations = new String[result.evaluations.size()];
            for (int i = 0; i < evaluations.length; i++) {
                evaluations[i] = result.evaluations.get(i);
            }
        }
    }

    private static class JSONAtom {
        public String predicate;
        public String[] arguments;
        public float value;

        public JSONAtom(RandomVariableAtom atom) {
            predicate = atom.getPredicate().getName();
            value = atom.getValue();

            arguments = new String[atom.getArity()];
            Term[] terms = atom.getArguments();
            for (int i = 0; i < terms.length; i++) {
                arguments[i] = ((Constant)terms[i]).rawToString();
            }
        }
    }
}
