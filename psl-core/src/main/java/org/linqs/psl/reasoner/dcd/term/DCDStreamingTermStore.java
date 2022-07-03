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
package org.linqs.psl.reasoner.dcd.term;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.reasoner.term.streaming.StreamingIterator;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A term store that iterates over ground queries directly (obviating the GroundRuleStore).
 * Note that the iterators given by this class are meant to be exhaustd (at least the first time).
 * Remember that this class will internally iterate over an unknown number of groundings.
 * So interrupting the iteration can cause the term count to be incorrect.
 */
public class DCDStreamingTermStore extends StreamingTermStore<DCDObjectiveTerm> {
    public DCDStreamingTermStore(List<Rule> rules, AtomManager atomManager, DCDTermGenerator dcdTermGenerator) {
        super(rules, atomManager, dcdTermGenerator);
    }

    @Override
    protected boolean supportsRule(Rule rule, boolean warnRules) {
        if (!super.supportsRule(rule, warnRules)) {
            return false;
        }

        // Don't allow explicit priors.
        if (rule instanceof WeightedLogicalRule) {
            Set<Atom> atomSet = ((WeightedLogicalRule)rule).getFormula().getAtoms(new HashSet<Atom>());
            if (atomSet.size() == 1) {
                return false;
            }
        } else if (rule instanceof WeightedArithmeticRule) {
            ArithmeticRuleExpression expression = ((WeightedArithmeticRule)rule).getExpression();
            if (expression.looksLikeNegativePrior()) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected StreamingIterator<DCDObjectiveTerm> getGroundingIterator() {
        return new DCDStreamingGroundingIterator(
                this, rules, atomManager, termGenerator,
                termCache, termPool, termBuffer, volatileBuffer, pageSize, numPages);
    }

    @Override
    protected StreamingIterator<DCDObjectiveTerm> getCacheIterator() {
        return new DCDStreamingCacheIterator(
                this, false, termCache, termPool,
                termBuffer, volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    protected StreamingIterator<DCDObjectiveTerm> getNoWriteIterator() {
        return new DCDStreamingCacheIterator(
                this, true, termCache, termPool,
                termBuffer, volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }
}
