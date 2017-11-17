/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model.weight;

public abstract class Weight {
	private static final double EPSILON = 1e-100;

	private double weight;

	public Weight() {
		weight = Double.NaN;
	}

	public Weight(double w) {
		checkWeight(w);
		weight = w;
	}

	private void checkWeight(double w) {
      if (!isValidWeight(w)) {
         throw new IllegalArgumentException("Illegal weight: " + w);
      }
	}

	public double getWeight() {
		return weight;
	}

	@Override
	public String toString() {
		return "W=" + weight;
	}

	@Override
	public boolean equals(Object oth) {
		if (oth == this) {
         return true;
      }

		if (oth == null || !(getClass().isInstance(oth))) {
         return false;
      }

		Weight w = (Weight)oth;
		return Math.abs(weight - w.weight) < EPSILON;
	}

	protected abstract boolean isValidWeight(double w);

	public abstract Weight duplicate();
}
