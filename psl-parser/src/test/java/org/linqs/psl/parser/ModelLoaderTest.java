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
package org.linqs.psl.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.test.PSLTest;
import org.linqs.psl.util.ListUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ModelLoaderTest extends LoaderTest{
    @Test
    public void testBase() {
        String input =
            "1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
            "5: Single(B) & Double(B, A) >> Single(A) ^2\n";
        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "5.0: ( SINGLE(B) & DOUBLE(B, A) ) >> SINGLE(A) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    // Shortest rule we can think of.
    public void testShortRule() {
        String input =
            "~Single(A) .";
        String[] expected = new String[]{
            "~( SINGLE(A) ) ."
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    // Very long rule.
    public void testLongRule() {
        List<String> parts = new ArrayList<String>();
        for (char i = 'A'; i < 'Z'; i++) {
            parts.add(String.format("Single(%c) & Double(%c, Z)", i, i));
        }
        String input = String.format("1: %s >> Single(Z) ^2", ListUtils.join(" & ", parts));
        String expected = String.format("1.0: ( %s ) >> SINGLE(Z) ^2", ListUtils.join(" & ", parts).toUpperCase());

        PSLTest.assertModel(input, new String[]{expected});
    }

    @Test
    // General check for comment support.
    public void testComments() {
        String input =
            "# This is a comment!\n" +
            "#This is a comment!\n" +
            "## This is another comment (but actually the same form).\n" +
            "    # This is a comment!\n" +
            "\n" +
            "//This is a comment!\n" +
            "// This is a comment!\n" +
            "//// This is another comment (but actually the same form).\n" +
            "    // This is a comment!\n" +
            "\n" +
            "/* Block time! */\n" +
            "/* Block time!\n" +
            " Sill in a comment\n" +
            "*/\n" +
            "/** Block time (javadoc style)!\n" +
            " * Sill in a comment\n" +
            " */\n" +
            "\n" +
            "1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
            "// Another comment.\n" +
            "1: Single(C) & Double(C, D) >> Single(D) ^2 // Inline comment!\n" +
            "1: Single(E) & Double(E, F) >> Single(F) ^2 # Inline comment!\n" +
            "1: Single(G) & Double(G, H) >> Single(H) ^2 /* Inline comment! */\n" +
            "1: Single(I) & Double(I, J) >> Single(J) // ^2 // Changing a rule.\n" +
            "1: Single(K) & Double(K, L) /* & Single(C) */ >> Single(L) ^2 // Inside of other syntax.\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE(C) & DOUBLE(C, D) ) >> SINGLE(D) ^2",
            "1.0: ( SINGLE(E) & DOUBLE(E, F) ) >> SINGLE(F) ^2",
            "1.0: ( SINGLE(G) & DOUBLE(G, H) ) >> SINGLE(H) ^2",
            "1.0: ( SINGLE(I) & DOUBLE(I, J) ) >> SINGLE(J)",
            "1.0: ( SINGLE(K) & DOUBLE(K, L) ) >> SINGLE(L) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testConstants() {
        String input =
            "1: Single(A) & Double(A, \"bar\") & Single(\"bar\") >> Double(A, \"bar\") ^2\n" +
            "1: Single(B) & Double(B, 'bar') & Single('bar') >> Double(B, 'bar') ^2\n" +
            "1: Single(C) & Double(C, 'BAR') & Single('BAR') >> Double(C, 'BAR') ^2\n" +
            "1: Single(D) & Double(D, '1BAR') & Single('1BAR') >> Double(D, '1BAR') ^2\n" +
            // Note that all constants get quotes, but they will get converted into their native types later.
            "1: Single(E) & Double(E, '1') & Single('1') >> Double(E, '1') ^2\n" +
            "1: Single(F) & Double(F, '999') & Single('999') >> Double(F, '999') ^2\n" +
            "1: Single(G) & Double(G, \"999\") & Single(\"999\") >> Double(G, \"999\") ^2\n" +
            // Spaces
            "1: Single(A) & Double(A, ' Z') & Single(' Z') >> Double(A, ' Z') ^2\n" +
            "1: Single(A) & Double(A, 'Z ') & Single('Z ') >> Double(A, 'Z ') ^2\n" +
            "1: Single(A) & Double(A, '  Z') & Single('  Z') >> Double(A, '  Z') ^2\n" +
            "1: Single(A) & Double(A, 'Z  ') & Single('Z  ') >> Double(A, 'Z  ') ^2\n" +
            "1: Single(A) & Double(A, ' Z ') & Single(' Z ') >> Double(A, ' Z ') ^2\n" +
            "1: Single(A) & Double(A, '  Z  ') & Single('  Z  ') >> Double(A, '  Z  ') ^2\n" +
            "1: Single(A) & Double(A, ' ') & Single(' ') >> Double(A, ' ') ^2\n" +
            "1: Single(A) & Double(A, '  ') & Single('  ') >> Double(A, '  ') ^2\n" +
            "1: Single(A) & Double(A, 'A B') & Single('A B') >> Double(A, 'A B') ^2\n" +
            "1: Single(A) & Double(A, 'A  B') & Single('A  B') >> Double(A, 'A  B') ^2\n" +
            "1: Single(A) & Double(A, ' A B ') & Single(' A B ') >> Double(A, ' A B ') ^2\n" +
            // Underscores
            "1: Single(A) & Double(A, '_Z') & Single('_Z') >> Double(A, '_Z') ^2\n" +
            "1: Single(A) & Double(A, 'Z_') & Single('Z_') >> Double(A, 'Z_') ^2\n" +
            "1: Single(A) & Double(A, '__Z') & Single('__Z') >> Double(A, '__Z') ^2\n" +
            "1: Single(A) & Double(A, 'Z__') & Single('Z__') >> Double(A, 'Z__') ^2\n" +
            "1: Single(A) & Double(A, '_Z_') & Single('_Z_') >> Double(A, '_Z_') ^2\n" +
            "1: Single(A) & Double(A, '__Z__') & Single('__Z__') >> Double(A, '__Z__') ^2\n" +
            "1: Single(A) & Double(A, '_') & Single('_') >> Double(A, '_') ^2\n" +
            "1: Single(A) & Double(A, '__') & Single('__') >> Double(A, '__') ^2\n" +
            "1: Single(A) & Double(A, 'A_B') & Single('A_B') >> Double(A, 'A_B') ^2\n" +
            "1: Single(A) & Double(A, 'A__B') & Single('A__B') >> Double(A, 'A__B') ^2\n" +
            "1: Single(A) & Double(A, '_A_B_') & Single('_A_B_') >> Double(A, '_A_B_') ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & DOUBLE(A, 'bar') & SINGLE('bar') ) >> DOUBLE(A, 'bar') ^2",
            "1.0: ( SINGLE(B) & DOUBLE(B, 'bar') & SINGLE('bar') ) >> DOUBLE(B, 'bar') ^2",
            "1.0: ( SINGLE(C) & DOUBLE(C, 'BAR') & SINGLE('BAR') ) >> DOUBLE(C, 'BAR') ^2",
            "1.0: ( SINGLE(D) & DOUBLE(D, '1BAR') & SINGLE('1BAR') ) >> DOUBLE(D, '1BAR') ^2",
            "1.0: ( SINGLE(E) & DOUBLE(E, '1') & SINGLE('1') ) >> DOUBLE(E, '1') ^2",
            "1.0: ( SINGLE(F) & DOUBLE(F, '999') & SINGLE('999') ) >> DOUBLE(F, '999') ^2",
            "1.0: ( SINGLE(G) & DOUBLE(G, '999') & SINGLE('999') ) >> DOUBLE(G, '999') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, ' Z') & SINGLE(' Z') ) >> DOUBLE(A, ' Z') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, 'Z ') & SINGLE('Z ') ) >> DOUBLE(A, 'Z ') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '  Z') & SINGLE('  Z') ) >> DOUBLE(A, '  Z') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, 'Z  ') & SINGLE('Z  ') ) >> DOUBLE(A, 'Z  ') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, ' Z ') & SINGLE(' Z ') ) >> DOUBLE(A, ' Z ') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '  Z  ') & SINGLE('  Z  ') ) >> DOUBLE(A, '  Z  ') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, ' ') & SINGLE(' ') ) >> DOUBLE(A, ' ') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '  ') & SINGLE('  ') ) >> DOUBLE(A, '  ') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, 'A B') & SINGLE('A B') ) >> DOUBLE(A, 'A B') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, 'A  B') & SINGLE('A  B') ) >> DOUBLE(A, 'A  B') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, ' A B ') & SINGLE(' A B ') ) >> DOUBLE(A, ' A B ') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '_Z') & SINGLE('_Z') ) >> DOUBLE(A, '_Z') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, 'Z_') & SINGLE('Z_') ) >> DOUBLE(A, 'Z_') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '__Z') & SINGLE('__Z') ) >> DOUBLE(A, '__Z') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, 'Z__') & SINGLE('Z__') ) >> DOUBLE(A, 'Z__') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '_Z_') & SINGLE('_Z_') ) >> DOUBLE(A, '_Z_') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '__Z__') & SINGLE('__Z__') ) >> DOUBLE(A, '__Z__') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '_') & SINGLE('_') ) >> DOUBLE(A, '_') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '__') & SINGLE('__') ) >> DOUBLE(A, '__') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, 'A_B') & SINGLE('A_B') ) >> DOUBLE(A, 'A_B') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, 'A__B') & SINGLE('A__B') ) >> DOUBLE(A, 'A__B') ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, '_A_B_') & SINGLE('_A_B_') ) >> DOUBLE(A, '_A_B_') ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    // We are actually testing both numeric constants and coefficients.
    public void testNumericConstants() {
        String input =
            "1: 1 Single(A) = 1 ^2\n" +
            "1: 1.0 Single(A) = 1 ^2\n" +
            "1: 1.5 Single(A) = 1 ^2\n" +
            "1: 0.5 Single(A) = 1 ^2\n" +
            "1: -1.0 Single(A) = 1 ^2\n" +
            "1: 5E6 Single(A) = 1 ^2\n" +
            "1: 5e6 Single(A) = 1 ^2\n" +
            "1: -5e6 Single(A) = 1 ^2\n" +
            "1: 1.2e6 Single(A) = 1 ^2\n" +
            "1: -1.2e6 Single(A) = 1 ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: 1.0 * SINGLE(A) = 1.0 ^2",
            // "1.0: 1.0 * SINGLE(A) = 1.0 ^2",  // Duplicate rule ignored.
            "1.0: 1.5 * SINGLE(A) = 1.0 ^2",
            "1.0: 0.5 * SINGLE(A) = 1.0 ^2",
            "1.0: -1.0 * SINGLE(A) = 1.0 ^2",
            "1.0: 5000000.0 * SINGLE(A) = 1.0 ^2",
            // "1.0: 5000000.0 * SINGLE(A) = 1.0 ^2",  // Duplicate rule ignored.
            "1.0: -5000000.0 * SINGLE(A) = 1.0 ^2",
            "1.0: 1200000.0 * SINGLE(A) = 1.0 ^2",
            "1.0: -1200000.0 * SINGLE(A) = 1.0 ^2",
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    // Using some more unusual identifiers.
    public void testIdentifiers() {
        String input =
            "1: Single(A1) >> Single(A1) ^2\n" +
            "1: Single(A1A) >> Single(A1A) ^2\n" +
            "1: Single(A_A) >> Single(A_A) ^2\n" +
            "1: Single(A_1) >> Single(A_1) ^2\n" +
            "1: Single(A__) >> Single(A__) ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: SINGLE(A1) >> SINGLE(A1) ^2",
            "1.0: SINGLE(A1A) >> SINGLE(A1A) ^2",
            "1.0: SINGLE(A_A) >> SINGLE(A_A) ^2",
            "1.0: SINGLE(A_1) >> SINGLE(A_1) ^2",
            "1.0: SINGLE(A__) >> SINGLE(A__) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    // Test possible values for the rule weight.
    public void testWeight() {
        String input =
            "1: Single(A) >> Single(A)\n" +
            "0: Single(B) >> Single(B)\n" +
            "0.5: Single(C) >> Single(C)\n" +
            "999999: Single(D) >> Single(D)\n" +
            "9999999: Single(E) >> Single(E)\n" +
            "0000000001: Single(F) >> Single(F)\n" +
            "0.001: Single(G) >> Single(G)\n" +
            "0.00000001: Single(H) >> Single(H)\n" +
            "2E6: Single(I) >> Single(I)\n" +
            "2e6: Single(J) >> Single(J)\n" +
            "2e-6: Single(K) >> Single(K)\n" +
            "2.5e6: Single(L) >> Single(L)\n" +
            "2.5e-6: Single(M) >> Single(M)\n" +
            "";
        String[] expected = new String[]{
            "1.0: SINGLE(A) >> SINGLE(A)",
            "0.0: SINGLE(B) >> SINGLE(B)",
            "0.5: SINGLE(C) >> SINGLE(C)",
            "999999.0: SINGLE(D) >> SINGLE(D)",
            "9999999.0: SINGLE(E) >> SINGLE(E)",
            "1.0: SINGLE(F) >> SINGLE(F)",
            "0.001: SINGLE(G) >> SINGLE(G)",
            "1.0E-8: SINGLE(H) >> SINGLE(H)",
            "2000000.0: SINGLE(I) >> SINGLE(I)",
            "2000000.0: SINGLE(J) >> SINGLE(J)",
            "2.0E-6: SINGLE(K) >> SINGLE(K)",
            "2500000.0: SINGLE(L) >> SINGLE(L)",
            "2.5E-6: SINGLE(M) >> SINGLE(M)"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testImpliedBy() {
        String input =
            "1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
            "1: Single(D) << Single(C) & Double(C, D) ^2\n";
        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE(C) & DOUBLE(C, D) ) >> SINGLE(D) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testDisjunction() {
        String input =
            "1: Single(A) | Double(A, B) << Single(B) & Single(A) ^2\n" +
            "1: Single(A) & Double(B, C) >> Single(B) | Single(C) ^2\n";
        String[] expected = new String[]{
            "1.0: ( SINGLE(B) & SINGLE(A) ) >> ( SINGLE(A) | DOUBLE(A, B) ) ^2",
            "1.0: ( SINGLE(A) & DOUBLE(B, C) ) >> ( SINGLE(B) | SINGLE(C) ) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    /**
     * Negation is only allowed on an atom or another negation.
     * This is because we only allow conjunctions in the body and disjunctions in the head.
     */
    @Test
    public void testNegation() {
        String input =
            "1: ~Single(A) & Double(A, B) >> ~Single(B) ^2\n" +
            "1: ~Single(C) & ~~Double(C, D) >> ~~~Single(D) ^2\n" +
            "1: !Single(E) & Double(E, F) >> !Single(F) ^2\n" +
            "1: !Single(G) & !!Double(G, H) >> !!!Single(H) ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( ~( SINGLE(A) ) & DOUBLE(A, B) ) >> ~( SINGLE(B) ) ^2",
            "1.0: ( ~( SINGLE(C) ) & ~( ~( DOUBLE(C, D) ) ) ) >> ~( ~( ~( SINGLE(D) ) ) ) ^2",
            "1.0: ( ~( SINGLE(E) ) & DOUBLE(E, F) ) >> ~( SINGLE(F) ) ^2",
            "1.0: ( ~( SINGLE(G) ) & ~( ~( DOUBLE(G, H) ) ) ) >> ~( ~( ~( SINGLE(H) ) ) ) ^2"
        };

        PSLTest.assertModel(input, expected);

        try {
            PSLTest.assertRule("1: ~( Single(A) & Single(B) ) >> Double(A, B) ^2", "");
            fail("Negation not allowed on a conjunction.");
        } catch (org.antlr.v4.runtime.RecognitionException ex) {
            // Exception expected.
        }

        try {
            PSLTest.assertRule("1: Double(A, B) >> ~( Single(A) | Single(B) ) ^2", "");
            fail("Negation not allowed on a disjunction.");
        } catch (org.antlr.v4.runtime.RecognitionException ex) {
            // Exception expected.
        }
    }

    @Test
    public void testTermEquality() {
        String input =
            "1: A == B & Double(A, B) >> Single(B) ^2\n" +
            "1: A == 'Bar' & Double(A, B) >> Single(B) ^2\n" +
            "1: 'Foo' == B & Double(A, B) >> Single(B) ^2\n" +
            "1: 'Foo' == 'Bar' & Double(A, B) >> Single(B) ^2\n" +
            "1: A ~= B & Double(A, B) >> Single(B) ^2\n" +
            "1: A ~= 'Bar' & Double(A, B) >> Single(B) ^2\n" +
            "1: 'Foo' ~= B & Double(A, B) >> Single(B) ^2\n" +
            "1: 'Foo' ~= 'Bar' & Double(A, B) >> Single(B) ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( (A == B) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( (A == 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( ('Foo' == B) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( ('Foo' == 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( (A != B) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( (A != 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( ('Foo' != B) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( ('Foo' != 'Bar') & DOUBLE(A, B) ) >> SINGLE(B) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testAlternativeSyntax() {
        String input =
            "1: Single(A) & Double(A, B) >> Single(B)\n" +
            "1: Single(C) && Double(C, D) >> Single(D)\n" +
            "1: Single(E) & Double(E, F) >> Single(F)\n" +
            "1: Single(G) & Double(G, H) -> Single(H)\n" +
            "1: Single(K) | Single(L) << Double(K, L)\n" +
            "1: Single(M) || Single(N) << Double(M, N)\n" +
            "1: Single(O) | Single(P) << Double(O, P)\n" +
            "1: Single(Q) | Single(R) <- Double(Q, R)\n" +
            "1: S != T & Double(S, T) >> Single(T)\n" +
            "1: U ~= V & Double(U, V) >> Single(V)\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
            "1.0: ( SINGLE(C) & DOUBLE(C, D) ) >> SINGLE(D)",
            "1.0: ( SINGLE(E) & DOUBLE(E, F) ) >> SINGLE(F)",
            "1.0: ( SINGLE(G) & DOUBLE(G, H) ) >> SINGLE(H)",
            "1.0: DOUBLE(K, L) >> ( SINGLE(K) | SINGLE(L) )",
            "1.0: DOUBLE(M, N) >> ( SINGLE(M) | SINGLE(N) )",
            "1.0: DOUBLE(O, P) >> ( SINGLE(O) | SINGLE(P) )",
            "1.0: DOUBLE(Q, R) >> ( SINGLE(Q) | SINGLE(R) )",
            "1.0: ( (S != T) & DOUBLE(S, T) ) >> SINGLE(T)",
            "1.0: ( (U != V) & DOUBLE(U, V) ) >> SINGLE(V)"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testMultiplyCoefficient() {
        String input =
            "1: 1 Single(A) = 1 ^2\n" +
            "1: 1 * Single(A) = 1 ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: 1.0 * SINGLE(A) = 1.0 ^2",
            // "1.0: 1.0 * SINGLE(A) = 1.0 ^2"  // Duplicate rule ignored.
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    // Just throw in a bunch of arithmetic rules used in other tests.
    public void testGeneralArithmetic() {
        String input =
            "Single(A) + Single(B) = 1 .\n" +
            "Double(+A, 'Foo') = 1 .\n" +
            "Single(+A) + Single(+B) = 1 .\n" +
            "Single(+A) = 1 . {A: Single(A)}\n" +
            "Single(+A) = 1 . {A: Single(A) || Double(A, A) }\n" +
            "Double(+A, B) = 1 . {A: Single(B)}\n" +
            "Single(+A) + Single(+B) = 1 . {A: Single(A)} {B: Single(B)}\n" +
            "|A| Single(+A) = 1 .\n" +
            "|A| Single(+A) = |A| .\n" +
            "|A| Single(+A) + |B| Single(+B) = 1 .\n" +
            "@Max[|A|, 0] Single(+A) = 1 .\n" +
            "@Max[1, 0] Single(+A) = 1 .\n" +
            "@Max[|A|, |B|] Single(+A) + Single(+B) = 1 .\n" +
            "@Min[1, 0] Single(A) = 1 .\n" +
            "";
        String[] expected = new String[]{
            "1.0 * SINGLE(A) + 1.0 * SINGLE(B) = 1.0 .",
            "1.0 * DOUBLE(+A, 'Foo') = 1.0 .",
            "1.0 * SINGLE(+A) + 1.0 * SINGLE(+B) = 1.0 .",
            "1.0 * SINGLE(+A) = 1.0 .   {A : SINGLE(A)}",
            "1.0 * SINGLE(+A) = 1.0 .   {A : SINGLE(A)}",
            "1.0 * SINGLE(+A) = 1.0 .   {A : DOUBLE(A, A)}",
            "1.0 * DOUBLE(+A, B) = 1.0 .   {A : SINGLE(B)}",
            "1.0 * SINGLE(+A) + 1.0 * SINGLE(+B) = 1.0 .   {A : SINGLE(A)}   {B : SINGLE(B)}",
            "|A| * SINGLE(+A) = 1.0 .",
            "|A| * SINGLE(+A) = |A| .",
            "|A| * SINGLE(+A) + |B| * SINGLE(+B) = 1.0 .",
            "@Max[|A|, 0.0] * SINGLE(+A) = 1.0 .",
            "1.0 * SINGLE(+A) = 1.0 .",
            "@Max[|A|, |B|] * SINGLE(+A) + 1.0 * SINGLE(+B) = 1.0 .",
            "0.0 * SINGLE(A) = 1.0 ."
        };

        PSLTest.assertStringModel(input, expected, true);
    }

    @Test
    public void testLoadRuleBase() {
        String input = "1: Single(A) & Double(A, B) >> Single(B) ^2";
        String expected = "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2";
        PSLTest.assertRule(input, expected);
    }

    @Test
    public void testLoadRuleBadCount() {
        // Having zero rules is a parse error, so the exception is different.
        try {
            PSLTest.assertRule("// Just a comment", "");
            fail("ModelLoader.LoadRule() with no rule did not throw an exception.");
        } catch (org.antlr.v4.runtime.NoViableAltException ex) {
            // Exception expected.
        }

        String input =
            "1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
            "5: Single(B) & Double(B, A) >> Single(A) ^2\n";
        String expected = "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2";

        try {
            PSLTest.assertRule(input, expected);
            fail("ModelLoader.LoadRule() with more than one rule did not throw an exception.");
        } catch (IllegalArgumentException ex) {
            // Exception expected.
        }
    }

    @Test
    // Floats must have a leading number (".1" is not good, must be "0.1").
    public void testLeadDigitOnFloat() {
        String[] input = new String[]{
            ".1: Single(A) & Double(A, B) >> Single(B) ^2",
            "1: .1 Single(A) = 1 ^2"
        };
        String[] expected = new String[]{
            "0.1: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: 0.1 * SINGLE(A) = 1.0 ^2"
        };

        for (int i = 0; i < input.length; i++) {
            try {
                PSLTest.assertRule(input[i], expected[i]);
                fail(String.format("Rule: %d - Exception not thrown when float used without leading digit.", i));
            } catch (Exception ex) {
                // Exception expected.
            }
        }
    }

    @Test
    // Various misc syntax errors.
    public void testGeneralBadSyntax() {
        String[] input = new String[]{
            // Missing comma
            "1: Single(A) & Double(A B) >> Single(B) ^2",
            // Unknown predicate
            "1: Unknown(A) & Double(A, B) >> Single(B) ^2",
            // Mismatched quotes.
            "1: Single(A) & Double(\"Foo', B) >> Single(B) ^2",
            // Mismatched parens.
            "1: ( Single(A) & Double(A, B) >> Single(B) ^2",
            "1: Single(A) & Double(A, B) ) >> Single(B) ^2",
            // Missing unweighted period.
            "Single(A) & Double(A, B) >> Single(B) ^2",
            // Negative weight
            "-1: Single(A) & Double(A B) >> Single(B) ^2"
        };
        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( UNKNOWN(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE(A) & DOUBLE('Foo', B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) . ^2",
            "-1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2"
        };

        for (int i = 0; i < input.length; i++) {
            try {
                PSLTest.assertRule(input[i], expected[i]);
                fail(String.format("Rule: %d - Exception not thrown on general syntax error.", i));
            } catch (Exception ex) {
                // Exception expected.
            }
        }
    }

    @Test
    public void testBadSquaring() {
        String[] input = new String[]{
            // TODO(eriq): This is a bad input but not caught by the parser.
            // "1: Single(A) & Double(A, B) >> Single(B) ^2.5",
            "1: Single(A) & Double(A, B) >> Single(B) ^3",
            "1: Single(A) & Double(A, B) >> Single(B) ^-1",
            "1: Single(A) & Double(A, B) >> Single(B) ^-2.0",
            "1: Single(A) & Double(A, B) >> Single(B) ^-2.5",
            "1: Single(A) & Double(A, B) >> Single(B) ^-3"
        };
        String[] expected = new String[]{
            // "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)",
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B)"
        };

        for (int i = 0; i < input.length; i++) {
            try {
                PSLTest.assertRule(input[i], expected[i]);
                fail(String.format("Rule: %d - Exception not thrown on bad square error.", i));
            } catch (Exception ex) {
                // Exception expected.
            }
        }
    }

    @Test
    // First test only rules that are fully specified.
    public void testLoadRulePartialCompleteRules() {
        String[] inputs = new String[]{
            "1: Single(A) & Double(A, B) >> Single(B) ^2",
            "Single(A) & Double(A, B) >> Single(B) .",
            "1: 1 Single(A) = 1 ^2",
            "1 Single(A) = 1 .",
            "Single(+A) = 1 . {A: Single(A)}",
            "1: Single(+A) = 1 {A: Single(A)}",
            "1: Single(+A) = 1 ^2 {A: Single(A)}"
        };

        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) .",
            "1.0: 1.0 * SINGLE(A) = 1.0 ^2",
            "1.0 * SINGLE(A) = 1.0 .",
            "1.0 * SINGLE(+A) = 1.0 .   {A : SINGLE(A)}",
            "1.0: 1.0 * SINGLE(+A) = 1.0   {A : SINGLE(A)}",
            "1.0: 1.0 * SINGLE(+A) = 1.0 ^2   {A : SINGLE(A)}",
        };

        for (int i = 0; i < inputs.length; i++) {
            RulePartial partial = ModelLoader.loadRulePartial(inputs[i]);
            assertEquals(
                    String.format("Expected RulePartial #%d to be a rule, but was not.", i),
                    true,
                    partial.isRule()
            );

            Rule rule = partial.toRule();
            PSLTest.assertStringEquals(expected[i], rule.toString(), true,
                    String.format("Rule %d string mismatch", i));
        }
    }

    @Test
    // First test only rules that are fully specified.
    public void testLoadRulePartialPartialRules() {
        String[] inputs = new String[]{
            "Single(A) & Double(A, B) >> Single(B)",
            "1 Single(A) = 1",
            "Single(+A) = 1 {A: Single(A)}",
            "Single(+A) + Single(+B) = 1 {A: Single(A)} {B: Single(B)}"
        };

        String[] unweightedExpected = new String[]{
            "( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) .",
            "1.0 * SINGLE(A) = 1.0 .",
            "1.0 * SINGLE(+A) = 1.0 .   {A : SINGLE(A)}",
            "1.0 * SINGLE(+A) + 1.0 * SINGLE(+B) = 1.0 .   {A : SINGLE(A)}   {B : SINGLE(B)}"
        };

        // Weight all the variants with 5 and square them.
        String[] weightedExpected = new String[]{
            "5.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "5.0: 1.0 * SINGLE(A) = 1.0 ^2",
            "5.0: 1.0 * SINGLE(+A) = 1.0 ^2   {A : SINGLE(A)}",
            "5.0: 1.0 * SINGLE(+A) + 1.0 * SINGLE(+B) = 1.0 ^2   {A : SINGLE(A)}   {B : SINGLE(B)}"
        };

        for (int i = 0; i < inputs.length; i++) {
            RulePartial partial = ModelLoader.loadRulePartial(inputs[i]);
            assertEquals(
                    String.format("Expected RulePartial #%d to not a rule, but was.", i),
                    false,
                    partial.isRule()
            );

            Rule unweightedRule = partial.toRule();
            PSLTest.assertStringEquals(unweightedExpected[i], unweightedRule.toString(), true,
                    String.format("Unweighted rule %d string mismatch", i));

            Rule weightedRule = partial.toRule(5.0f, true);
            PSLTest.assertStringEquals(weightedExpected[i], weightedRule.toString(), true,
                    String.format("Weighted rule %d string mismatch", i));
        }
    }

    @Test
    public void testNonSymmetric() {
        String input =
            "1: Single(A) & Single(B) & (A % B) >> Double(A, B) ^2\n" +
            "1: Single(C) & Single(D) & (C ^ D) >> Double(C, D) ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & SINGLE(B) & (A % B) ) >> DOUBLE(A, B) ^2",
            "1.0: ( SINGLE(C) & SINGLE(D) & (C % D) ) >> DOUBLE(C, D) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testArithmeticCoefficientOperationOrder() {
        String input =
            "1.0 + 2.0 * 3.0 * Single(A) = 99 .\n" +
            "1.0 + 2.0 * 3.0 * Single(A) + Single(B) = 99 .\n" +
            "99 = 1.0 + 2.0 * 3.0 * Single(A) .\n" +
            "1.0 + 2.0 * 3.0 * Single(A) = 4.0 + 5.0 * 6.0 * Single(B) .\n" +
            "1.0 + 2.0 - 3.0 * Single(A) = 99 .\n" +
            "1.0 - 2.0 + 3.0 * Single(A) = 99 .\n" +
            "1.0 + 2.0 + 3.0 - 4.0 * Single(A) = 99 .\n" +
            "1.0 - 2.0 - 3.0 + 4.0 * Single(A) = 99 .\n" +
            "1.0 + (2.0 * 3.0) * Single(A) = 99 .\n" +
            "(1.0 + 2.0) * 3.0 * Single(A) = 99 .\n" +
            "1.0 + 2.0 * |A| * Single(+A) = 99 .\n" +
            "1.0 + 2.0 * @Min(3.0, 4.0) * Single(+A) = 99 .\n" +
            "1.0 + 2.0 * @Max(3.0, 4.0) * Single(+A) = 99 .\n" +
            "1.0 + 2.0 * 3.0 + 4.0 * Single(A) = 99 .\n" +
            "1.0 + (2.0 * 3.0) + 4.0 * Single(A) = 99 .\n" +
            "(1.0 + 2.0) * (3.0 + 4.0) * Single(A) = 99 .\n" +
            "1.0 - 2.0 / 4.0 - 5.0 * Single(A) = 99 .\n" +
            "1.0 - (2.0 / 4.0) - 5.0 * Single(A) = 99 .\n" +
            "(1.0 - 2.0) / (4.0 - 5.0) * Single(A) = 99 .\n" +
            "";
        String[] expected = new String[]{
            "7.0 * SINGLE(A) = 99.0 .",
            "7.0 * SINGLE(A) + 1.0 * SINGLE(B) = 99.0 .",
            "-7.0 * SINGLE(A) = -99.0 .",
            "7.0 * SINGLE(A) + -34.0 * SINGLE(B) = 0.0 .",
            "0.0 * SINGLE(A) = 99.0 .",
            "2.0 * SINGLE(A) = 99.0 .",
            // "2.0 * SINGLE(A) = 99.0 .",  // Duplicate rule ignored.
            // "0.0 * SINGLE(A) = 99.0 .",  // Duplicate rule ignored.
            // "7.0 * SINGLE(A) = 99.0 .",  // Duplicate rule ignored.
            "9.0 * SINGLE(A) = 99.0 .",
            "(1.0 + (2.0 * |A|)) * SINGLE(+A) = 99.0 .",
            "7.0 * SINGLE(+A) = 99.0 .",
            "9.0 * SINGLE(+A) = 99.0 .",
            "11.0 * SINGLE(A) = 99.0 .",
            // "11.0 * SINGLE(A) = 99.0 .",  // Duplicate rule ignored.
            "21.0 * SINGLE(A) = 99.0 .",
            "-4.5 * SINGLE(A) = 99.0 .",
            // "-4.5 * SINGLE(A) = 99.0 .",  // Duplicate rule ignored.
            "1.0 * SINGLE(A) = 99.0 ."
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testNotEquals() {
        // Test both syntaxes.
        String input =
            "1: A != B & Single(A) & Single(B) >> Double(A, B) ^2\n" +
            "1: (C != D) & Single(C) & Single(D) >> Double(C, D) ^2\n" +
            "1: E!=F & Single(E) & Single(F) >> Double(E, F) ^2\n" +
            "1: (G!=H) & Single(G) & Single(H) >> Double(G, H) ^2\n" +
            "1: I - J & Single(I) & Single(J) >> Double(I, J) ^2\n" +
            "1: (K - L) & Single(K) & Single(L) >> Double(K, L) ^2\n" +
            "1: M-N & Single(M) & Single(N) >> Double(M, N) ^2\n" +
            "1: (O-P) & Single(O) & Single(P) >> Double(O, P) ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( (A != B) & SINGLE(A) & SINGLE(B) ) >> DOUBLE(A, B) ^2",
            "1.0: ( (C != D) & SINGLE(C) & SINGLE(D) ) >> DOUBLE(C, D) ^2",
            "1.0: ( (E != F) & SINGLE(E) & SINGLE(F) ) >> DOUBLE(E, F) ^2",
            "1.0: ( (G != H) & SINGLE(G) & SINGLE(H) ) >> DOUBLE(G, H) ^2",
            "1.0: ( (I != J) & SINGLE(I) & SINGLE(J) ) >> DOUBLE(I, J) ^2",
            "1.0: ( (K != L) & SINGLE(K) & SINGLE(L) ) >> DOUBLE(K, L) ^2",
            "1.0: ( (M != N) & SINGLE(M) & SINGLE(N) ) >> DOUBLE(M, N) ^2",
            "1.0: ( (O != P) & SINGLE(O) & SINGLE(P) ) >> DOUBLE(O, P) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testFilterOperatorOrder() {
        String input =
            "10 * Single(+A) + 10 * Double(B, C) = 1 . {A: Single(A) || Single(B) || Single(C)}\n" +
            "11 * Single(+A) + 11 * Double(B, C) = 1 . {A: Single(A) && Single(B) && Single(C)}\n" +
            "12 * Single(+A) + 12 * Double(B, C) = 1 . {A: Single(A) || Single(B) && Single(C)}\n" +
            "13 * Single(+A) + 13 * Double(B, C) = 1 . {A: Single(A) && Single(B) || Single(C)}\n" +

            "14 * Single(+A) + 14 * Double(B, C) = 1 . {A: (Single(A) || Single(B)) || Single(C)}\n" +
            "15 * Single(+A) + 15 * Double(B, C) = 1 . {A: Single(A) || (Single(B) || Single(C))}\n" +
            "16 * Single(+A) + 16 * Double(B, C) = 1 . {A: (Single(A) && Single(B)) && Single(C)}\n" +
            "17 * Single(+A) + 17 * Double(B, C) = 1 . {A: Single(A) && (Single(B) && Single(C))}\n" +

            "18 * Single(+A) + 18 * Double(B, C) = 1 . {A: (Single(A) || Single(B)) && Single(C)}\n" +
            "19 * Single(+A) + 19 * Double(B, C) = 1 . {A: Single(A) || (Single(B) && Single(C))}\n" +
            "20 * Single(+A) + 20 * Double(B, C) = 1 . {A: (Single(A) && Single(B)) || Single(C)}\n" +
            "21 * Single(+A) + 21 * Double(B, C) = 1 . {A: Single(A) && (Single(B) || Single(C))}\n" +
            "";
        String[] expected = new String[]{
            "10.0 * SINGLE(+A) + 10.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(A)}",
            "10.0 * SINGLE(+A) + 10.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(B)}",
            "10.0 * SINGLE(+A) + 10.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(C)}",

            "11.0 * SINGLE(+A) + 11.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(A) & SINGLE(B) & SINGLE(C) )}",

            "12.0 * SINGLE(+A) + 12.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(A)}",
            "12.0 * SINGLE(+A) + 12.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(B) & SINGLE(C) )}",

            "13.0 * SINGLE(+A) + 13.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(A) & SINGLE(B) )}",
            "13.0 * SINGLE(+A) + 13.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(C)}",

            "14.0 * SINGLE(+A) + 14.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(A)}",
            "14.0 * SINGLE(+A) + 14.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(B)}",
            "14.0 * SINGLE(+A) + 14.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(C)}",

            "15.0 * SINGLE(+A) + 15.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(A)}",
            "15.0 * SINGLE(+A) + 15.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(B)}",
            "15.0 * SINGLE(+A) + 15.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(C)}",

            "16.0 * SINGLE(+A) + 16.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(A) & SINGLE(B) & SINGLE(C) )}",

            "17.0 * SINGLE(+A) + 17.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(A) & SINGLE(B) & SINGLE(C) )}",

            "18.0 * SINGLE(+A) + 18.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(A) & SINGLE(C) )}",
            "18.0 * SINGLE(+A) + 18.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(B) & SINGLE(C) )}",

            "19.0 * SINGLE(+A) + 19.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(A)}",
            "19.0 * SINGLE(+A) + 19.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(B) & SINGLE(C) )}",

            "20.0 * SINGLE(+A) + 20.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(A) & SINGLE(B) )}",
            "20.0 * SINGLE(+A) + 20.0 * DOUBLE(B, C) = 1.0 .   {A : SINGLE(C)}",

            "21.0 * SINGLE(+A) + 21.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(A) & SINGLE(B) )}",
            "21.0 * SINGLE(+A) + 21.0 * DOUBLE(B, C) = 1.0 .   {A : ( SINGLE(A) & SINGLE(C) )}",
        };

        PSLTest.assertStringModel(input, expected, true);
    }

    @Test
    public void testArithmeticSubtraction() {
        String input =
            "Double(A, B) - Double(B, A) = 0.0 .\n" +
            "0.0 = Double(A, B) - Double(B, A) .\n" +
            "Double(A, B) + Double(B, A) = 0.0 .\n" +
            "0.0 = Double(A, B) + Double(B, A) .\n" +
            "";
        String[] expected = new String[]{
            "1.0 * DOUBLE(A, B) + -1.0 * DOUBLE(B, A) = 0.0 .",
            "-1.0 * DOUBLE(A, B) + 1.0 * DOUBLE(B, A) = 0.0 .",
            "1.0 * DOUBLE(A, B) + 1.0 * DOUBLE(B, A) = 0.0 .",
            "-1.0 * DOUBLE(A, B) + -1.0 * DOUBLE(B, A) = 0.0 ."
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testLogicalParens() {
        String input =
            "1.0: Single(A) & Single(B) >> Double(A, B) ^2\n" +
            "1.0: Single(C) & Single(D) >> Double(C, D)\n" +
            "1.0: ( Single(E) && Single(F) ) -> Double(E, F) ^2\n" +
            "1.0: ( Single(G) && Single(H) ) -> Double(G, H)\n" +

            "1.0: (Single(I)) & Single(J) >> Double(I, J)\n" +
            "1.0: (Single(K)) & (Single(L)) >> Double(K, L)\n" +
            "1.0: ((Single(M)) & (Single(N))) >> Double(M, N)\n" +
            "1.0: (Single(O)) & Single(P) >> (Double(O, P))\n" +
            "1.0: (((Single(Q)) & (Single(R))) & Double(R, Q)) >> Double(Q, R)\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( SINGLE(A) & SINGLE(B) ) >> DOUBLE(A, B) ^2",
            "1.0: ( SINGLE(C) & SINGLE(D) ) >> DOUBLE(C, D)",
            "1.0: ( SINGLE(E) & SINGLE(F) ) >> DOUBLE(E, F) ^2",
            "1.0: ( SINGLE(G) & SINGLE(H) ) >> DOUBLE(G, H)",

            "1.0: ( SINGLE(I) & SINGLE(J) ) >> DOUBLE(I, J)",
            "1.0: ( SINGLE(K) & SINGLE(L) ) >> DOUBLE(K, L)",
            "1.0: ( SINGLE(M) & SINGLE(N) ) >> DOUBLE(M, N)",
            "1.0: ( SINGLE(O) & SINGLE(P) ) >> DOUBLE(O, P)",
            "1.0: ( SINGLE(Q) & SINGLE(R) & DOUBLE(R, Q) ) >> DOUBLE(Q, R)",
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testArithmeticParens() {
        String input =
            "Single(A) + Single(B) = 0.0 .\n" +
            "(Single(A) + Single(B)) = 0.0 .\n" +
            "";
        String[] expected = new String[]{
            "1.0 * SINGLE(A) + 1.0 * SINGLE(B) = 0.0 .",
            // "1.0 * SINGLE(A) + 1.0 * SINGLE(B) = 0.0 ."  // Duplicate rule ignored.
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testArithmeticDivideByZero() {
        String[] input = new String[]{
            "Single(A) / 0 + Single(B) = 0.0 .",
            "2 / 0 * Single(A) + Single(B) = 0.0 .",
            "2 / (2 - 2) * Single(A) + Single(B) = 0.0 .",
            "Single(A) / (-1 + 1) + Single(B) = 0.0 .",
            "Single(A) / @Min[0, 1] + Single(B) = 0.0 ."
        };

        for (String rule : input) {
            try {
                PSLTest.assertRule(rule, "");
                fail("Divide by zero did not throw exception.");
            } catch (RuntimeException ex) {
                if (!(ex.getCause() instanceof ArithmeticException)) {
                    fail("Divide by zero threw a non-Arithmetic exception: " + ex.getCause() + ".");
                }
            }
        }
    }

    // Make sure that arithmetic rules properly differentiate between
    // QueryAtoms and SummationAtoms.
    @Test
    public void testArithmeticSummationAtom() {
        // GetAtom
        String input = "1.0: Double(A, B) <= 1.0 ^2";
        List<Rule> rules = PSLTest.getRules(input);

        assertEquals(1, rules.size());
        assertEquals(WeightedArithmeticRule.class, rules.get(0).getClass());

        WeightedArithmeticRule rule = (WeightedArithmeticRule)rules.get(0);
        List<SummationAtomOrAtom> atoms = rule.getExpression().getAtoms();
        assertEquals(1, atoms.size());
        assertEquals(QueryAtom.class, atoms.get(0).getClass());

        // SummationAtom
        input = "1.0: Double(+A, B) <= 1.0 ^2";
        rules = PSLTest.getRules(input);

        assertEquals(1, rules.size());
        assertEquals(WeightedArithmeticRule.class, rules.get(0).getClass());

        rule = (WeightedArithmeticRule)rules.get(0);
        atoms = rule.getExpression().getAtoms();
        assertEquals(1, atoms.size());
        assertEquals(SummationAtom.class, atoms.get(0).getClass());
    }

    @Test
    public void testNegativeWeights() {
        String input =
            "-1: Single(A) & Double(A, B) >> Single(B) ^2\n" +
            "-5.2: Single(B) & Double(B, A) >> Single(A) ^2\n";
        String[] expected = new String[]{
            "-1.0: ( SINGLE(A) & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "-5.2: ( SINGLE(B) & DOUBLE(B, A) ) >> SINGLE(A) ^2"
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testNonAlphanumericConstants() {
        String input =
            "1: Single('') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single('\\\\') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single('\\'') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single('\"') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single('a\n\t\rb') & Double(A1, B) >> Single(B) ^2\n" +
            "1: Single('a\\n\\t\\rb') & Double(A2, B) >> Single(B) ^2\n" +
            "1: Single('abc') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single('123') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single('`~!@#$%^&*()-_=+') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single('{[}]|;:') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single('<,>.?/') & Double(A, B) >> Single(B) ^2\n" +
            "1: Single(\"\") & Double(Z, B) >> Single(B) ^2\n" +
            "1: Single(\"\\\\\") & Double(Z, B) >> Single(B) ^2\n" +
            "1: Single(\"'\") & Double(Z, B) >> Single(B) ^2\n" +
            "1: Single(\"\\\"\") & Double(Z, B) >> Single(B) ^2\n" +
            "1: Single(\"a\n\t\rb\") & Double(Z1, B) >> Single(B) ^2\n" +
            "1: Single(\"a\\n\\t\\rb\") & Double(Z2, B) >> Single(B) ^2\n" +
            "1: Single(\"abc\") & Double(Z, B) >> Single(B) ^2\n" +
            "1: Single(\"123\") & Double(Z, B) >> Single(B) ^2\n" +
            "1: Single(\"`~!@#$%^&*()-_=+\") & Double(Z, B) >> Single(B) ^2\n" +
            "1: Single(\"{[}]|;:\") & Double(Z, B) >> Single(B) ^2\n" +
            "1: Single(\"<,>.?/\") & Double(Z, B) >> Single(B) ^2\n" +
            "";
        String[] expected = new String[]{
            "1.0: ( SINGLE('') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('\\\\') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('\\'') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('\"') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('a\n\t\rb') & DOUBLE(A1, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('a\n\t\rb') & DOUBLE(A2, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('abc') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('123') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('`~!@#$%^&*()-_=+') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('{[}]|;:') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('<,>.?/') & DOUBLE(A, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('\\\\') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('\\'') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('\"') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('a\n\t\rb') & DOUBLE(Z1, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('a\n\t\rb') & DOUBLE(Z2, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('abc') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('123') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('`~!@#$%^&*()-_=+') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('{[}]|;:') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
            "1.0: ( SINGLE('<,>.?/') & DOUBLE(Z, B) ) >> SINGLE(B) ^2",
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testArithmeticGroundingOnlyPredicates() {
        String input =
            "Single(A) + Single(B) + (A == B) = 0.0 .\n" +
            "Single(A) + Single(B) + (A != B) = 0.0 .\n" +
            "Single(A) + Single(B) + (A ~= B) = 0.0 .\n" +
            "Single(A) + Single(B) + (A - B) = 0.0 .\n" +
            "Single(A) + Single(B) + (A % B) = 0.0 .\n" +
            "Single(A) + Single(B) + (A ^ B) = 0.0 .\n" +
            "";
        String[] expected = new String[]{
            "1.0 * SINGLE(A) + 1.0 * SINGLE(B) + 1.0 * (A == B) = 0.0 .",
            "1.0 * SINGLE(A) + 1.0 * SINGLE(B) + 1.0 * (A != B) = 0.0 .",
            // "1.0 * SINGLE(A) + 1.0 * SINGLE(B) + 1.0 * (A != B) = 0.0 .",  // Duplicate rule ignored.
            // "1.0 * SINGLE(A) + 1.0 * SINGLE(B) + 1.0 * (A != B) = 0.0 .",  // Duplicate rule ignored.
            "1.0 * SINGLE(A) + 1.0 * SINGLE(B) + 1.0 * (A % B) = 0.0 .",
            // "1.0 * SINGLE(A) + 1.0 * SINGLE(B) + 1.0 * (A % B) = 0.0 .",  // Duplicate rule ignored.
        };

        PSLTest.assertModel(input, expected);
    }

    @Test
    public void testNumericArithmeticTerm() {
        String input = null;
        String[] expected = null;

        input =
            "Single(A) - 1.0 = Single(B) .\n" +
            "-1.0 + Single(A) = Single(B) .\n" +
            "0.0 - 1.0 + Single(A) = Single(B) .\n" +
            "-1.0 + 0.0 + Single(A) = Single(B) .\n" +

            "Single(A) = Single(B) + 1.0 .\n" +
            "Single(A) = Single(B) + 1.0 .\n" +
            "Single(A) = 1.0 + Single(B) .\n" +
            "Single(A) = 0.0 + 1.0 + Single(B) .\n" +
            "Single(A) = 0.0 - -1.0 + Single(B) .\n" +
            "";
        expected = new String[]{
            "1.0 * SINGLE(A) + -1.0 * SINGLE(B) = 1.0 .",
        };

        PSLTest.assertModel(input, expected);

        input =
            "Single(A) + 1.0 = Single(B) .\n" +
            "1.0 + Single(A) = Single(B) .\n" +
            "0.0 + 1.0 + Single(A) = Single(B) .\n" +
            "1.0 + 0.0 + Single(A) = Single(B) .\n" +

            "Single(A) = Single(B) - 1.0 .\n" +
            "Single(A) = Single(B) - 1.0 .\n" +
            "Single(A) = -1.0 + Single(B) .\n" +
            "Single(A) = 0.0 - 1.0 + Single(B) .\n" +
            "Single(A) = 0.0 + -1.0 + Single(B) .\n" +
            "";
        expected = new String[]{
            "1.0 * SINGLE(A) + -1.0 * SINGLE(B) = -1.0 .",
        };

        PSLTest.assertModel(input, expected);

        input =
            " 2.0 + Single(A) =  3.0 + Single(B) .\n" +
            " 2.0 + Single(A) = Single(B) + 3.0 .\n" +
            "Single(A) + 2.0 =  3.0 + Single(B) .\n" +
            "Single(A) + 2.0 = Single(B) + 3.0 .\n" +
            "";
        expected = new String[]{
            "1.0 * SINGLE(A) + -1.0 * SINGLE(B) = 1.0 .",
        };

        PSLTest.assertModel(input, expected);

        input =
            "-2.0 + Single(A) =  3.0 + Single(B) .\n" +
            "-2.0 + Single(A) = Single(B) + 3.0 .\n" +
            "Single(A) - 2.0 =  3.0 + Single(B) .\n" +
            "Single(A) - 2.0 = Single(B) + 3.0 .\n" +
            "";
        expected = new String[]{
            "1.0 * SINGLE(A) + -1.0 * SINGLE(B) = 5.0 .",
        };

        PSLTest.assertModel(input, expected);

        input =
            " 2.0 + Single(A) = -3.0 + Single(B) .\n" +
            " 2.0 + Single(A) = Single(B) - 3.0 .\n" +
            "Single(A) + 2.0 = -3.0 + Single(B) .\n" +
            "Single(A) + 2.0 = Single(B) - 3.0 .\n" +
            "";
        expected = new String[]{
            "1.0 * SINGLE(A) + -1.0 * SINGLE(B) = -5.0 .",
        };

        PSLTest.assertModel(input, expected);

        input =
            "-2.0 + Single(A) = -3.0 + Single(B) .\n" +
            "-2.0 + Single(A) = Single(B) - 3.0 .\n" +
            "Single(A) - 2.0 = -3.0 + Single(B) .\n" +
            "Single(A) - 2.0 = Single(B) - 3.0 .\n" +
            "";
        expected = new String[]{
            "1.0 * SINGLE(A) + -1.0 * SINGLE(B) = -1.0 .",
        };

        PSLTest.assertModel(input, expected);
    }
}
