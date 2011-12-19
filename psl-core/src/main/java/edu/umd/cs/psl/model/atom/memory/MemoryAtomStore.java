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
package edu.umd.cs.psl.model.atom.memory;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import de.mathnbits.util.RetrievalSet;
import edu.umd.cs.psl.database.AtomRecord;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.argument.EntitySet;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

class MemoryAtomStore {

	private final Database db;

	
	private final RetrievalSet<Atom> atomCache;
	
	MemoryAtomStore(Database _db) {
		db = _db;

		atomCache = new RetrievalSet<Atom>();
	}

	void store(Atom atom) {
		assert atom.isInferenceAtom();
		if (!atom.isAtomGroup()) {
			db.persist(atom);
		}
	}
	
	Database getDatabase() {
		return db;
	}
	
	void free(Atom atom) {
		Preconditions.checkArgument(atom.isUnconsidered() || !atom.isDefined());
		assert atom.getNumRegisteredGroundKernels()==0;
		if (!atomCache.remove(atom))
			throw new IllegalArgumentException("Cannot free non-existant atom: " + atom);
		atom.delete();
	}

	Atom getAtom(Predicate p, GroundTerm[] arguments) {
		Atom atom = new MemoryAtom(p,arguments,AtomStatus.Undefined);
		Atom oldAtom = atomCache.get(atom);
		if (oldAtom!=null) {
			return oldAtom;
		} else {
			if (p instanceof StandardPredicate) {
				if (containsEntitySet(arguments)) {
					return initializeGroupAtom(p,arguments);
				} else {
					AtomRecord res = db.getAtom(p,arguments);
					return this.initializeAtom(p, arguments, res);
				}
			} else if (p instanceof FunctionalPredicate){
				return initializeAtom(p, arguments, 
						new AtomRecord(((FunctionalPredicate)p).computeValue(arguments), 
						ConfidenceValues.getMax(),
						AtomRecord.Status.FACT
						));
			} else throw new IllegalArgumentException("Unsupported predicate: " + p);
		}

	}
	
	static boolean containsEntitySet(GroundTerm[] terms) {
		for (int i=0;i<terms.length;i++) {
			if (terms[i] instanceof EntitySet) return true;
		}
		return false;
 	}
	
	Atom getConsideredAtom(Predicate p, GroundTerm[] arguments) {
		Atom atom = new MemoryAtom(p,arguments,AtomStatus.Undefined);
		return atomCache.get(atom);
	}
	
	int getNumAtoms(final Set<AtomStatus> stati) {
		int result = 0;
		for (Atom atom : atomCache) {
			if (stati.contains(atom.getStatus())) result++;
		}
		return result;
	}
	
	Iterable<Atom> getAtoms(final Set<AtomStatus> stati) {
		return Iterables.filter(atomCache, new com.google.common.base.Predicate<Atom>(){
			@Override
			public boolean apply(Atom atom) {
				return stati.contains(atom.getStatus());
			}
		});
	}

	Iterable<Atom> getAtoms(final AtomStatus status) {
		return getAtoms(ImmutableSet.of(status));
	}
	
	private Atom addToCache(Atom a) {
		assert !atomCache.contains(a);
		atomCache.add(a);
		return a;
	}
	
	private Atom initializeAtom(Predicate p, GroundTerm[] terms, AtomStatus status, double value, double confidence) {
		return new MemoryAtom(p, terms, status, value, confidence);
	}
	
	private Atom initializeAtom(Predicate p, GroundTerm[] terms, AtomRecord record) {
		AtomStatus s = null;
		switch(record.getStatus()) {
		case CERTAINTY:
			s = AtomStatus.UnconsideredCertainty;
			break;
		case FACT:
			s = AtomStatus.UnconsideredFact;
			break;
		case RV:
			s = AtomStatus.UnconsideredRV;
			break;
		}
		return addToCache(initializeAtom(p, terms, s, record.getValue(), record.getConfidence()));
	}
	
	private Atom initializeGroupAtom(Predicate p, GroundTerm[] terms) {
		return addToCache(new MemoryGroupAtom(p,terms));
	}
	
}
