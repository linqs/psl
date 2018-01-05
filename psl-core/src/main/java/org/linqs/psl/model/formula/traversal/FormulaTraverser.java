/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.model.formula.traversal;

import org.linqs.psl.model.atom.Atom;
/**
 * Interface specifying the methods needed to properly traverse a formula
 */
public interface FormulaTraverser {

	public boolean beforeConjunction();
	
	public void afterConjunction(int noFormulas);
	
	public boolean beforeDisjunction();
	
	public void afterDisjunction(int noFormulas);
	
	public boolean beforeNegation();
	
	public void afterNegation();
	
	public void visitAtom(Atom atom);
}
