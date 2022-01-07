/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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

/**
 * A utility to help build hash codes for objects.
 * This class borrows the methodology from org.apache.commons.lang3.builder.HashCodeBuilder.
 * We do not just directly use that class and instead choose a static class to reduce
 * allocations in high-traffic code like atoms or ground rules.
 *
 * When adding to a hash code, the previous value can be supplied as the |start| parameter.
 * Therefore, calls can be nested:
 * int myHashCode = HashCode.build(HashCode.build(a), b);
 */
public class HashCode {
    public static final int DEFAULT_INITIAL_NUMBER = 17;
    public static final int DEFAULT_MULTIPLIER = 37;

    // Static only.
    private HashCode() {}

    public static int build(Object obj) {
        return build(DEFAULT_INITIAL_NUMBER, DEFAULT_MULTIPLIER, obj);
    }

    public static int build(int start, Object obj) {
        return build(start, DEFAULT_MULTIPLIER, obj);
    }

    public static int build(int start, int multiplier, Object obj) {
        return start * multiplier + obj.hashCode();
    }

    public static int build(Object[] objs) {
        return build(DEFAULT_INITIAL_NUMBER, DEFAULT_MULTIPLIER, objs);
    }

    public static int build(int start, Object[] objs) {
        return build(start, DEFAULT_MULTIPLIER, objs);
    }

    public static int build(int start, int multiplier, Object[] objs) {
        for (int i = 0; i < objs.length; i++) {
            start = start * multiplier + objs[i].hashCode();
        }
        return start;
    }
}
