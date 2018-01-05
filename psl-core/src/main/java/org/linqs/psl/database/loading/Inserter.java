/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.database.loading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Inserter {
	/**
	 * Insert a single object using the default truth value.
	 */
	public void insert(Object... data) {
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("Attempted to insert empty data.");
		}

		List<List<Object>> newData = new ArrayList<List<Object>>(1);
		newData.add(Arrays.asList(data));
		insertAll(newData);
	}

	/**
	 * Insert a single object using the specified truth value.
	 */
	public void insertValue(double value, Object... data) {
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("Attempted to insert empty data.");
		}

		if (value < 0 || value > 1) {
			throw new IllegalArgumentException("Invalid truth value: " + value + ". Must be between 0 and 1 inclusive.");
		}

		List<List<Object>> newData = new ArrayList<List<Object>>(1);
		newData.add(Arrays.asList(data));

		List<Double> newValue = new ArrayList<Double>(1);
		newValue.add(value);

		insertAllValues(newValue, newData);
	}

	/**
	 * Insert several objects using the default truth value.
	 */
	public abstract void insertAll(List<List<Object>> data);

	/**
	 * Insert several objects using the specified truth values.
	 */
	public abstract void insertAllValues(List<Double> values, List<List<Object>> data);
}
