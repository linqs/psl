/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.util.dynamicclass;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class DynamicClassLoader {
	
	public static final char argumentDelimiter = '#';

	public static<V> V loadClassArbitraryArgs(String definition, Class<V> expected) throws Throwable {
		return loadClassArbitraryArgs(definition,null,expected);
	}
	
	public static<V> V loadClassArbitraryArgs(String definition, Map<String,Class<? extends V>> classKeys, Class<V> expected) throws Throwable {
		definition = definition.trim();
		int argPosition = definition.indexOf(argumentDelimiter);
		String fullClassName, args;
		if (argPosition<0) {
			fullClassName = definition;
			args = "";
		} else {
			fullClassName = definition.substring(0,argPosition);
			args = definition.substring(argPosition+1);
		}
		fullClassName=fullClassName.trim();
		if (classKeys!=null && classKeys.containsKey(fullClassName.toLowerCase())) {
			Class<? extends V> clazz = classKeys.get(fullClassName.toLowerCase());
			return loadClassWithArgs(clazz,parseArguments(args),expected);
		} else {
			return loadClassWithArgs(fullClassName,parseArguments(args),expected);
		}
	}
	
	public static String[] parseArguments(String arg) {
		arg = arg.trim();
		if (arg.length()<1) return new String[]{};
		else {
			String args[] = arg.split(",");
			for (int i=0;i<args.length;i++) 
				args[i] = args[i].trim();
			return args;
		}
	}
	
	
	public static<V> V loadClassWithArgs(String fullClassName, String[] args, Class<V> expected) throws Throwable {
		try {
			Class<?> c = Class.forName(fullClassName);
			return loadClassWithArgs(c.asSubclass(expected),args,expected);
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Specified attribute predicate type is unkown: " + fullClassName);
		} catch (ClassCastException e) {
			throw new AssertionError("Specified class is not a subclass of the expected one: " + fullClassName);
		}
	}
	
	public static<V> V loadClassWithArgs(Class<? extends V> clazz, String[] args, Class<V> expected) throws Throwable {
		try {
			Constructor<? extends V> co;
			V obj;
			if (args.length>0)  {
				co = clazz.getConstructor(new Class[]{String[].class});
				obj =  co.newInstance(new Object[]{args});
			} else {
				co = clazz.getConstructor();
				obj =  co.newInstance();
			}
			return obj;
		} catch (NoSuchMethodException e) {
			throw new AssertionError("Specified class does not have expected constructor: " + clazz);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
		} catch (Exception e) {
			e.printStackTrace();
			throw new AssertionError("Error when trying to create new object.\n");
		}
	}

	
}
