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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public class EmptyBundle implements ConfigBundle {

	@Override
	public void addProperty(String key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setProperty(String key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clearProperty(String key) { }

	@Override
	public void clear() { }

	 	@Override
	public Object getProperty(String key){
		throw new UnsupportedOperationException();
	}

	@Override
	public Boolean getBoolean(String key, Boolean defaultValue) {
		return defaultValue;
	}

	@Override
	public Double getDouble(String key, Double defaultValue) {
		return defaultValue;
	}

	@Override
	public String getString(String key, String defaultValue) {
		return defaultValue;
	}

	@Override
	public ConfigBundle subset(String prefix) {
		return new EmptyBundle();
	}

	@Override
	public boolean getBoolean(String key, boolean defaultValue) {
		return defaultValue;
	}

	@Override
	public byte getByte(String key, byte defaultValue) {
		return defaultValue;
	}

	@Override
	public Byte getByte(String key, Byte defaultValue) {
		return defaultValue;
	}

	@Override
	public double getDouble(String key, double defaultValue) {
		return defaultValue;
	}

	@Override
	public float getFloat(String key, float defaultValue) {
		return defaultValue;
	}

	@Override
	public Float getFloat(String key, Float defaultValue) {
		return defaultValue;
	}

	@Override
	public int getInt(String key, int defaultValue) {
		return defaultValue;
	}

	@Override
	public Integer getInteger(String key, Integer defaultValue) {
		return defaultValue;
	}

	@Override
	public long getLong(String key, long defaultValue) {
		return defaultValue;
	}

	@Override
	public Long getLong(String key, Long defaultValue) {
		return defaultValue;
	}

	@Override
	public short getShort(String key, short defaultValue) {
		return defaultValue;
	}

	@Override
	public Short getShort(String key, Short defaultValue) {
		return defaultValue;
	}

	@Override
	public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
		return defaultValue;
	}

	@Override
	public BigInteger getBigInteger(String key, BigInteger defaultValue) {
		return defaultValue;
	}

	@Override
	public List<String> getList(String key, List<String> defaultValue) {
		return defaultValue;
	}

	@Override
	public Factory getFactory(String key, Factory defaultValue) {
		return defaultValue;
	}

	@Override
	public Enum<?> getEnum(String key, Enum<?> defaultValue) {
		return defaultValue;
	}

	@Override
	public Object getNewObject(String key, String defaultValue) {
		String className = getString(key, defaultValue);

		// It is not unusual for someone to want no object if the key does not exist.
		if (className == null) {
			return null;
		}

		return Objects.newObject(className, this);
	}
}
