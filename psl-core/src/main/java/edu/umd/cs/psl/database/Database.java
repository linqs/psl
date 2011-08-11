/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.database;

import java.util.*;

import edu.umd.cs.psl.model.argument.ArgumentFactory;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.*;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.Predicate;

public interface Database {

	public ResultAtom getAtom(Predicate p, GroundTerm[] arguments);
	
	public ResultListValues getFacts(Predicate p, Term[] arguments);
	
	public Map<PredicatePosition,ResultListValues> getAllFactsWith(GroundTerm e);
	
	public void persist(Atom atom);
	
	public ResultList query(Formula f, VariableAssignment partialGrounding, List<Variable> projectTo);
	
	public ResultList query(Formula f, VariableAssignment partialGrounding);
	
	public ResultList query(Formula f, List<Variable> projectTo);
	
	public ResultList query(Formula f);
	
	public void registerDatabaseEventObserver(DatabaseEventObserver atomEvents);
	
	public void deregisterDatabaseEventObserver(DatabaseEventObserver atomEvents);
	
//	public void setAtomStore(AtomStore store);
//	
//	public AtomStore getAtomStore();
	
	public Entity getEntity(Object entity, ArgumentType type);
	
	public Set<Entity> getEntities(ArgumentType type);
	
	public int getNumEntities(ArgumentType type);
	
	public boolean isClosed(Predicate p);
	
	public void close();
}
