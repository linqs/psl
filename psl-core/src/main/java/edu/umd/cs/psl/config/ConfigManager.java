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

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.helpers.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {

	private static final ConfigManager instance = new ConfigManager();
	
	private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
	
	private Map<String, Object> settings;
	
	private ConfigManager() {
		settings = new HashMap<String, Object>();
		loadResource("psl.properties");
	}
	
	public static ConfigManager getManager() {
		return instance;
	}
	
	public void loadResource(String resource) {
		URL url = Loader.getResource(resource);
	}
	
	public ConfigBundle getBundle(String id) {
		return new ManagedBundle(id);
	}
	
	private class ManagedBundle implements ConfigBundle {
		private String id;
		
		private static final String INVALID_TYPE = "The requested value could not "
			+ "be cast as the requested type.";

		private ManagedBundle(String bundleId) {
			id = bundleId;
		}
		
		private Object getObject(String key, Object defaultValue) {
			String scopedKey = id + "." + key;
			if (settings.containsKey(scopedKey)) {
				Object value = settings.get(key);
				log.debug("Found value {} for option {}.", value, scopedKey);
				return value;
			}
			else {
				log.debug("No value found for option {}. Returning default of {}.", scopedKey, defaultValue);
				return defaultValue;
			}
		}

		@Override
		public Boolean getBoolean(String key, Boolean defaultValue) {
			Object value = getObject(key, defaultValue);
			if (value instanceof Boolean) {
				return (Boolean) value;
			}
			else
				throw new IllegalArgumentException(INVALID_TYPE);
		}

		@Override
		public Double getDouble(String key, Double defaultValue) {
			Object value = getObject(key, defaultValue);
			if (value instanceof Double) {
				return (Double) value;
			}
			else
				throw new IllegalArgumentException(INVALID_TYPE);
		}

		@Override
		public Enum<?> getEnum(String key, Enum<?> defaultValue) {
			Object value = getObject(key, defaultValue);
			if (value instanceof Enum<?> && ((Enum<?>) value).getDeclaringClass().equals(defaultValue.getDeclaringClass())) {
				return (Enum<?>) value;
			}
			else
				throw new IllegalArgumentException(INVALID_TYPE);
		}

		@Override
		public String getString(String key, Double defaultValue) {
			Object value = getObject(key, defaultValue);
			if (value instanceof String) {
				return (String) value;
			}
			else
				throw new IllegalArgumentException(INVALID_TYPE);
		}
	}
}
