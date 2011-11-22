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
package edu.umd.cs.psl.database;

import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * A general data type to be used for storing the {@link Term Terms}
 * of {@link Atom Atoms} for a particular argument to a particular
 * {@link Predicate}.
 * 
 * Predicates take Terms as their arguments. A DataFormat is
 * used to specify how Terms used for a particular argument for a
 * particular Predicate should be represented by a {@link DataStore}.
 * It is the responsibility of an implementation of DataStore to map these
 * general data types to the specific representations it uses for storage. 
 */
public enum DataFormat {

	/** Integer as unique ID */
	IntegerID,
	
	/** Text of short to moderate length */
	String,
	
	/** Text of long length */
	LongString,
	
	/** Floating point number (possibly double precision) */
	Number;
	
	/**
	 * Returns default DataFormats for the arguments of a Predicate,
	 * assuming that integers are used to represent {@link Entity Entities}.
	 * 
	 * @param p  the Predicate for which default DataFormats will be returned
	 * @return array of DataFormats in the order of the Predicate's arguments
	 */
	public static DataFormat[] getDefaultFormat(Predicate p) {
		return getDefaultFormat(p, false);
	}
	
	/**
	 * Returns default DataFormats for the arguments of a Predicate,
	 * assuming that strings are used to represent {@link Entity Entities}.
	 * 
	 * @param p  the Predicate for which default DataFormats will be returned
	 * @return array of DataFormats in the order of the Predicate's arguments
	 */
	public static DataFormat[] getDefaultFormatforStringEntities(Predicate p) {
		return getDefaultFormat(p, true);
	}
	
	/**
	 * Returns default DataFormats for the arguments of a Predicate.
	 * 
	 * @param p  the Predicate for which default DataFormats will be returned
	 * @param hasStringEntities
	 *            if true, strings will be used instead of integers to represent Entities
	 * @return array of DataFormats in the order of the Predicate's arguments
	 */
	public static DataFormat[] getDefaultFormat(Predicate p, boolean hasStringEntities) {
		DataFormat[] format = new DataFormat[p.getArity()];
		for (int i=0;i<p.getArity();i++) {
			ArgumentType arg = p.getArgumentType(i);
			if (arg==ArgumentTypes.Text) {
				format[i]=String;
			} else if (arg==ArgumentTypes.Number) {
				format[i]=Number;
			} else if (arg.isEntity()) {
				if (hasStringEntities)
					format[i]=String;
				else
					format[i]=IntegerID;
			} else {
				throw new IllegalArgumentException("Unknown argument type in position "
						+ i +" of predicate " + p);
			}
		}
		return format;
	}
	
}
