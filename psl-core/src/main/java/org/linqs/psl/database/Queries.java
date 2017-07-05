/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.database;

import java.util.HashSet;
import java.util.Set;

import org.joda.time.DateTime;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.DateAttribute;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.LongAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueID;
import org.linqs.psl.model.term.Variable;

import com.google.common.base.Preconditions;

/**
 * Utility methods for common {@link Database} and {@link DatabaseQuery} tasks.
 */
public class Queries {
	
	/**
	 * Returns all GroundAtoms of a Predicate persisted in a Database.
	 * <p>
	 * GroundAtoms are retrieved by executing the query returned by
	 * {@link #getQueryForAllAtoms(Predicate)} and calling
	 * {@link Database#getAtom(Predicate, Constant...)} on each result.
	 * 
	 * @param db  the Database to query for GroundAtoms
	 * @param p  the Predicate of the GroundAtoms to return
	 * @return all GroundAtoms of p in db
	 */
	public static Set<GroundAtom> getAllAtoms(Database db, Predicate p) {
		DatabaseQuery query = getQueryForAllAtoms(p);
		ResultList results = db.executeQuery(query);
		Set<GroundAtom> atoms = new HashSet<GroundAtom>(results.size());
		for (int i = 0; i < results.size(); i++)
			atoms.add(db.getAtom(p, results.get(i)));
		return atoms;
	}

	/**
	 * Returns a DatabaseQuery that matches any GroundAtom of a Predicate.
	 * <p>
	 * The query is a {@link QueryAtom} of the given Predicate with a different
	 * {@link Variable} for each argument.
	 * 
	 * @param p  the Predicate of the Atoms to return
	 * @return the query
	 */
	public static DatabaseQuery getQueryForAllAtoms(Predicate p) {
		Variable[] args = new Variable[p.getArity()];
		for (int i = 0; i < args.length; i++)
			args[i] = new Variable("Vars" + i);
		QueryAtom atom = new QueryAtom(p, args);
		return new DatabaseQuery(atom);
	}
	
	/**
	 * Constructs a {@link QueryAtom} from raw arguments using
	 * {@link #convertArguments(Database, Predicate, Object...)}.
	 * 
	 * @param db  the Database to use to get a {@link UniqueID}
	 * @param p  the Predicate of the QueryAtom
	 * @param rawArgs  the arguments to the QueryAtom (after conversion)
	 * @return the QueryAtom
	 * @throws IllegalArgumentException  if any element of rawArgs could not be
	 *                                       converted to a valid type or is already
	 *                                       a GroundTerm of an invalid type
	 */
	public static QueryAtom getQueryAtom(Database db, Predicate p, Object... rawArgs) {
		return new QueryAtom(p, convertArguments(db, p, rawArgs));
	}
	
	/**
	 * Converts raw arguments to {@link Term Terms} that fit a given Predicate.
	 * <p>
	 * Returns Terms such that they match the ArgumentTypes of a Predicate. Any
	 * raw argument that is already a Term is returned as is. Any other raw
	 * argument will be used to construct a {@link Constant} of the appropriate
	 * type if possible.
	 * 
	 * @param db  the Database to use to get a {@link UniqueID}
	 * @param p  the Predicate to match the arguments to
	 * @param rawArgs  the arguments to convert
	 * @return the converted terms
	 * @throws IllegalArgumentException  if any element of rawArgs could not be
	 *                                       converted to a valid type or is already
	 *                                       a GroundTerm of an invalid type
	 */
	public static Term[] convertArguments(Database db, Predicate p, Object... rawArgs) {
		Preconditions.checkArgument(p.getArity()==rawArgs.length);
		Term[] args = new Term[rawArgs.length];
		ConstantType type;
		for (int i=0;i<rawArgs.length;i++) {
			type = p.getArgumentType(i);
			if (rawArgs[i] instanceof Variable) {
				args[i] = (Variable) rawArgs[i]; 
			}
			else if (rawArgs[i] instanceof Constant && type.isInstance((Constant) rawArgs[i])) {
				args[i] = (Constant) rawArgs[i];
			}
			else {
				switch (type) {
					case UniqueID:
						args[i] = db.getUniqueID(rawArgs[i]);
						break;
					case String:
						args[i] = new StringAttribute(rawArgs[i].toString());
						break;
					case Double:
						if (rawArgs[i] instanceof Double)
							args[i] = new DoubleAttribute((Double) rawArgs[i]);
						else if (rawArgs[i] instanceof String)
							args[i] = new DoubleAttribute(Double.parseDouble((String) rawArgs[i]));
						else
							throw new IllegalArgumentException("Could not convert raw arg " + i + " to Double.");
						break;
					case Integer:
						if (rawArgs[i] instanceof Integer)
							args[i] = new IntegerAttribute((Integer) rawArgs[i]);
						else if (rawArgs[i] instanceof String)
							args[i] = new IntegerAttribute(Integer.parseInt((String) rawArgs[i]));
						else
							throw new IllegalArgumentException("Could not convert raw arg " + i + " to Integer.");
						break;
					case Long:
						if (rawArgs[i] instanceof Long)
							args[i] = new LongAttribute((Long) rawArgs[i]);
						else
							throw new IllegalArgumentException("Could not convert raw arg " + i + " to Long.");
					case Date:
						try {
							args[i] = new DateAttribute(new DateTime(rawArgs[i]));
						} catch (IllegalArgumentException e) {
							throw new IllegalArgumentException("Could not convert raw arg " + i + " to Date.");
						}
						break;
					default:
						throw new IllegalArgumentException("Unrecognized argument type " + type + " at index " + i + ".");
				}
			}
		}
		return args;
	}
}
