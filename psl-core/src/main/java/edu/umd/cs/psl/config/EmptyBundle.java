package edu.umd.cs.psl.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public class EmptyBundle implements ConfigBundle {

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
	
}
