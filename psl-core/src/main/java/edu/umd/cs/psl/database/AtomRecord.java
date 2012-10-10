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

import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.TruthValues;
import edu.umd.cs.psl.model.atom.Atom;

/**
 * A {@link DataStore} record of an {@link Atom}.
 */
public class AtomRecord {

	public enum Status {
		
		ACTIVE_OBSERVED {

			@Override
			public int getIntValue() {
				return 0;
			}
			
		},
		
		CONSIDERED_FIXED {

			@Override
			public int getIntValue() {
				return 30;
			}
			
		},
		
		ACTIVE_FIXED {

			@Override
			public int getIntValue() {
				return 10;
			}
		
		},
		
		CONSIDERED_RV {

			@Override
			public int getIntValue() {
				return 40;
			}
			
		},
		
		ACTIVE_RV {

			@Override
			public int getIntValue() {
				return 20;
			}
			
		};
	
		/**
		 * @return a storage-friendly code for the Status
		 */
		public abstract int getIntValue();
	}
		
	private final double value;
	
	private final double confidence;
	
	private Status status;
	
	/**
	 * Constructs an AtomRecord with default truth and confidence values.
	 * 
	 * @param status  the status in the AtomRecord
	 * @see TruthValues#getDefault()
	 * @see ConfidenceValues#getDefault()
	 */
	public AtomRecord(Status status) {
		this(TruthValues.getDefault(), ConfidenceValues.getDefault(), status);
	}
	
	/**
	 * Constructs an AtomRecord.
	 * 
	 * @param value  the truth value in the AtomRecord
	 * @param confidence  the confidence value in the AtomRecord
	 * @param status  the status in the AtomRecord
	 */
	public AtomRecord(double value, double confidence, Status status) {
		this.value = value;
		this.confidence = confidence;
		this.status = status;
	}
	
	public Status getStatus() {
		return status;
	}

	public double getValue() {
		return value;
	}

	public double getConfidence() {
		return confidence;
	}
	
	public static int getObservedUpperBound() {
		return 5;
	}
	
	public static int getActiveUpperBound() {
		return 25;
	}
	
	public static Status parseIntValue(int value) {
		switch(value) {
			case 0: return Status.ACTIVE_OBSERVED;
			case 30: return Status.CONSIDERED_FIXED;
			case 10: return Status.ACTIVE_FIXED;
			case 40: return Status.CONSIDERED_RV;
			case 20: return Status.ACTIVE_RV;
			default: throw new IllegalArgumentException("Unrecognized value.");
		}
	}
	
}
