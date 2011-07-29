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

import java.util.*;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.EntitySet;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.predicate.Predicate;

public class MemoryGroupAtom extends ComplexMemoryAtom {

	MemoryGroupAtom(Predicate p, GroundTerm[] args) {
		super(p, args, AtomStatus.UnconsideredRV);
	}

	@Override
	public Collection<Atom> getAtomsInGroup(AtomManager atommanager,  DatabaseAtomStoreQuery db) {
		assert isConsidered();
		List<Atom> result = new ArrayList<Atom>();
		expandAtom(0,new GroundTerm[getArity()],atommanager,db,result);
		return result;
	}
	
	private void expandAtom(int argpos,GroundTerm[] args, AtomManager atommanager, DatabaseAtomStoreQuery db, Collection<Atom> result) {
		if (argpos>=args.length) {
			//Create atom
			GroundTerm[] copy = args.clone();
			Atom atom = atommanager.getAtom(getPredicate(), copy);
			assert atom.isConsidered();
			result.add(atom);
		} else {
			GroundTerm t = (GroundTerm)getArguments()[argpos];
			if (t instanceof EntitySet) {
				for (Entity e : ((EntitySet)t).getEntities(db)) {
					args[argpos]=e;
					expandAtom(argpos+1,args,atommanager,db,result);
				}
			} else {
				args[argpos]=t;
				expandAtom(argpos+1,args,atommanager,db,result);
			}
		}
	}

	@Override
	public boolean isAtomGroup() {
		return true;
	}

	@Override
	public void activate() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void deactivate() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void makeCertain() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void revokeCertain() {
		throw new UnsupportedOperationException();
	}
	
}
