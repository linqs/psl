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

import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;
import edu.umd.cs.psl.model.predicate.Predicate;

public enum DataFormat {

	IntegerID, String, LongString, Number;
	
	public static DataFormat[] getDefaultFormat(Predicate p) {
		return getDefaultFormat(p,false);
	}
	
	public static DataFormat[] getDefaultFormatforStringEntities(Predicate p) {
		return getDefaultFormat(p,false);
	}
	
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
				throw new IllegalArgumentException("Unknown argument type in position " + i +" of predicate: " + p);
			}
		}
		return format;
	}
	
}
