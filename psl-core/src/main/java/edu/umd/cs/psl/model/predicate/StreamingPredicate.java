package edu.umd.cs.psl.model.predicate;

import edu.umd.cs.psl.database.Stream;
import edu.umd.cs.psl.model.argument.ArgumentType;

public class StreamingPredicate extends StandardPredicate {

	private Stream s;

	public StreamingPredicate(String name, ArgumentType[] types, Stream s) {
		super(name, types);
		this.s = s;
	}
	
	public void setStream(Stream s){
		this.s = s;
	}
	
	public Stream getStream(){
		return s;
	}

}
