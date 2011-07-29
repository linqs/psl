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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.model.argument.ArgumentFactory;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.TemplateAtom;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class DatabaseQuery {

	protected final Database database;;
	
	public DatabaseQuery(Database db) {
		database = db;
	}
	
	public ResultList query(Formula f, VariableAssignment partialGrounding, List<Variable> projectTo) {
		return database.query(f, partialGrounding, projectTo);
	}
	
	public ResultList query(Formula f, VariableAssignment partialGrounding) {
		return database.query(f, partialGrounding);
	}
	
	public ResultList query(Formula f, List<Variable> projectTo) {
		return database.query(f, projectTo);
	}
	
	public ResultList query(Formula f) {
		return database.query(f);
	}
	
	public ResultListValues getFacts(Predicate p, Term[] arguments) {
		return database.getFacts(p, arguments);
	}
	
	public Map<PredicatePosition,ResultListValues> getAllFactsWith(GroundTerm e) {
		return database.getAllFactsWith(e);
	}

	public Set<Entity> getEntities(ArgumentType type) {
		return database.getEntities(type);
	}
	
	
	public ResultList getAtoms(Predicate p) {
		if (!(p instanceof StandardPredicate)) throw new IllegalArgumentException("Only standard predicates can be retrieved!");
		Variable[] args = new Variable[p.getArity()];
		for (int i=0;i<p.getArity();i++) args[i]=new Variable("V"+i);
		Atom query = new TemplateAtom(p,args);
		return database.query(query,Arrays.asList(args));
	}
	
	public boolean isClosed(Predicate p) {
		if (p instanceof FunctionalPredicate) return true;
		return database.isClosed(p);
	}
	
	public static Atom getQueryAtom(Database db, Predicate p, Object...terms) {
		Preconditions.checkArgument(p.getArity()==terms.length);
		Term[] args = new Term[terms.length];
		for (int i=0;i<terms.length;i++) {
			if (terms[i] instanceof String) {
				String term = (String)terms[i];
				if (term.equals(term.toUpperCase())) {
					args[i] =  new Variable(term);
				} else {
					if (p.getArgumentType(i)==ArgumentTypes.Text) {
						args[i] = ArgumentFactory.getAttribute(term);
					} else {
						assert p.getArgumentType(i).isEntity();
						args[i] = db.getEntity(term,p.getArgumentType(i));
					}
				}
			} else if (terms[i] instanceof Number) {
				args[i] = ArgumentFactory.getAttribute(((Number)terms[i]).doubleValue());
			} else if (p.getArgumentType(i).isEntity()) {
				args[i] = db.getEntity(terms[i],p.getArgumentType(i));
			} else throw new IllegalArgumentException("Argument type not supported: " + terms[i]);
		}
		return new TemplateAtom(p,args);
	}
	
}
