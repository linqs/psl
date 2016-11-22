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
 * An {@link Attribute} that encapsulates a String.
 */
public class StringAttribute implements Attribute {

	private final String value;
	
	/**
	 * Constructs a StringAttribute from a String
	 * 
	 * @param value  String to encapsulate
	 */
	public StringAttribute(String value) {
		this.value = value;
	}
	
	/**
	 * @return the encapsulated String in single quotes, truncated to 30 characters
	 */
	@Override
	public String toString() {
		if (value.length() > 28)
			return "'" + value.substring(0, Math.min(value.length(), 25)) + "...'";
		else
			return "'" + value + "'";
	}
	
	@Override
	public String getValue() {
		return value;
	}
	
	@Override
	public int hashCode() {
		return value.hashCode();
	}
	
	/**
	 * A StringAttribute is equal to another Object if that Object is a TextAttribute
	 * and their values are equal.
	 */
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(oth instanceof StringAttribute) ) return false;
		return value.equals(((StringAttribute) oth).getValue());  
	}

	@Override
	public int compareTo(Constant o) {
		if (o instanceof StringAttribute)
			return value.compareTo(((StringAttribute) o).value);
		else
			return this.getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
	}
	
}
