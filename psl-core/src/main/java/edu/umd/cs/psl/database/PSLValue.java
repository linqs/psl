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

public enum PSLValue {

	Fact {

		@Override
		public int getIntValue() {
			return 0;
		}
		
	},
	
	DefaultFact {
		
		@Override
		public int getIntValue() {
			return 60;
		}
		
	},
	
	ActiveRV {
		@Override
		public int getIntValue() {
			return 20;
		}
		
	},
	
	DefaultRV {
		@Override
		public int getIntValue() {
			return 80;
		}
		
	};
	
	public abstract int getIntValue();
	
	public static int getNonDefaultFactsUpperBound() {
		return 10;
	}
	
	public static int getNonDefaultUpperBound() {
		return 50;
	}
	
	public static PSLValue parse(int value) {
		switch(value) {
		case 0: return Fact;
		case 60: return DefaultFact;
		case 20: return ActiveRV;
		case 80: return DefaultRV;
		default: throw new AssertionError("Invalid PSL value: " + value);
		}
	}
	
}
