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
package org.linqs.psl.evaluation.debug;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.AtomCache;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.database.Queries;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import de.mathnbits.io.BasicUserInteraction;

public class CmdDebugger implements Debugger {

	private static final String quit = "q";
	private static final String queryPredicates = "all";
	private static final String queryAtom = "atom";
	private static final String help = "h";
	
	private static final DecimalFormat valueFormatter = new DecimalFormat("#.##");

	private final Database db;
	private final AtomCache cache;
	private final PredicateFactory predicateFactory;
	
	private Map<Integer,Atom> atomHandles;
	
	public CmdDebugger(Database db) {
		this.db = db;
		cache = db.getAtomCache();
		predicateFactory = PredicateFactory.getFactory();
	}
	
	@Override
	public void start() {
		println("Debugger started. Enter '"+quit+"' to exit and '"+help+"' for help.");
		String cmd;
		while (!(cmd = readCmd()).equalsIgnoreCase(quit)) {
			Integer atomID = null;
			try {
			  atomID = Integer.parseInt(cmd);
			} catch (NumberFormatException e) {}
			
			if (atomID!=null) {
				if (atomHandles==null) error("No atom handles defined in context!");
				else {
					if (!atomHandles.containsKey(atomID)) error("Atom handle ["+atomID+"] not defined in context!");
					else {
						printAtom(atomHandles.get(atomID));
					}
				}
			} else if (cmd.toLowerCase().startsWith(queryPredicates)) {
				String query = cmd.substring(queryPredicates.length()).trim();
				queryPredicate(query);
			} else if (cmd.toLowerCase().startsWith(queryAtom)) {
				String query = cmd.substring(queryAtom.length()).trim();
				queryAtom(query);
			} else if (cmd.equalsIgnoreCase(help)) {
				printHelp();
			} else {
				error("Unrecognized command!");
			}
		}
		println("Exiting Debugger.");
	}
	
	private void printAtom(Atom atom) {
		println(AtomPrinter.atomDetails(atom));
		if (atom instanceof GroundAtom)
			printGroundKernels(((GroundAtom) atom).getRegisteredGroundKernels());
	}
	
	private String printGroundKernels(GroundRule e) {
		String ret = e.toString();
		if (e instanceof WeightedGroundRule) {
			ret += " V="+valueFormatter.format(((WeightedGroundRule)e).getIncompatibility());
		}
		return ret;
	}
	
	private void printGroundKernels(Collection<GroundRule> evidences) {
		BiMap<Integer,Atom> biatomHandles = HashBiMap.create();
		int counter = 1;
		for (GroundRule e : evidences) {
			String str = printGroundKernels(e);
			StringBuilder dep = new StringBuilder();
			dep.append("--> Affected Atoms: ");
			Collection<GroundAtom> atoms = e.getAtoms();
			for (Atom a : atoms) {
				int atomNr = -1;
				if (biatomHandles.containsValue(a)) {
					atomNr = biatomHandles.inverse().get(a);
				} else {
					atomNr = counter;
					counter++;
					biatomHandles.put(atomNr, a);
				}
				str.replace(a.toString(), a.toString()+ " ["+atomNr+"]");
				dep.append(AtomPrinter.atomDetails(a)).append(" ["+atomNr+"]").append(" , ");
			}
			println(str);
			println(dep.toString());
		}
		atomHandles = biatomHandles;
	}
	
	private void queryPredicate(String predicate) {
		try {
			Predicate p = predicateFactory.getPredicate(predicate);
			printAtoms(getConsideredAtoms(p));
		} catch (IllegalArgumentException e) {
			error(e.getMessage());
		}
	}
	
	private void printAtoms(List<GroundAtom> atoms) {
		if (atoms.isEmpty()) println("No atoms found for query");
		else {
			atomHandles = new HashMap<Integer,Atom>(atoms.size());
			int counter = 1;
			for (Atom atom : atoms) {
				println(AtomPrinter.atomDetails(atom) + "  ["+counter+"]");
				atomHandles.put(counter, atom);
				counter++;
			}
		}
	}
	
	private void queryAtom(String query) {
		String[] queryParts = query.split(" ");
		if (queryParts.length<2) {
			error("Invalid atom query!");
		} else {
			String predicate = queryParts[0];
			Object[] args = new Object[queryParts.length-1];
			for (int i=1;i<queryParts.length;i++) {
				try {
					args[i-1]=(Integer)Integer.parseInt(queryParts[i]);
					continue;
				} catch (NumberFormatException e) {}
				args[i-1]=(String)queryParts[i];
			}
			try {
				Predicate p = predicateFactory.getPredicate(predicate);
				printAtoms(getConsideredAtoms(p, Queries.convertArguments(db, p, args)));
			} catch (IllegalArgumentException e) {
				error(e.getMessage());
			}
		}
	}
	
	private List<GroundAtom> getConsideredAtoms(Predicate p) {
		Term[] args = new Term[p.getArity()];
		for (int i = 0; i < args.length; i++) {
			args[i] = new Variable("Arg_" + i);
		}
		return getConsideredAtoms(p, args);
	}
	
	private List<GroundAtom> getConsideredAtoms(Predicate p, Term[] args) {
		if (!(p instanceof StandardPredicate))
			throw new IllegalArgumentException("Only StandardPredicates can be retrieved.");
		ResultList res  = db.executeQuery(new DatabaseQuery(new QueryAtom(p, args)));
		List<GroundAtom> atoms = new ArrayList<GroundAtom>();
		for (int i=0;i<res.size();i++) {
			GroundAtom a = cache.getCachedAtom(new QueryAtom(p,res.get(i)));
			if (a!=null) atoms.add(a);
		}
		return atoms;
	}
	
	private void printHelp() {
		println("'all <predicate>'	-- display all atoms of that predicate");
		println("'atom <predicate> <atomArgument>+'	-- display all atoms of that predicate with matching arguments. Note that the number of arguments must match the predicate arity. Use * as an argument wildcard.");
		println("'<number>'			-- display atom with this number handle presented on the current screen");
	}
	
	private String readCmd() {
		return readCmd(null);
	}
	
	private String readCmd(String out) {
		System.out.print(">> ");
		if (out!=null) System.out.print(out+" ");
		return BasicUserInteraction.readline().trim();
	}
	
	private void println(String out) {
		System.out.println(out);
	}
	
	private void error(String msg) {
		println("ERROR: "+msg);
	}
	
	
}
