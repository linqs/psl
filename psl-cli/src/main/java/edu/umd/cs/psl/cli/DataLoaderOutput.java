package edu.umd.cs.psl.cli;

import java.util.Set;

import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class DataLoaderOutput {
	private Set<StandardPredicate> closedPredicates;

	public DataLoaderOutput(Set<StandardPredicate> closedPredicates){
		this.closedPredicates = closedPredicates;
	}
	public Set<StandardPredicate> getClosedPredicates(){
		return closedPredicates;
	}
}
