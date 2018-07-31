/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.util;

import java.util.List;

/**
 * Various static String utilities.
 */
public final class StringUtils {
	public static final char DEFAULT_DELIM = ',';

	// Static only.
	private StringUtils() {}

	public static int[] splitInt(String text) {
		return splitInt(text, DEFAULT_DELIM);
	}

	public static int[] splitInt(String text, char delim) {
		return splitInt(text, "" + delim);
	}

	public static int[] splitInt(String text, String delim) {
		String[] parts = text.split(delim);

		int[] ints = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			ints[i] = Integer.parseInt(parts[i]);
		}

		return ints;
	}

	public static double[] splitDouble(String text) {
		return splitDouble(text, DEFAULT_DELIM);
	}

	public static double[] splitDouble(String text, char delim) {
		return splitDouble(text, "" + delim);
	}

	public static double[] splitDouble(String text, String delim) {
		String[] parts = text.split(delim);

		double[] doubles = new double[parts.length];
		for (int i = 0; i < parts.length; i++) {
			doubles[i] = Double.parseDouble(parts[i]);
		}

		return doubles;
	}

	public static String join(List<?> parts) {
		return join(parts, DEFAULT_DELIM);
	}

	public static String join(List<?> parts, char delim) {
		return join(parts, "" + delim);
	}

	public static String join(List<?> parts, String delim) {
		StringBuilder builder = new StringBuilder(parts.size() * 2 - 1);

		for (int i = 0; i < parts.size(); i++) {
			builder.append(parts.get(i));

			if (i != parts.size() - 1) {
				builder.append(delim);
			}
		}

		return builder.toString();
	}

	public static String join(Object[] parts) {
		return join(parts, DEFAULT_DELIM);
	}

	public static String join(Object[] parts, char delim) {
		return join(parts, "" + delim);
	}

	public static String join(Object[] parts, String delim) {
		StringBuilder builder = new StringBuilder(parts.length * 2 - 1);

		for (int i = 0; i < parts.length; i++) {
			builder.append(parts[i]);

			if (i != parts.length - 1) {
				builder.append(delim);
			}
		}

		return builder.toString();
	}

	public static String join(int[] parts) {
		return join(parts, DEFAULT_DELIM);
	}

	public static String join(int[] parts, char delim) {
		return join(parts, "" + delim);
	}

	public static String join(int[] parts, String delim) {
		StringBuilder builder = new StringBuilder(parts.length * 2 - 1);

		for (int i = 0; i < parts.length; i++) {
			builder.append(parts[i]);

			if (i != parts.length - 1) {
				builder.append(delim);
			}
		}

		return builder.toString();
	}

	public static String join(double[] parts) {
		return join(parts, DEFAULT_DELIM);
	}

	public static String join(double[] parts, char delim) {
		return join(parts, "" + delim);
	}

	public static String join(double[] parts, String delim) {
		StringBuilder builder = new StringBuilder(parts.length * 2 - 1);

		for (int i = 0; i < parts.length; i++) {
			builder.append(parts[i]);

			if (i != parts.length - 1) {
				builder.append(delim);
			}
		}

		return builder.toString();
	}

	public static String repeat(String text, int times) {
		return repeat(text, "", times);
	}

	public static String repeat(String text, String delim, int times) {
		if (times < 0) {
			throw new IllegalArgumentException("Cannot repeat a string negative times.");
		} else if (times == 0) {
			return "";
		}

		StringBuilder builder = new StringBuilder(times * 2);
		for (int i = 0; i < times; i++) {
			builder.append(text);

			if (i != (times - 1)) {
				builder.append(delim);
			}
		}

		return builder.toString();
	}
}
