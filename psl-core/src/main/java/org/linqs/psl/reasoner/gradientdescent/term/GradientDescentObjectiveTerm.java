/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
package org.linqs.psl.reasoner.gradientdescent.term;

import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.ReasonerTerm;

public class GradientDescentObjectiveTerm extends ReasonerTerm {
    public GradientDescentObjectiveTerm(WeightedRule rule, boolean squared, boolean hinge, Hyperplane hyperplane) {
        super(hyperplane, rule, squared, hinge, null);
    }

    public GradientDescentObjectiveTerm(short size, float[] coefficients, float constant, int[] atomIndexes,
                                        Rule rule, boolean squared, boolean hinge, FunctionComparator comparator) {
        super(size, coefficients, constant, atomIndexes, rule, squared, hinge, comparator);
    }

    @Override
    public SGDObjectiveTerm copy() {
        return new SGDObjectiveTerm(size, coefficients, constant, atomIndexes, rule, squared, hinge, comparator);
    }
}
