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
package org.linqs.psl.model.term;

/**
 * An {@link Attribute} that encapsulates an Integer.
 */
public class IntegerAttribute implements Attribute {

	private final Integer value;
	
	/**
	 * Constructs an Integer attribute from an Integer
	 * 
	 * @param value  Integer to encapsulate
	 */
	public IntegerAttribute(Integer value) {
		this.value = value;
	}
	
	/**
	 * @return the encapsulated Integer as a String in single quotes
	 */
	@Override
	public String toString() {
		return "'" + value + "'";
	}
	
	@Override
	public Integer getValue() {
		return value;
	}
	
	@Override
	public int hashCode() {
		return value.hashCode();
	}
	
	/**
	 * An IntegerAttribute is equal to another Object if that Object is an IntegerAttribute
	 * and their values are equal.
	 */
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(oth instanceof IntegerAttribute)) return false;
		return value.equals(((IntegerAttribute)oth).getValue());  
	}

	@Override
	public int compareTo(Constant o) {
		if (o instanceof IntegerAttribute)
			return value - ((IntegerAttribute) o).value;
		else
			return this.getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
	}

}
