/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
import org.linqs.psl.util.Reflection;
import org.linqs.psl.util.RuntimeStats;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.DataConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.helpers.Loader;
import org.apache.log4j.helpers.OptionConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The storage for all configuration values used in PSL.
 * No prefixes are used (unlike previous config infrastructure).
 * All access to configuration is static.
 *
 * Properties can be managed through the following methods:
 * loadResource(), addProperty(), setProperty(), clearProperty(), clear().
 *
 * PSL will statically try to load a properties file pointed to by the "psl.configuration"
 * system property ("psl.properties") by default.
 *
 * When a property is put, RuntimeStats will get called to try collecting stats.
 */
public class Config {
    public static final String CLASS_LIST_PROPS = "classlist.properties";
    public static final String GIT_PROPS = "git.properties";
    public static final String PROJECT_PROPS = "project.properties";

    public static final String PSL_CONFIG = "psl.configuration";
    public static final String PSL_CONFIG_DEFAULT = "psl.properties";

    public static final String CLASS_LIST_KEY = "classlist.classes";

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private static DataConfiguration config = null;

    static {
        init();
    }

    /**
     * (Re)create and populate the initial config.
     */
    public static void init() {
        config = new DataConfiguration(new BaseConfiguration());

        // Load maven project properties.
        InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream(PROJECT_PROPS);
        if (stream != null) {
            loadResource(stream, PROJECT_PROPS);
        }

        // Load git project properties.
        stream = ClassLoader.getSystemClassLoader().getResourceAsStream(GIT_PROPS);
        if (stream != null) {
            loadResource(stream, GIT_PROPS);
        }

        // Load list of classes build at compile time.
        stream = ClassLoader.getSystemClassLoader().getResourceAsStream(CLASS_LIST_PROPS);
        if (stream != null) {
            loadResource(stream, CLASS_LIST_PROPS);
        }

        // Load the configuration file directly if the path exists.
        String path = OptionConverter.getSystemProperty(PSL_CONFIG, PSL_CONFIG_DEFAULT);
        if (FileUtils.isFile(path)) {
            loadResource(path);
            return;
        }

        // Try to get a resource URL from the system (if we have a property key instead of a path).
        stream = ClassLoader.getSystemClassLoader().getResourceAsStream(path);
        if (stream != null) {
            loadResource(stream, PSL_CONFIG);
            return;
        }

        log.debug(
                "PSL configuration {} file not found." +
                " Only default values will be used unless additional properties are specified.",
                path);
    }

    public static void loadResource(InputStream stream, String resourceName) {
        try {
            PropertiesConfiguration props = new PropertiesConfiguration();
            props.read(FileUtils.getInputStreamReader(stream));
            config.append(props);
        } catch (IOException | ConfigurationException ex) {
            throw new RuntimeException("Failed to load config resource: " + resourceName, ex);
        }

        log.debug("Configuration stream loaded: {}", resourceName);
        RuntimeStats.collect();
    }

    public static void loadResource(String path) {
        try {
            PropertiesConfiguration props = new PropertiesConfiguration();
            props.read(FileUtils.getInputStreamReader(path));
            config.append(props);
        } catch (IOException | ConfigurationException ex) {
            throw new RuntimeException("Failed to load config resource: " + path, ex);
        }

        log.debug("Configuration file loaded: {}", path);
        RuntimeStats.collect();
    }

    /**
     * Add a property to the configuration.
     * If it already exists then the value stated here will be added to the configuration entry.
     * For example, if the property:
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
    public static void addProperty(String key, Object value) {
        config.addProperty(key, value);
        log.debug("Added {} to option {}.", value, key);
        RuntimeStats.collect();
    }

    /**
     * Set a property, this will replace any previously set values.
     * Set values is implicitly a call to clearProperty(key), addProperty(key, value).
     *
     * @param key the key to remove along with corresponding value.
     */
    public static void setProperty(String key, Object value) {
        config.setProperty(key, value);
        log.debug("Set option {} to {}.", key, value);
        RuntimeStats.collect();
    }

    /**
     * Remove a property from the configuration.
     *
     * @param key the key to remove along with corresponding value.
     */
    public static void clearProperty(String key) {
        config.clearProperty(key);
        log.debug("Cleared option {}.", key);
    }

    /**
     * Remove all properties from the configuration.
     */
    public static void clear() {
        config.clear();
        log.debug("Cleared all options in the configuration.");
    }

    /**
     * Get a property from the configuration.
     * Typically, a more specific method should be used.
     *
     * @param key The configuration key
     *
     * @return The associated Object (or null if undefined)
     */
    public static Object getProperty(String key) {
        logAccess(key, "");
        if (config.containsKey(key)) {
            return config.getProperty(key);
        }

        return null;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        logAccess(key, defaultValue);
        return config.getBoolean(key, defaultValue);
    }

    public static Boolean getBoolean(String key, Boolean defaultValue) {
        logAccess(key, defaultValue);
        return config.getBoolean(key, defaultValue);
    }

    public static Double getDouble(String key, Double defaultValue) {
        logAccess(key, defaultValue);
        return config.getDouble(key, defaultValue);
    }

    public static String getString(String key, String defaultValue) {
        logAccess(key, defaultValue);
        return config.getString(key, defaultValue);
    }

    public static byte getByte(String key, byte defaultValue) {
        logAccess(key, defaultValue);
        return config.getByte(key, defaultValue);
    }

    public static Byte getByte(String key, Byte defaultValue) {
        logAccess(key, defaultValue);
        return config.getByte(key, defaultValue);
    }

    public static double getDouble(String key, double defaultValue) {
        logAccess(key, defaultValue);
        return config.getDouble(key, defaultValue);
    }

    public static float getFloat(String key, float defaultValue) {
        logAccess(key, defaultValue);
        return config.getFloat(key, defaultValue);
    }

    public static Float getFloat(String key, Float defaultValue) {
        logAccess(key, defaultValue);
        return config.getFloat(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        logAccess(key, defaultValue);
        return config.getInt(key, defaultValue);
    }

    public static Integer getInteger(String key, Integer defaultValue) {
        logAccess(key, defaultValue);
        return config.getInteger(key, defaultValue);
    }

    public static long getLong(String key, long defaultValue) {
        logAccess(key, defaultValue);
        return config.getLong(key, defaultValue);
    }

    public static Long getLong(String key, Long defaultValue) {
        logAccess(key, defaultValue);
        return config.getLong(key, defaultValue);
    }

    public static short getShort(String key, short defaultValue) {
        logAccess(key, defaultValue);
        return config.getShort(key, defaultValue);
    }

    public static Short getShort(String key, Short defaultValue) {
        logAccess(key, defaultValue);
        return config.getShort(key, defaultValue);
    }

    public static BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
        logAccess(key, defaultValue);
        return config.getBigDecimal(key, defaultValue);
    }

    public static BigInteger getBigInteger(String key, BigInteger defaultValue) {
        logAccess(key, defaultValue);
        return config.getBigInteger(key, defaultValue);
    }

     /**
      * Because list options can be quite large, we allow them to be suppressed on request.
      */
    public static List<String> getList(String key, List<String> defaultValue, boolean suppressLogging) {
        if (!suppressLogging) {
            logAccess(key, defaultValue);
        }

        List<?> configList = config.getList(key, defaultValue);

        List<String> toReturn = new ArrayList<String>(configList.size());
        for (Object item : configList) {
            toReturn.add((String)item);
        }

        return toReturn;
    }

    public static List<String> getList(String key, List<String> defaultValue) {
        return getList(key, defaultValue, false);
    }

    public static List<String> getList(String key, boolean suppressLogging) {
        return getList(key, new ArrayList<String>(0), suppressLogging);
    }

    public static List<String> getList(String key) {
        return getList(key, new ArrayList<String>(0));
    }

    /**
     * Get a property, but don't log the access.
     * This should only be used in rare cases.
     */
    public static Object getUnloggedProperty(String key) {
        if (config.containsKey(key)) {
            return config.getProperty(key);
        }

        return null;
    }

    /**
     * Returns a new instance of the class whose name associated with the given configuration key.
     * The default constructor will be used.
     */
    public static Object getNewObject(String key, String defaultValue) {
        logAccess(key, defaultValue);

        String className = config.getString(key, defaultValue);

        // It is not unusual for someone to want no object if the key does not exist.
        if (className == null) {
            return null;
        }

        return Reflection.newObject(className);
    }

    public static String asString() {
        StringBuilder string = new StringBuilder();

        @SuppressWarnings("unchecked")
        Iterator<String> keys = config.getKeys();
        while (keys.hasNext()) {
            String key = keys.next();
            string.append(key + ": " + config.getProperty(key) + System.lineSeparator());
        }

        return string.toString();
    }

    private static void logAccess(String key, Object defaultValue) {
        if (config.containsKey(key)) {
            log.debug("Found value {} for option {}.", config.getProperty(key), key);
        } else {
            log.debug("No value found for option {}. Returning default of {}.", key, defaultValue);
        }
    }
}
