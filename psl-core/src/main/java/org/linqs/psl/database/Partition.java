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
package org.linqs.psl.database;

public class Partition {
    /**
     * When grounding with lazy atoms, we will initially set their partition
     * value to this so that we can tell them apart.
     * Afterwards, they will be reset to the correct value (the write partition
     * of the database).
     * Note that no valid partition is actually allowed to have negative values.
     */
    public static final int SPECIAL_WRITE_ID = -1;
    public static final int SPECIAL_READ_ID = -2;

    private final int id;
    private final String name;

    /**
     * Sole constructor.
     *
     * @param id non-negative identifier
     */
    public Partition(int id, String name) {
        assert(id >= 0);

        this.id = id;
        this.name = name;
    }

    public int getID() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return "Partition[" + name + "]";
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (other == null || !(other instanceof Partition)) {
            return false;
        }

        return id == ((Partition)other).id;
    }
}
