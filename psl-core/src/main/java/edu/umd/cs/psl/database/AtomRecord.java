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

/**
 * A {@link DataStore} record of an {@link Atom}.
 */
public class AtomRecord {

	public enum Status { FACT, CERTAINTY, RV }
		
	private final double[] values;
	
	private final double[] confidences;
	
	private Status status;
	
	public AtomRecord(double[] values, double[] confidences) {
		this.values=values;
		this.confidences=confidences;
		this.status = Status.RV;
	}
	
	public AtomRecord(double[] values, double[] confidences, Status status) {
		this.values=values;
		this.confidences=confidences;
		setStatus(status);
	}
	
	public AtomRecord() {
		this(null,null);
	}
	
	public AtomRecord(Status status) {
		this();
		setStatus(status);
	}
	
	public Status getStatus() {
		return status;
	}
	
	public void setStatus(Status status) {
		this.status=status;		
	}

	public double[] getValues() {
		return values;
	}

	public double[] getConfidences() {
		return confidences;
	}
	
	
	
}
