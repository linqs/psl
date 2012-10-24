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
package edu.umd.cs.psl.model.argument;

/**
 * A String {@link Attribute}.
 */
public class TextAttribute implements Attribute {

	private final String value;
	
	/**
	 * Constructs a TextAttribute from a String
	 * 
	 * @param value  String to encapsulate
	 */
	public TextAttribute(String value) {
		this.value = value;
	}
	
	/**
	 * @return the encapsulated String, truncated to 30 characters
	 */
	@Override
	public String toString() {
		if (value.length() > 30)
			return "'" + value.substring(0, Math.min(value.length(), 25)) + "...'";
		else
			return value;
	}
	
	@Override
	public String getValue() {
		return value;
	}
	
	@Override
	public ArgumentType getType() {
		return ArgumentType.Text;
	}
	
	@Override
	public int hashCode() {
		return value.hashCode();
	}
	
	/**
	 * A TextAttribute is equal to another Object if that Object is a TextAttribute
	 * and their values are equal.
	 */
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(oth instanceof TextAttribute) ) return false;
		return value.equals(((TextAttribute) oth).getValue());  
	}
	
}
