/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.ui.functions.textsimilarity;

import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.function.ExternalFunction;

public class SubStringSimilarity implements ExternalFunction {

	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ArgumentType[] getArgumentTypes() {
		return new ArgumentType[] {ArgumentType.String, ArgumentType.String};
	}
	
	@Override
	public double getValue(ReadOnlyDatabase db, GroundTerm... args) {
		String a = args[0].toString();
		String b = args[1].toString();
		String s1,s2;
		if (a.length()<b.length()) {
			s1 = a; s2 = b;
		} else {
			s1 = b; s2 = a;
		}
		s1 = s1.toLowerCase(); s2 = s2.toLowerCase();
		int index = s2.indexOf(s1, 0);
		if (index<0) return 0.0;
		else {
			return s1.length()*1.0/s2.length();
		}
	}

	
}
