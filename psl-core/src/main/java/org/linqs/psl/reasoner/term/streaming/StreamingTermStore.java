/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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
package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.config.Config;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.SystemUtils;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TEST(eriq) Make abstract and pull in most the functionality.

// TEST(eriq): Absorb a lot from DCDTermStore

public interface StreamingTermStore<T extends ReasonerTerm> extends TermStore<T, RandomVariableAtom> {
    public String getTermPagePath(int index);
    public String getLagrangePagePath(int index);
    public void initialIterationComplete(int termCount, int numPages, ByteBuffer termBuffer, ByteBuffer lagrangeBuffer);
    public void cacheIterationComplete();
}
