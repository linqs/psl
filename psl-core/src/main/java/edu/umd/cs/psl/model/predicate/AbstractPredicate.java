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
package edu.umd.cs.psl.model.predicate;

import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.predicate.type.PredicateType;


/**
 * This class implements a generic predicate, hence it is abstract since only specific implementations
 * of a predicate, i.e. boolean or composite, can be instantiated.
 * 
 * A predicate is defined by its name and its signature, i.e. types of its arguments. Predicates can be
 * defined in different namespaces to avoid naming conflicts. Predicates must be constructed using the 
 * static create() methods.
 * 
 * @author Matthias Broecheler
 *
 */
abstract class AbstractPredicate implements Predicate {
	
	private final String predicateName;
	
	private final ArgumentType[] types;
	
	protected final PredicateType type;
	
	AbstractPredicate(String name, PredicateType _type, ArgumentType[] _types) {
		types = _types;
		type = _type;
		predicateName = name;
	}
	
	@Override
	public int getArity() {
		return types.length;
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(predicateName).append("(");
		for (int i=0;i<types.length;i++) {
			if (i>0) s.append(", ");
			if (types[i]!=null) s.append(types[i]);
			else s.append("?");
		}
		return s.append(")").toString();
	}
	
	@Override
	public ArgumentType getArgumentType(int position) {
		return types[position];
	}
	
	@Override
	public String getName() {
		return predicateName;
	}
	
	@Override
	public PredicateType getType() {
		return type;
	}

	@Override
	public int getNumberOfValues() {
		return type.getNumberOfValues();
	}
	

	@Override
	public double[] getDefaultValues() {
		return type.getDefaultValues();
	}

	@Override
	public double[] getStandardValues() {
		return type.getStandardValues();
	}

//	@Override
//	public int getNumberOfActivationParameters() {
//		return type.getNumberOfActivationParameters();
//	}

	@Override
	public String getValueName(int pos) {
		return type.getValueName(pos);
	}

	@Override
	public boolean validValue(int pos, double value) {
		return type.validValue(pos, value);
	}
	
	@Override
	public boolean validValues(double[] values) {
		return validValues(this,values);
	}
	
	public static boolean validValues(Predicate p, double[] values) {
		if (values.length!=p.getNumberOfValues()) return false;
		for (int i=0;i<values.length;i++) {
			if (!p.validValue(i,values[i])) return false;
		}
		return true;
	}
	
	
	/* The predicate factory ensures that names uniquely identify predicates within the same {@link PredicateFactory}.
	 * Hence, equality of predicates can be determined by identity;
	 */
	@Override
	public int hashCode() {
		return super.hashCode();
		//return predicateName.hashCode()*17;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		else return false;
//		if (oth==null || !(getClass().isInstance(oth)) ) return false;
//		return predicateName.equals(((AbstractPredicate)oth).predicateName);  
	}
	
}
