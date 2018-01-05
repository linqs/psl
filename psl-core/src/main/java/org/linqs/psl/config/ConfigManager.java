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
package org.linqs.psl.config;

import org.linqs.psl.util.Objects;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DataConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SubsetConfiguration;
import org.apache.log4j.helpers.Loader;
import org.apache.log4j.helpers.OptionConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ConfigManager {
	private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

	private static ConfigManager instance = null;

	private DataConfiguration masterConfig;

	private ConfigManager() throws ConfigurationException {
		masterConfig = new DataConfiguration(new BaseConfiguration());
		String pslConfigFile = OptionConverter.getSystemProperty("psl.configuration", "psl.properties");

		// Load the configuration file directly if it exists.
		if ((new File(pslConfigFile)).isFile()) {
			loadResource(pslConfigFile);
		} else {
			// Try to get a resource URL from the system (if we have a property key instead of a path).
			URL resourceURL = Loader.getResource(pslConfigFile);
			if (resourceURL != null) {
				loadResource(resourceURL);
			} else {
				log.debug("PSL configuration {} file not found. " +
						"Only default values will be used unless additional properties are " +
						"specified.", pslConfigFile);
			}
		}
	}

	public static ConfigManager getManager() throws ConfigurationException {
		if (instance == null) {
			instance = new ConfigManager();
		}

		return instance;
	}

	public void loadResource(String path) throws ConfigurationException {
		masterConfig.append(new PropertiesConfiguration(path));
		log.debug("Configuration file loaded: {}", path);
	}

	public void loadResource(URL url) throws ConfigurationException {
		masterConfig.append(new PropertiesConfiguration(url));
		log.debug("Configuration URL loaded: {}", url);
	}

	public ConfigBundle getBundle(String id) {
		return new ManagedBundle((SubsetConfiguration) masterConfig.subset(id));
	}

	private class ManagedBundle implements ConfigBundle {
		private DataConfiguration config;
		private String prefix;

		private ManagedBundle(SubsetConfiguration bundleConfig) {
			prefix = bundleConfig.getPrefix();
			config = new DataConfiguration(new BaseConfiguration());
			config.copy(bundleConfig);
		}

		private void logAccess(String key, Object defaultValue) {
			String scopedKey = prefix + "." + key;
			if (config.containsKey(key)) {
				Object value = config.getProperty(key);
				log.debug("Found value {} for option {}.", value, scopedKey);
			} else {
				log.debug("No value found for option {}. Returning default of {}.", scopedKey, defaultValue);
			}
		}

		@Override
		public void addProperty(String key, Object value) {
			config.addProperty(key, value);
			log.debug("Added {} to option {}.", value, prefix + "." + key);
		}

		@Override
		public void setProperty(String key, Object value) {
			config.setProperty(key, value);
			log.debug("Set option {} to {}.", prefix + "." + key, value);
		}

		@Override
		public void clearProperty(String key) {
			config.clearProperty(key);
			log.debug("Cleared option {}.", prefix + "." + key);
		}

		@Override
		public void clear() {
			config.clear();
			log.debug("Cleared all options in {} bundle.", prefix);
		}

		@Override
		public Object getProperty(String key) {
			logAccess(key, "");
			if (config.containsKey(key)) {
				 return config.getProperty(key);
			} else {
				 return null;
			}
		}

		@Override
		public Boolean getBoolean(String key, Boolean defaultValue) {
			logAccess(key, defaultValue);
			return config.getBoolean(key, defaultValue);
		}

		@Override
		public Double getDouble(String key, Double defaultValue) {
			logAccess(key, defaultValue);
			return config.getDouble(key, defaultValue);
		}

		@Override
		public String getString(String key, String defaultValue) {
			logAccess(key, defaultValue);
			return config.getString(key, defaultValue);
		}

		@Override
		public ConfigBundle subset(String prefix) {
			return new ManagedBundle((SubsetConfiguration) config.subset(prefix));
		}

		@Override
		public boolean getBoolean(String key, boolean defaultValue) {
			logAccess(key, defaultValue);
			return config.getBoolean(key, defaultValue);
		}

		@Override
		public byte getByte(String key, byte defaultValue) {
			logAccess(key, defaultValue);
			return config.getByte(key, defaultValue);
		}

		@Override
		public Byte getByte(String key, Byte defaultValue) {
			logAccess(key, defaultValue);
			return config.getByte(key, defaultValue);
		}

		@Override
		public double getDouble(String key, double defaultValue) {
			logAccess(key, defaultValue);
			return config.getDouble(key, defaultValue);
		}

		@Override
		public float getFloat(String key, float defaultValue) {
			logAccess(key, defaultValue);
			return config.getFloat(key, defaultValue);
		}

		@Override
		public Float getFloat(String key, Float defaultValue) {
			logAccess(key, defaultValue);
			return config.getFloat(key, defaultValue);
		}

		@Override
		public int getInt(String key, int defaultValue) {
			logAccess(key, defaultValue);
			return config.getInt(key, defaultValue);
		}

		@Override
		public Integer getInteger(String key, Integer defaultValue) {
			logAccess(key, defaultValue);
			return config.getInteger(key, defaultValue);
		}

		@Override
		public long getLong(String key, long defaultValue) {
			logAccess(key, defaultValue);
			return config.getLong(key, defaultValue);
		}

		@Override
		public Long getLong(String key, Long defaultValue) {
			logAccess(key, defaultValue);
			return config.getLong(key, defaultValue);
		}

		@Override
		public short getShort(String key, short defaultValue) {
			logAccess(key, defaultValue);
			return config.getShort(key, defaultValue);
		}

		@Override
		public Short getShort(String key, Short defaultValue) {
			logAccess(key, defaultValue);
			return config.getShort(key, defaultValue);
		}

		@Override
		public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
			logAccess(key, defaultValue);
			return config.getBigDecimal(key, defaultValue);
		}

		@Override
		public BigInteger getBigInteger(String key, BigInteger defaultValue) {
			logAccess(key, defaultValue);
			return config.getBigInteger(key, defaultValue);
		}

		@Override
		public List<String> getList(String key, List<String> defaultValue) {
			logAccess(key, defaultValue);
			List<?> configList = config.getList(key, defaultValue);
			List<String> toReturn = new ArrayList<String>(configList.size());
			for (int i = 0; i < configList.size(); i++) {
				toReturn.add( (String) configList.get(i));
			}
			return toReturn;
		}

		@Override
		public Factory getFactory(String key, Factory defaultValue)
				throws ClassNotFoundException, IllegalAccessException, InstantiationException {
			logAccess(key, defaultValue);

			Object value = config.getProperty(key);
			if (value == null) {
				return defaultValue;
			}

			if (value instanceof Factory) {
				return (Factory) value;
			} else if (value instanceof String) {
				return (Factory) ClassLoader.getSystemClassLoader().loadClass((String) value).newInstance();
			} else {
				throw new IllegalArgumentException("Value " + value + " is not a Factory nor a String.");
			}
		}

		@Override
		public Enum<?> getEnum(String key, Enum<?> defaultValue) {
			logAccess(key, defaultValue);
			return (Enum<?>) config.get(defaultValue.getDeclaringClass(), key, defaultValue);
		}

		@Override
		public Object getNewObject(String key, String defaultValue) {
			String className = config.getString(key, defaultValue);

			// It is not unusual for someone to want no object if the key does not exist.
			if (className == null) {
				return null;
			}

			return Objects.newObject(className, this);
		}

		@Override
		public String toString() {
			StringBuilder string = new StringBuilder();
			for (@SuppressWarnings("unchecked")
			Iterator<String> itr = (Iterator<String>) config.getKeys(); itr.hasNext();) {
				String key = itr.next();
				string.append(prefix + "." + key + ": " + config.getProperty(key) + "\n");
			}
			return string.toString();
		}
	}
}
