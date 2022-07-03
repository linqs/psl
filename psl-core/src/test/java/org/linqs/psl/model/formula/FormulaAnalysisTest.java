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
package org.linqs.psl.model.formula;

import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;
import org.linqs.psl.util.ListUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FormulaAnalysisTest extends PSLBaseTest {
    private TestModel.ModelInformation model;

    @Before
    public void setup() {
        model = TestModel.getModel();
    }

    @After
    public void cleanup() {
        model.dataStore.close();
    }

    @Test
    public void testGeneralDNF() {
        // Test Cases:
        // A
        // !A
        // A && B
        // A || B
        // !(A && B)
        // !(A || B)
        // A && B && C
        // A && (B && C)
        // A || B || C
        // A || (B || C)
        // A && (A && B)
        // A || (A || B)
        // A && (B || C)
        // A || (B && C)
        // (A && B) && (C && D)
        // (A && B) || (C && D)
        // (A || B) && (C || D)
        // (A || B) || (C && D)
        // A && (B && C) && (D && E)
        // A || (B || C) || (D || E)
        // A && (B || C) && (D || E)
        // A || (B && C) || (D && E)
        // A -> B

        Formula[] inputs = new Formula[]{
            // A
            new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
            // !A
            new Negation(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A"))
            ),
            // A && B
            new Conjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
            ),
            // A || B
            new Disjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
            ),
            // !(A && B)
            new Negation(
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                )
            ),
            // !(A || B)
            new Negation(
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                )
            ),
            // A && B && C
            new Conjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
            ),
            // A && (B && C)
            new Conjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
                )
            ),
            // A || B || C
            new Disjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
            ),
            // A || (B || C)
            new Disjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
                )
            ),
            // A && (A && B)
            new Conjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                )
            ),
            // A || (A || B)
            new Disjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                )
            ),
            // A && (B || C)
            new Conjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
                )
            ),
            // A || (B && C)
            new Disjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
                )
            ),
            // (A && B) && (C && D)
            new Conjunction(
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                ),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("D"))
                )
            ),
            // (A && B) || (C && D)
            new Disjunction(
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                ),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("D"))
                )
            ),
            // (A || B) && (C || D)
            new Conjunction(
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                ),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("D"))
                )
            ),
            // (A || B) || (C && D)
            new Disjunction(
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
                ),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("D"))
                )
            ),
            // A && (B && C) && (D && E)
            new Conjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
                ),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("D")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("E"))
                )
            ),
            // A || (B || C) || (D || E)
            new Disjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
                ),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("D")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("E"))
                )
            ),
            // A && (B || C) && (D || E)
            new Conjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
                ),
                new Disjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("D")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("E"))
                )
            ),
            // A || (B && C) || (D && E)
            new Disjunction(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("C"))
                ),
                new Conjunction(
                    new QueryAtom(model.predicates.get("Nice"), new Variable("D")),
                    new QueryAtom(model.predicates.get("Nice"), new Variable("E"))
                )
            ),
            // A -> B
            new Implication(
                new QueryAtom(model.predicates.get("Nice"), new Variable("A")),
                new QueryAtom(model.predicates.get("Nice"), new Variable("B"))
            )
        };

        String[] expected = new String[]{
            // A
            "NICE(A)",
            // !A
            "~NICE(A)",
            // A && B
            "NICE(A) & NICE(B)",
            // A || B
            "NICE(A) | NICE(B)",
            // !(A && B)
            "~NICE(A) | ~NICE(B)",
            // !(A || B)
            "~NICE(A) & ~NICE(B)",
            // A && B && C
            "NICE(A) & NICE(B) & NICE(C)",
            // A && (B && C)
            "NICE(A) & NICE(B) & NICE(C)",
            // A || B || C
            "NICE(A) | NICE(B) | NICE(C)",
            // A || (B || C)
            "NICE(A) | NICE(B) | NICE(C)",
            // A && (A && B)
            "NICE(A) & NICE(B)",
            // A || (A || B)
            "NICE(A) | NICE(B)",
            // A && (B || C)
            "NICE(A) & NICE(B) | NICE(A) & NICE(C)",
            // A || (B && C)
            "NICE(A) | NICE(B) & NICE(C)",
            // (A && B) && (C && D)
            "NICE(A) & NICE(B) & NICE(C) & NICE(D)",
            // (A && B) || (C && D)
            "NICE(A) & NICE(B) | NICE(C) & NICE(D)",
            // (A || B) && (C || D)
            "NICE(A) & NICE(C) | NICE(A) & NICE(D) | NICE(B) & NICE(C) | NICE(B) & NICE(D)",
            // (A || B) || (C && D)
            "NICE(A) | NICE(B) | NICE(C) | NICE(D)",
            // A && (B && C) && (D && E)
            "NICE(A) & NICE(B) & NICE(C) & NICE(D) & NICE(E)",
            // A || (B || C) || (D || E)
            "NICE(A) | NICE(B) | NICE(C) | NICE(D) | NICE(E)",
            // A && (B || C) && (D || E)
            "NICE(A) & NICE(B) & NICE(D) | NICE(A) & NICE(B) & NICE(E) | NICE(A) & NICE(C) & NICE(D) | NICE(A) & NICE(C) & NICE(E)",
            // A || (B && C) || (D && E)
            "NICE(A) | NICE(B) & NICE(C) | NICE(D) & NICE(E)",
            // A -> B
            "~NICE(A) | NICE(B)",
        };

        String[] actual = new String[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            FormulaAnalysis analysis = new FormulaAnalysis(inputs[i]);
            List<String> clauses = new ArrayList<String>();
            for (int j = 0; j < analysis.getNumDNFClauses(); j++) {
                clauses.add(analysis.getDNFClause(j).toString());
            }
            actual[i] = ListUtils.join(" | ", clauses);
        }

        assertStringsEquals(expected, actual, true);
    }
}
