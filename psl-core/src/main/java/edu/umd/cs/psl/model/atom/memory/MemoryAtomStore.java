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
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.ResultAtom;
import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.argument.EntitySet;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class MemoryAtomStore implements AtomStore {

	private final Database db;

	
	private final RetrievalSet<Atom> atomCache;
	
	public MemoryAtomStore(Database _db) {
		db = _db;

		atomCache = new RetrievalSet<Atom>();
	}

	@Override
	public void store(Atom atom) {
		assert atom.isInferenceAtom();
		if (!atom.isAtomGroup()) {
			db.persist(atom);
		}
	}
	
	@Override
	public Database getDatabase() {
		return db;
	}
	
	@Override
	public void free(Atom atom) {
		Preconditions.checkArgument(atom.isUnconsidered() || !atom.isDefined());
		assert atom.getNumRegisteredGroundKernels()==0;
		if (!atomCache.remove(atom)) throw new IllegalArgumentException("Cannot free non-existant atom: " + atom);
		atom.delete();
	}

	@Override
	public Atom getAtom(Predicate p, GroundTerm[] arguments) {
		Atom atom = new MemoryAtom(p,arguments,AtomStatus.Undefined);
		Atom oldAtom = atomCache.get(atom);
		if (oldAtom!=null) {
			return oldAtom;
		} else {
			if (p instanceof StandardPredicate) {
				if (containsEntitySet(arguments)) {
					return initializeGroupAtom(p,arguments);
				} else {
					ResultAtom res = db.getAtom(p,arguments);
					return this.initializeAtom(p, arguments, res);
				}
			} else if (p instanceof FunctionalPredicate){
				return initializeAtom(p, arguments, 
						new ResultAtom(((FunctionalPredicate)p).computeValues(arguments), 
						ConfidenceValues.getMaxConfidence(p.getNumberOfValues()),
						ResultAtom.Status.FACT
						));
			} else throw new IllegalArgumentException("Unsupported predicate: " + p);
		}

	}
	
	public static boolean containsEntitySet(GroundTerm[] terms) {
		for (int i=0;i<terms.length;i++) {
			if (terms[i] instanceof EntitySet) return true;
		}
		return false;
 	}
	
	@Override
	public Atom getConsideredAtom(Predicate p, GroundTerm[] arguments) {
		Atom atom = new MemoryAtom(p,arguments,AtomStatus.Undefined);
		return atomCache.get(atom);
	}
	
	public int getNumAtoms(final Set<AtomStatus> stati) {
		int result = 0;
		for (Atom atom : atomCache) {
			if (stati.contains(atom.getStatus())) result++;
		}
		return result;
	}
	
	public Iterable<Atom> getAtoms(final Set<AtomStatus> stati) {
		return Iterables.filter(atomCache, new com.google.common.base.Predicate<Atom>(){
			@Override
			public boolean apply(Atom atom) {
				return stati.contains(atom.getStatus());
			}
		});
	}

	@Override
	public Iterable<Atom> getAtoms(final AtomStatus status) {
		return getAtoms(ImmutableSet.of(status));
	}
	
//	public static Atom initializeAtom(Predicate p, GroundTerm[] terms) {
//		if (p instanceof FunctionalPredicate) {
//			//Compute once
//			if (p.getArity()!=terms.length) throw new IllegalArgumentException("Number of arguments does not match!");
//			double[] softValues = ((FunctionalPredicate)p).computeValues(terms);
//			return initializeFactAtom(p,terms,softValues,ConfidenceValues.getMaxConfidence(softValues.length));
//		} else 
//			return new Atom(p,terms);
//	}
	
	private Atom addToCache(Atom a) {
		assert !atomCache.contains(a);
		atomCache.add(a);
		return a;
	}
	
	private Atom initializeAtom(Predicate p, GroundTerm[] terms, AtomStatus status, double[] values, double[] confidences) {
		if (p.getNumberOfValues()==1) {
			Preconditions.checkArgument(values.length==1);
			Preconditions.checkArgument(confidences.length==1);
			return new SimpleMemoryAtom(p,terms,status,values[0],confidences[0]);
		} else {
			return new ComplexMemoryAtom(p,terms,status,values,confidences);
		}
	}
	
	private Atom initializeAtom(Predicate p, GroundTerm[] terms, ResultAtom res) {
		double[] values = res.getValues();
		double[] confidences = res.getConfidences();
		AtomStatus s = null;
		switch(res.getStatus()) {
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
		if (values==null) values = p.getDefaultValues();
		if (confidences==null) confidences = ConfidenceValues.getDefaultConfidence(p.getNumberOfValues());
		return addToCache(initializeAtom(p,terms,s,values,confidences));
	}
	
	
//	@Override
//	public Atom initializeFactAtom(Predicate p, GroundTerm[] terms, double[] values, double[] confidences) {
//		return addToCache(initializeAtom(p,terms,AtomStatus.UnconsideredFact,values,confidences));
//	}
//	
//	@Override
//	public Atom initializeCertaintyAtom(Predicate p, GroundTerm[] terms, double[] values, double[] confidences) {
//		return addToCache(initializeAtom(p,terms,AtomStatus.UnconsideredCertainty,values,confidences));
//	}
//	
//	@Override
//	public Atom initializeRVAtom(Predicate p, GroundTerm[] terms, double[] values, double[] confidences) {
//		return addToCache(initializeAtom(p,terms,AtomStatus.UnconsideredRV,values,confidences));
//	}
//	
//	@Override
//	public Atom initializeRVAtom(Predicate p, GroundTerm[] terms) {
//		return initializeRVAtom(p,terms,p.getDefaultValues(),ConfidenceValues.getDefaultConfidence(p.getNumberOfValues()));
//	}
	
	private Atom initializeGroupAtom(Predicate p, GroundTerm[] terms) {
		return addToCache(new MemoryGroupAtom(p,terms));
	}


	
}
