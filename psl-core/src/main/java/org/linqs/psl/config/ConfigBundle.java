/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
 * Copyright 2001-2008 The Apache Software Foundation
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
 *
 * The University of Maryland modified the file
 * org/apache/commons/configuration/Configuration.java, from the
 * commons-configuration distribution, version 1.6, to create this file.
 */
package org.linqs.psl.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.apache.commons.configuration.ConversionException;

/**
 * Encapsulates a set of configuration properties organized by keys.
 */
public interface ConfigBundle {
	/**
	 * Return a ConfigBundle containing every key from the current
	 * ConfigBundle that starts with the specified prefix. The prefix is
	 * removed from the keys in the subset. For example, if the configuration
	 * contains the following properties:
	 *
	 * <pre>
	 *    prefix.number = 1
	 *    prefix.string = Apache
	 *    prefixed.foo = bar
	 *    prefix = Jakarta
	 * </pre>
	 *
	 * the ConfigBundle returned by <code>subset("prefix")</code> will contain
	 * the properties:
	 *
	 * <pre>
	 *    number = 1
	 *    string = Apache
	 *    = Jakarta
	 * </pre>
	 *
	 * (The key for the value "Jakarta" is an empty string)
	 *
	 * @param prefix The prefix used to select the properties.
	 * @return a subset configuration bundle
	 */
	ConfigBundle subset(String prefix);

	/**
	 * Add a property to the configuration. If it already exists then the value
	 * stated here will be added to the configuration entry. For example, if the
	 * property:
	 *
	 * <pre>
	 * resource.loader = file
	 * </pre>
	 *
	 * is already present in the configuration and you call
	 *
	 * <pre>
	 * addProperty(&quot;resource.loader&quot;, &quot;classpath&quot;)
	 * </pre>
	 *
	 * Then you will end up with a List like the following:
	 *
	 * <pre>
	 * ["file", "classpath"]
	 * </pre>
	 *
	 * @param key The key to add the property to.
	 * @param value The value to add.
	 */
	void addProperty(String key, Object value);

	/**
	 * Set a property, this will replace any previously set values. Set values
	 * is implicitly a call to clearProperty(key), addProperty(key, value).
	 *
	 * @param key The key of the property to change
	 * @param value The new value
	 */
	void setProperty(String key, Object value);

	/**
	 * Remove a property from the configuration.
	 *
	 * @param key the key to remove along with corresponding value.
	 */
	void clearProperty(String key);

	/**
	 * Remove all properties from the configuration.
	 */
	void clear();

	/**
	 * Get a property from the configuration.
	 *
	 * @param key The configuration key
	 *
	 * @return The associated Object (or null if undefined)
	 */
	Object getProperty(String key);

	/**
	 * Get a boolean associated with the given configuration key. If the key
	 * doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated boolean.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Boolean.
	 */
	boolean getBoolean(String key, boolean defaultValue);

	/**
	 * Get a {@link Boolean} associated with the given configuration key.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated boolean if key is found and has valid format, default value otherwise.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Boolean.
	 */
	Boolean getBoolean(String key, Boolean defaultValue);

	/**
	 * Get a byte associated with the given configuration key. If the key
	 * doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated byte.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Byte.
	 */
	byte getByte(String key, byte defaultValue);

	/**
	 * Get a {@link Byte} associated with the given configuration key.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated byte if key is found and has valid format, default value otherwise.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Byte.
	 */
	Byte getByte(String key, Byte defaultValue);

	/**
	 * Get a double associated with the given configuration key. If the key
	 * doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated double.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Double.
	 */
	double getDouble(String key, double defaultValue);

	/**
	 * Get a {@link Double} associated with the given configuration key.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated double if key is found and has valid format, default value otherwise.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Double.
	 */
	Double getDouble(String key, Double defaultValue);

	/**
	 * Get a float associated with the given configuration key. If the key
	 * doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated float.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Float.
	 */
	float getFloat(String key, float defaultValue);

	/**
	 * Get a {@link Float} associated with the given configuration key. If the
	 * key doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated float if key is found and has valid format, default value otherwise.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Float.
	 */
	Float getFloat(String key, Float defaultValue);

	/**
	 * Get a int associated with the given configuration key. If the key doesn't
	 * map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated int.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Integer.
	 */
	int getInt(String key, int defaultValue);

	/**
	 * Get an {@link Integer} associated with the given configuration key. If
	 * the key doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated int if key is found and has valid format, default value otherwise.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Integer.
	 */
	Integer getInteger(String key, Integer defaultValue);

	/**
	 * Get a long associated with the given configuration key. If the key
	 * doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated long.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Long.
	 */
	long getLong(String key, long defaultValue);

	/**
	 * Get a {@link Long} associated with the given configuration key. If the
	 * key doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated long if key is found and has valid format, default value otherwise.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Long.
	 */
	Long getLong(String key, Long defaultValue);

	/**
	 * Get a short associated with the given configuration key.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated short.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Short.
	 */
	short getShort(String key, short defaultValue);

	/**
	 * Get a {@link Short} associated with the given configuration key. If the
	 * key doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated short if key is found and has valid format, default value otherwise.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a Short.
	 */
	Short getShort(String key, Short defaultValue);

	/**
	 * Get a {@link BigDecimal} associated with the given configuration key. If
	 * the key doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 *
	 * @return The associated BigDecimal if key is found and has valid format, default value otherwise.
	 */
	BigDecimal getBigDecimal(String key, BigDecimal defaultValue);

	/**
	 * Get a {@link BigInteger} associated with the given configuration key. If
	 * the key doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 *
	 * @return The associated BigInteger if key is found and has valid format, default value otherwise.
	 */
	BigInteger getBigInteger(String key, BigInteger defaultValue);

	/**
	 * Get a string associated with the given configuration key. If the key
	 * doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated string if key is found and has valid format, default value otherwise.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a String.
	 */
	String getString(String key, String defaultValue);

	/**
	 * Get a List of strings associated with the given configuration key. If the
	 * key doesn't map to an existing object, the default value is returned.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated List of strings.
	 *
	 * @throws ConversionException is thrown if the key maps to an object that is not a List.
	 */
	List<String> getList(String key, List<String> defaultValue);

	/**
	 * Gets a {@link Factory} associated with the given configuration key.
	 *
	 * If the value found is a String, then this method returns an instance of the
	 * class with that <a href="http://download.oracle.com/javase/6/docs/api/java/lang/ClassLoader.html#name">
	 * binary name</a>.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The requested {@link Factory} if found, otherwise the default value.
	 * @throws ClassNotFoundException The value found indicated a class that could not be found.
	 * @throws IllegalAccessException The value found indicated a class that is not accessible.
	 * @throws InstantiationException Instantiating the indicated class failed.
	 */
	Factory getFactory(String key, Factory defaultValue)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException;

	/**
	 * Returns an enum associated with the given configuration key.
	 *
	 * If the value found is a String, then it will be cast to an enum value of
	 * the same class as the default value.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @return The associated enum if the key is found, default value otherwise.
	 */
	Enum<?> getEnum(String key, Enum<?> defaultValue);

	/**
	 * Returns a new instance of the class whose name associated with the given configuration key.
	 *
	 * If the class has a constructor that takes only a ConfigBundle, that constructor will be called.
	 * Otherwise, the default constructor will be called.
	 *
	 * @param key The configuration key.
	 * @param defaultValue The default value.
	 * @throws NoSuchMethodException If no sutible constructor could be found.
	 * @return A new instance of the specified class.
	 */
	Object getNewObject(String key, String defaultValue);
}
