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
package org.linqs.psl.model.term;

public class UniqueStringID implements Constant {
	private final String id;

	public UniqueStringID(String id) {
		this.id = id;
	}

	public String getID() {
		return id;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}

		if (other == null || !(getClass().isInstance(other))) {
			return false;
		}

		return id.equals(((UniqueStringID)other).id);
	}

	@Override
	public int compareTo(Constant other) {
		if (other instanceof UniqueStringID) {
			return id.compareTo(((UniqueStringID)other).id);
		}

		return this.getClass().getSimpleName().compareTo(other.getClass().getSimpleName());
	}

	@Override
	public String toString() {
		return "'" + id + "'";
	}
}
