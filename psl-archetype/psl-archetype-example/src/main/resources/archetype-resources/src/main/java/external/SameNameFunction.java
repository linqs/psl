#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
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
package ${package}.external;

import ${groupId}.psl.model.argument.Attribute;
import ${groupId}.psl.model.argument.GroundTerm;
import ${groupId}.psl.model.argument.type.ArgumentType;
import ${groupId}.psl.model.argument.type.ArgumentTypes;
import ${groupId}.psl.model.function.ExternalFunction;
import ${groupId}.psl.model.predicate.type.PredicateType;
import ${groupId}.psl.model.predicate.type.PredicateTypes;

/**
 * This is an example external function.
 */
class SameNameFunction implements ExternalFunction {
	
	/**
	 * Returns the value for the given arguments in the GroundTerm array. In this
	 * case the arguments are:
     * GroundTerm[0] = Name 1
     * GroundTerm[1] = Name 2
	 */
	@Override
	public double[] getValue(GroundTerm... args) {
		if (args[0] instanceof Attribute && args[1] instanceof Attribute) {
			return ((String) ((Attribute) args[0]).getAttribute())
				.equalsIgnoreCase((String) ((Attribute) args[1]).getAttribute())
				? new double[] {1.0} : new double[] {0.0};
		}
		else throw new IllegalArgumentException("Strings only.");
	}
	
	/**
	 * Returns the number of arguments to the function.
	 */
	@Override
	public int getArity() {
		return 2;
	}
	
	/**
	 * Returns the type(s) of the argument(s) to the function. There should be as
	 * many types returned as the arity of the function. See {@link ArgumentTypes}
	 * for default types.
	 */
	@Override
	public ArgumentType[] getArgumentTypes() {
		return new ArgumentType[] {ArgumentTypes.Text, ArgumentTypes.Text};
	}
	
	/**
	 * Returns the predicate type associated with this function. See
	 * {@link PredicateTypes} for default types.
	 */
	@Override
	public PredicateType getPredicateType() {
		return PredicateTypes.SoftTruth;
	}
	
}
