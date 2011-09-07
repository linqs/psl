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
package edu.umd.cs.psl.config;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DataConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SubsetConfiguration;
import org.apache.log4j.helpers.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {

	private static ConfigManager instance = null;
	
	private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
	
	private DataConfiguration masterConfig;
	
	private ConfigManager() throws ConfigurationException {
		masterConfig = new DataConfiguration(new BaseConfiguration());
		try {
			loadResource("psl.properties");
		}
		catch (FileNotFoundException e) {
			log.debug("PSL configuration file 'psl.properties' not found on classpath. " +
					"Only default values will be used unless additional properties are " +
					"specified.");
		}
	}
	
	public static ConfigManager getManager() throws ConfigurationException {
		if (instance == null) {
			instance = new ConfigManager();
		}
		return instance;
	}
	
	public void loadResource(String resource) throws FileNotFoundException, ConfigurationException {
		URL url;
		PropertiesConfiguration newConfig;
		
		try {
			url = new URL(resource);
		}
		catch (MalformedURLException ex) {
			url = Loader.getResource(resource);
		}
		if (url != null) {
			newConfig = new PropertiesConfiguration(url);
			masterConfig.append(newConfig);
		}
		else
			throw new FileNotFoundException();
	}
	
	public ConfigBundle getBundle(String id) {
		return new ManagedBundle((SubsetConfiguration) masterConfig.subset(id));
	}
	
	private class ManagedBundle implements ConfigBundle {
		private DataConfiguration config;
		private String prefix;
		
		private ManagedBundle(SubsetConfiguration bundleConfig) {
			prefix = bundleConfig.getPrefix();
			config = new DataConfiguration(bundleConfig);
		}
		
		private void logAccess(String key, Object defaultValue) {
			String scopedKey = prefix + "." + key;
			if (config.containsKey(key)) {
				Object value = config.getProperty(key);
				log.debug("Found value {} for option {}.", value, scopedKey);
			}
			else {
				log.debug("No value found for option {}. Returning default of {}.", scopedKey, defaultValue);
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
				toReturn.set(i, (String) configList.get(i));
			}
			return toReturn;
		}
		
		@Override
		public Factory getFactory(String key, Factory defaultValue)
				throws ClassNotFoundException, IllegalAccessException, InstantiationException {
			logAccess(key, defaultValue);
			Object value = config.getProperty(key);
			if (value == null)
				return defaultValue;
			if (value instanceof Factory)
				return (Factory) value;
			else if (value instanceof String)
				return (Factory) ClassLoader.getSystemClassLoader().loadClass((String) value).newInstance();
			else
				throw new IllegalArgumentException("Value " + value + " is not a Factory nor a String.");
		}

		@Override
		public Enum<?> getEnum(String key, Enum<?> defaultValue) {
			logAccess(key, defaultValue);
			return (Enum<?>) config.get(defaultValue.getDeclaringClass(), key, defaultValue);
		}
	}
}
