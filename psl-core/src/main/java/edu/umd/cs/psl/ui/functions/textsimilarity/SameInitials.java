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
package edu.umd.cs.psl.ui.functions.textsimilarity;

import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.StringAttribute;
import edu.umd.cs.psl.model.function.ExternalFunction;

/**
 * Returns 1 if the input names have the same initials
 * (ignoring case and order), and 0 otherwise.
 */
class SameInitials implements ExternalFunction
{
	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ArgumentType[] getArgumentTypes() {
		return new ArgumentType[] { ArgumentType.String, ArgumentType.String };
	}
	
	@Override
	public double getValue(ReadOnlyDatabase db, GroundTerm... args) {
		String a = ((StringAttribute) args[0]).getValue();
		String b = ((StringAttribute) args[1]).getValue();
		String[] tokens0 = a.split("\\s+");
		String[] tokens1 = b.split("\\s+");
		if (tokens0.length != tokens1.length)
			return 0.0;
		
		int[] initialsHistogram0 = new int[27];
		int[] initialsHistogram1 = new int[27];
		
		for (int i = 0; i < tokens0.length; i++) {
			updateHistogram(tokens0[i].toLowerCase().charAt(0), initialsHistogram0);
			updateHistogram(tokens1[i].toLowerCase().charAt(0), initialsHistogram1);
		}
		
		for (int i = 0; i < initialsHistogram0.length; i++)
			if (initialsHistogram0[i] != initialsHistogram1[i])
				return 0.0;
		
		return 1.0;
    }
    
    static void updateHistogram(char initial, int[] histogram) {
    	int code = (int) initial - 97;
    	if (code < 0 || code > 25)
    		code = 26;
    	
    	histogram[code]++;
    }
}
