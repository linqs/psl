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

import org.linqs.psl.config.ConfigBundle;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Various utilties around objects and reflection.
 */
public final class Objects {
	// Static only.
	private Objects() {}

	/**
	 * Try to construct a new object by first looking for the PSL-style constructor with a ConfigBundle,
	 * and then a default constructor.
	 */
	public static Object newObject(String className) {
		return newObject(className, null);
	}

	public static Object newObject(String className, ConfigBundle config) {
		Class classObject = null;
		try {
			classObject = Class.forName(className);
		} catch (ClassNotFoundException ex) {
			throw new IllegalArgumentException("Could not find class: " + className, ex);
		}

		return newObject(classObject, config);
	}

	public static Object newObject(Class classObject) {
		return newObject(classObject, null);
	}

	public static Object newObject(Class classObject, ConfigBundle config) {
		Constructor constructor = null;
		boolean useConfig = true;

		try {
			// First, try to get a constructor with only a ConfigBundle.
			constructor = classObject.getConstructor(ConfigBundle.class);
		} catch (NoSuchMethodException ex) {
			useConfig = false;
		}

		// If we couldn't find a constructor with a config, or we don't have a config,
		// then try to find a default one.
		if (!useConfig || config == null) {
			try {
				constructor = classObject.getConstructor();
				useConfig = false;
			} catch (NoSuchMethodException ex2) {
				throw new IllegalArgumentException(
						"Could not find a suitable constructor (only ConfigBundle or no parameters) for " +
						classObject.getName());
			}
		}

		Object rtn = null;
		try {
			if (useConfig) {
				rtn = constructor.newInstance(config);
			} else {
				rtn = constructor.newInstance();
			}
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
