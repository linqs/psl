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

import org.joda.time.DateTime;

/**
 * An {@link Attribute} that encapsulates a Date.
 *
 * @author John Sullivan
 */
public class DateAttribute implements Attribute{
	 private final DateTime value;

	 /**
	  * Constructs a Date attribute from a Date
	  *
	  * @param value  Date to encapsulate
	  */
	 public DateAttribute(DateTime value) {
		  this.value = value;
	 }

	 /**
	  * @return the encapsulated Date as a String in single quotes
	  */
	 @Override
	 public String toString() {
		  return "'" + value.toString() + "'";
	 }

	 @Override
	 public DateTime getValue() {
		  return value;
	 }

	 @Override
	 public int hashCode() {
		  return value.hashCode();
	 }

	 /**
	  * A DateAttribute is equal to another Object if that Object is a DateAttribute
	  * and their values are equal.
	  */
	 @Override
	 public boolean equals(Object oth) {
		  if (oth==this) return true;
		  if (oth==null || !(oth instanceof DateAttribute)) return false;
		  return value == ((DateAttribute) oth).value;
	 }

	 @Override
	 public int compareTo(Constant o) {
		  if (o instanceof DateAttribute)
				return value.compareTo(((DateAttribute) o).value);
		  else
				return this.getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
	 }
}
