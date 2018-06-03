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
package org.linqs.psl.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Various utilties around objects and reflection.
 */
public final class Reflection {
	// Static only.
	private Reflection() {}

	public static Object newObject(String className) {
		Class<?> classObject = null;
		try {
			classObject = Class.forName(className);
		} catch (ClassNotFoundException ex) {
			throw new IllegalArgumentException("Could not find class: " + className, ex);
		}

		return newObject(classObject);
	}

	public static Object newObject(Class<?> classObject) {
		Constructor constructor = null;
		try {
			constructor = classObject.getConstructor();
		} catch (NoSuchMethodException ex2) {
			throw new IllegalArgumentException(
					"Could not find a default constructor for " + classObject.getName());
		}

		Object rtn = null;
		try {
			rtn = constructor.newInstance();
		} catch (InstantiationException ex) {
			throw new RuntimeException("Unable to instantiate object (" + classObject.getName() + ")", ex);
		} catch (IllegalAccessException ex) {
			throw new RuntimeException("Insufficient access to constructor for " + classObject.getName(), ex);
		} catch (InvocationTargetException ex) {
			throw new RuntimeException("Error thrown while constructing " + classObject.getName(), ex);
		}

		return rtn;
	}
}
