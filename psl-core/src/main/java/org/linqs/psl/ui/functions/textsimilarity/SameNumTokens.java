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
package org.linqs.psl.ui.functions.textsimilarity;

import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.StringAttribute;

/**
 * Returns 1 if the
 * input strings contain the same number of tokens,
 * and 0 otherwise.
 */
class SameNumTokens implements ExternalFunction {
	
	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ConstantType[] getArgumentTypes() {
		return new ConstantType[] { ConstantType.String, ConstantType.String };
	}
	
	@Override
	public double getValue(ReadOnlyDatabase db, Constant... args) {
		String a = ((StringAttribute) args[0]).getValue();
		String b = ((StringAttribute) args[1]).getValue();
		String[] tokens0 = a.split("\\s+");
		String[] tokens1 = b.split("\\s+");
		if (tokens0.length != tokens1.length)
			return 0.0;
		return 1.0;
    }
}
