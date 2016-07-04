/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package edu.umd.cs.psl.model.rule.arithmetic;

import java.util.Map;

import edu.umd.cs.psl.application.groundrulestore.GroundRuleStore;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.rule.AbstractRule;
import edu.umd.cs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import edu.umd.cs.psl.model.rule.arithmetic.expression.SummationVariable;

/**
 * Base class for all (first order, i.e., not ground) arithmetic rules.
 * 
 * @author Stephen Bach
 */
public class AbstractArithmeticRule extends AbstractRule {
	
	enum Comparator {EQUAL, GREATER_EQUAL, LESS_EQUAL}
	
	protected final ArithmeticRuleExpression expression;
	protected final Map<SummationVariable, Formula> selects;
	
	public AbstractArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> selectStatements) {
		this.expression = expression;
		this.selects = selectStatements;
	}
	
	@Override
	public void groundAll(AtomManager atomManager, GroundRuleStore grs) {
		//DatabaseQuery query = new DatabaseQuery(new Conjunction(expression.getAtoms()));
		
		/* Collects regular variables to to project query onto them */
		/*for (Atom atom : expression.getAtoms()) {
			for (Term term : atom.getArguments()) {
				if (term instanceof Variable && !(term instanceof SummationVariable)) {
					query.getProjectionSubset().add((Variable) term);
				}
			}
		}
		
		ResultList groundings = atomManager.executeQuery(query);
		
		for (int i = 0; i < groundings.size(); i++) {
			
		}*/
		
		throw new UnsupportedOperationException("Grounding arithmetic rules is not supported.");
	}

	@Override
	protected void notifyAtomEvent(AtomEvent event, GroundRuleStore grs) {
		throw new UnsupportedOperationException("Arithmetic rules do not support atom events.");
	}

	@Override
	protected void registerForAtomEvents(AtomEventFramework eventFramework) {
		throw new UnsupportedOperationException("Arithmetic rules do not support atom events.");
	}

	@Override
	protected void unregisterForAtomEvents(AtomEventFramework eventFramework) {
		throw new UnsupportedOperationException("Arithmetic rules do not support atom events.");
	}

}
