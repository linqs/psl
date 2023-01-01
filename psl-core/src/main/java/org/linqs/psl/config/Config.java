/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Reflection;
import org.linqs.psl.util.RuntimeStats;

import java.io.BufferedReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The storage for all process-wide configuration values used in PSL.
 * All access to the configuration is static.
 *
 * Users should heavily favor the org.linqs.psl.config.Options interface over this.
 * Options uses this as its backend.
 *
 * Configuration is set on a process-wide level.
 *
 * Internally, all config values is kept as objects and conversion attempts are made when requested.
 * Null values are considered empty or missing (don't store nulls).
 * To get raw objects use getProperty(), otherwise parsing will be done for the specific type.
 *
 * Configurations are loaded into stacked layers.
 * Callers may manipulate layers through pushLayer() and popLayer().
 * When a config value is requested, the key will be searched for in the layers (most recent first).
 * The first instance of the configuration will be returned.
 * Callers are responsible for ensuring that any layer they push is eventually popped (use try/finally).
 *
 * An attempt to load a few properties files
 * (CLASS_LIST_PROPS, GIT_PROPS, and PROJECT_PROPS)
 * will be made when this class initializes.
 */
public class Config {
    public static final String CLASS_LIST_PROPS = "classlist.properties";
    public static final String GIT_PROPS = "git.properties";
    public static final String PROJECT_PROPS = "project.properties";

    public static final String CLASS_LIST_KEY = "classlist.classes";

    private static final Logger log = Logger.getLogger(Config.class);

    private static Deque<Map<String, Object>> layers = null;

    static {
        init();
    }

    /**
     * (Re)create and populate the initial config.
     */
    public static void init() {
        layers = new LinkedList<Map<String, Object>>();
        pushLayer();

        // Load maven project properties.
        InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream(PROJECT_PROPS);
        if (stream != null) {
            loadProperties(stream, PROJECT_PROPS);
        }

        // Load git project properties.
        stream = ClassLoader.getSystemClassLoader().getResourceAsStream(GIT_PROPS);
        if (stream != null) {
            loadProperties(stream, GIT_PROPS);
        }

        // Load list of classes build at compile time.
        stream = ClassLoader.getSystemClassLoader().getResourceAsStream(CLASS_LIST_PROPS);
        if (stream != null) {
            loadClassList(stream, CLASS_LIST_PROPS);
        }
    }

    public static void pushLayer() {
        layers.push(new HashMap<String, Object>());
    }

    public static Map<String, Object> popLayer() {
        if (layers.size() == 1) {
            throw new IllegalStateException("Attempt to pop the only Config layer.");
        }

        return layers.pop();
    }

    public static void loadProperties(InputStream stream, String resourceName) {
        try (BufferedReader reader = FileUtils.getBufferedReader(stream)) {
            for (String line : FileUtils.lines(reader)) {
                line = line.trim();
                if (line.equals("") || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("=", 2);

                if (parts.length != 2) {
                    throw new IllegalArgumentException(String.format(
                            "Bad properties format for resource %s. Offending line: '%s'.",
                            resourceName, line));
                }

                setProperty(parts[0], parts[1], false);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load config resource: " + resourceName, ex);
        }

        log.debug("Configuration resource loaded: {}", resourceName);
        RuntimeStats.collect();
    }

    public static void loadClassList(InputStream stream, String resourceName) {
        List<String> classNames = new ArrayList<String>();

        try (BufferedReader reader = FileUtils.getBufferedReader(stream)) {
            for (String line : FileUtils.lines(reader)) {
                line = line.trim();
                if (line.equals("") || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("=", 2);

                if (parts.length != 2) {
                    throw new IllegalArgumentException(String.format(
                            "Bad properties format for resource %s. Offending line: '%s'.",
                            resourceName, line));
                }

                if (!parts[0].equals(CLASS_LIST_KEY)) {
                    throw new IllegalArgumentException(String.format(
                            "Unknown key (%s) found in classlist. Expecting only '%s'.",
                            parts[0], CLASS_LIST_KEY));
                }

                classNames.add(parts[1]);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load config resource: " + resourceName, ex);
        }

        setProperty(CLASS_LIST_KEY, classNames, false);

        log.debug("Configuration resource loaded: {}", resourceName);
        RuntimeStats.collect();
    }

    /**
     * Set a property in the most recent layer.
     */
    public static void setProperty(String key, Object value, boolean logAccess) {
        layers.peek().put(key, value);

        if (logAccess) {
            log.debug("Set option {} to {}.", key, value);
        }

        RuntimeStats.collect();
    }

    /**
     * Remove a property from the current layer of this configuration.
     */
    public static void clearProperty(String key, boolean logAccess) {
        layers.peek().remove(key);

        if (logAccess) {
            log.debug("Cleared option {}.", key);
        }
    }

    /**
     * Get a property from the configuration.
     * Typically, a more specific method should be used.
     */
    public static Object getProperty(String key, Object defaultValue, boolean logAccess) {
        Object value = null;
        for (Map<String, Object> layer : layers) {
            if (layer.containsKey(key)) {
                value = layer.get(key);
                break;
            }
        }

        if (logAccess) {
            if (value != null) {
                log.debug("Found value {} for option {}.", value, key);
            } else {
                log.debug("No value found for option {}. Returning default of {}.", key, defaultValue);
            }
        }

        return (value == null) ? defaultValue : value;
    }

    public static boolean hasProperty(String key) {
        return getProperty(key, null, false) != null;
    }

    public static Boolean getBoolean(String key, Boolean defaultValue) {
        Object value = getProperty(key, defaultValue, true);
        if (value instanceof Boolean) {
            return (Boolean)value;
        } else if (value instanceof String) {
            return Boolean.valueOf((String)value);
        }

        return Boolean.valueOf(value.toString());
    }

    public static Double getDouble(String key, Number defaultValue) {
        Object value = getProperty(key, defaultValue, true);
        if (value instanceof Double) {
            return (Double)value;
        } else if (value instanceof String) {
            return Double.valueOf((String)value);
        } else if (value instanceof Number) {
            return Double.valueOf(((Number)value).doubleValue());
        }

        return Double.valueOf(value.toString());
    }

    public static Float getFloat(String key, Number defaultValue) {
        Object value = getProperty(key, defaultValue, true);
        if (value instanceof Float) {
            return (Float)value;
        } else if (value instanceof String) {
            return Float.valueOf((String)value);
        } else if (value instanceof Number) {
            return Float.valueOf(((Number)value).floatValue());
        }

        return Float.valueOf(value.toString());
    }

    public static Integer getInteger(String key, Number defaultValue) {
        Object value = getProperty(key, defaultValue, true);
        if (value instanceof Integer) {
            return (Integer)value;
        } else if (value instanceof String) {
            return Integer.valueOf((String)value);
        } else if (value instanceof Number) {
            return Integer.valueOf(((Number)value).intValue());
        }

        return Integer.valueOf(value.toString());
    }

    public static Long getLong(String key, Number defaultValue) {
        Object value = getProperty(key, defaultValue, true);
        if (value instanceof Long) {
            return (Long)value;
        } else if (value instanceof String) {
            return Long.valueOf((String)value);
        } else if (value instanceof Number) {
            return Long.valueOf(((Number)value).longValue());
        }

        return Long.valueOf(value.toString());
    }

    public static String getString(String key, String defaultValue) {
        Object value = getProperty(key, defaultValue, true);
        if (value == null) {
            return null;
        } else if (!(value instanceof String)) {
            return value.toString();
        }

        return (String)value;
    }

    public static List getList(String key, List defaultValue, boolean logAccess) {
        return (List)getProperty(key, defaultValue, logAccess);
    }

    /**
     * A less flexible list fetch.
     */
    public static List<String> getStringList(String key) {
        List rawList = getList(key, null, false);
        if (rawList == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> rtn = (List<String>)rawList;

        return rtn;
    }

    /**
     * Returns a new instance of the class whose name associated with the given configuration key.
     * The default constructor will be used.
     */
    public static Object getNewObject(String key, String defaultValue) {
        String className = getString(key, defaultValue);
        if (className == null) {
            return null;
        }

        return Reflection.newObject(className);
    }
}
