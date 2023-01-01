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

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * A container for configuration options.
 * This is the preferred way to interface with configuration options compiled into the code.
 * Calls to the get methods will in-turn call into the Config option with the correct name and default value.
 * If flags are set (such as FLAG_POSITIVE), they will be checked when a value is retrieved (via a get method).
 */
public class Option implements Comparable<Option> {
    public static final int FLAG_NON_NEGATIVE = (1 << 0);  // >= 0
    public static final int FLAG_POSITIVE = (1 << 1);  // > 0
    public static final int FLAG_LT_ONE = (1 << 2);  // < 1
    public static final int FLAG_LTE_ONE = (1 << 3);  // <= 1

    private static Set<String> seenNames = new HashSet<String>();

    private String name;
    private Object defaultValue;
    private String description;
    private Class<?> type;
    private int flags;

    public Option(String name, boolean defaultValue, String description) {
        this(name, Boolean.valueOf(defaultValue), boolean.class, description, 0);
    }

    public Option(String name, String defaultValue, String description) {
        this(name, defaultValue, String.class, description, 0);
    }

    public Option(String name, int defaultValue, String description) {
        this(name, Integer.valueOf(defaultValue), int.class, description, 0);
    }

    public Option(String name, int defaultValue, String description, int flags) {
        this(name, Integer.valueOf(defaultValue), int.class, description, flags);
    }

    public Option(String name, long defaultValue, String description) {
        this(name, Long.valueOf(defaultValue), long.class, description, 0);
    }

    public Option(String name, long defaultValue, String description, int flags) {
        this(name, Long.valueOf(defaultValue), long.class, description, flags);
    }

    public Option(String name, float defaultValue, String description) {
        this(name, Float.valueOf(defaultValue), float.class, description, 0);
    }

    public Option(String name, float defaultValue, String description, int flags) {
        this(name, Float.valueOf(defaultValue), float.class, description, flags);
    }

    public Option(String name, double defaultValue, String description) {
        this(name, Double.valueOf(defaultValue), double.class, description, 0);
    }

    public Option(String name, double defaultValue, String description, int flags) {
        this(name, Double.valueOf(defaultValue), double.class, description, flags);
    }

    public Option(String name, Object defaultValue, Class<?> type, String description, int flags) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.description = description;
        this.type = type;
        this.flags = flags;

        if (seenNames.contains(name)) {
            throw new RuntimeException("Option with name '" + name + "' already seen.");
        }
        seenNames.add(name);
    }

    public String name() {
        return name;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public String description() {
        return description;
    }

    public int flags() {
        return flags;
    }

    /**
     * Set the value (in the Config) for this Option.
     * This will overwrite any previously set value.
     * No type checking is done on the passed-in value.
     */
    public void set(Object value) {
        Config.setProperty(name, value, true);
    }

    /**
     * Clear the value (in the Config) for this Option.
     * This will remove any previously set value,
     * but not remove any defaults.
     */
    public void clear() {
        Config.clearProperty(name, true);
    }

    /**
     * Fetch the Option's configuration value from the Config class.
     * This is the most general get method, one with a specific type should generally be used.
     * This is the only provided get method that does not use a default.
     */
    public Object get() {
        return Config.getProperty(name, null, true);
    }

    public boolean isSet() {
        return Config.hasProperty(name);
    }

    public Object getUnlogged() {
        return Config.getProperty(name, null, false);
    }

    public boolean getBoolean() {
        return Config.getBoolean(name, (Boolean)defaultValue).booleanValue();
    }

    public String getString() {
        if (defaultValue == null) {
            return Config.getString(name, null);
        } else {
            return Config.getString(name, defaultValue.toString());
        }
    }

    public int getInt() {
        int value = Config.getInteger(name, (Number)defaultValue).intValue();
        checkNumericFlags(value, "" + value);
        return value;
    }

    public long getLong() {
        long value = Config.getLong(name, (Number)defaultValue).longValue();
        checkNumericFlags(value, "" + value);
        return value;
    }

    public float getFloat() {
        float value = Config.getFloat(name, (Number)defaultValue).floatValue();
        checkNumericFlags(value, "" + value);
        return value;
    }

    public double getDouble() {
        double value = Config.getDouble(name, (Number)defaultValue).doubleValue();
        checkNumericFlags(value, "" + value);
        return value;
    }

    public Object getNewObject() {
        return Config.getNewObject(name, ((String)defaultValue));
    }

    @Override
    public int compareTo(Option other) {
        if (other == null) {
            return -1;
        }

        return this.name().compareTo(other.name());
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || other.getClass() != this.getClass()) {
            return false;
        }

        return this.name.equals(((Option)other).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append(name);

        boolean hasType = (type != null);
        boolean hasDefault = (defaultValue != null);

        if (hasType || hasDefault) {
            builder.append("(");

            if (hasType) {
                builder.append("Type: " + type.getName().toString());
            }

            if (hasType && hasDefault) {
                builder.append(", ");
            }

            if (hasDefault) {
                builder.append("Default Value: " + defaultValue.toString());
            }

            builder.append(")");
        }

        if (description != null) {
            builder.append(" - " + description);
        }

        return builder.toString();
    }

    private void checkNumericFlags(double value, String displayValue) {
        if ((flags & FLAG_NON_NEGATIVE) != 0 && value < 0) {
            throw new IllegalArgumentException("Property " + name + " must be non-negative, found value: " + displayValue);
        }

        if ((flags & FLAG_POSITIVE) != 0 && value <= 0) {
            throw new IllegalArgumentException("Property " + name + " must be positive, found value: " + displayValue);
        }

        if ((flags & FLAG_LT_ONE) != 0 && value >= 1) {
            throw new IllegalArgumentException("Property " + name + " must be < 1, found value: " + displayValue);
        }

        if ((flags & FLAG_LTE_ONE) != 0 && value > 1) {
            throw new IllegalArgumentException("Property " + name + " must be <= 1, found value: " + displayValue);
        }
    }

    @SuppressWarnings("unchecked")
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("flags", flags);

        if (type != null) {
            json.put("type", type.getName());
        }

        if (defaultValue != null) {
            if (type == float.class && ((Float)defaultValue).floatValue() == Float.POSITIVE_INFINITY) {
                json.put("default", "+Infinity");
            } else if (type == float.class && ((Float)defaultValue).floatValue() == Float.NEGATIVE_INFINITY) {
                json.put("default", "-Infinity");
            } else if (type == double.class && ((Double)defaultValue).doubleValue() == Float.POSITIVE_INFINITY) {
                json.put("default", "+Infinity");
            } else if (type == double.class && ((Double)defaultValue).doubleValue() == Float.NEGATIVE_INFINITY) {
                json.put("default", "-Infinity");
            } else {
                json.put("default", defaultValue);
            }
        }

        if (description != null) {
            json.put("description", description);
        }

        return json;
    }
}
