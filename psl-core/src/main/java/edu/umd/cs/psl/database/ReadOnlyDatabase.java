package edu.umd.cs.psl.database;

import java.util.HashSet;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class ReadOnlyDatabase {

	// The database this class wraps around
	private final Database db;
	
	public ReadOnlyDatabase(Database db) {
		this.db = db;
		
	}
	
	public GroundAtom getAtom(Predicate p, GroundTerm... arguments) {
		if (p instanceof FunctionalPredicate) {
			return db.getAtom(p, arguments);
		} else if (p instanceof StandardPredicate) {
			if (db.isClosed((StandardPredicate)p))
				return db.getAtom(p, arguments);
			else
				throw new IllegalArgumentException("Can only call getAtom() on a closed or functional predicate.");
		} else
			throw new IllegalArgumentException("Unknown predicate type: " + p.getClass().getName());
	}

	public ResultList executeQuery(DatabaseQuery query) {
		HashSet<Atom> atoms = new HashSet<Atom>();
		
		for (Atom atom : query.getFormula().getAtoms(atoms)) {
			if (atom.getPredicate() instanceof FunctionalPredicate)
				continue;
			else if (atom.getPredicate() instanceof StandardPredicate) {
				if (!db.isClosed((StandardPredicate)atom.getPredicate()))
					throw new IllegalArgumentException("Can only perform queries over closed or functional predicates.");
				else
					continue;
			} else
				throw new IllegalArgumentException("Unknown predicate type: " + atom.getPredicate().getClass().getName());
		}
		
		return db.executeQuery(query);
	}
	
	public UniqueID getUniqueID(Object key) {
		return db.getUniqueID(key);
	}
}
