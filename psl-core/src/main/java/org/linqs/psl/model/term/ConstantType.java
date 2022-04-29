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
package org.linqs.psl.model.term;

/**
 * A type of {@link Constant}.
 * <p>
 * The enumerated types of ConstantType are used to identify different subtypes
 * of Constant.
 */
public enum ConstantType {

    /**
     * A {@link StringAttribute} argument.
     */
    String {
        @Override
        public String getName() {
            return "String";
        }

        @Override
        public boolean isInstance(Constant term) {
            return (term instanceof StringAttribute);
        }
    },

    /**
     * An {@link IntegerAttribute} argument.
     */
    Integer {
        @Override
        public String getName() {
            return "Integer";
        }

        @Override
        public boolean isInstance(Constant term) {
            return (term instanceof IntegerAttribute);
        }
    },

    /**
     * A {@link DoubleAttribute} argument.
     */
    Double {
        @Override
        public String getName() {
            return "Double";
        }

        @Override
        public boolean isInstance(Constant term) {
            return (term instanceof DoubleAttribute);
        }
    },

    /**
     * A {@link Long} argument.
     */
    Long {
        @Override
        public String getName() {
            return "Long";
        }

        @Override
        public boolean isInstance(Constant term) {
            return term instanceof LongAttribute;
        }
    },

    /**
     * A {@link UniqueIntID} argument.
     * A unique identifier that is explicitly an int.
     * Will generally perform faster than a UniqueStringID.
     */
    UniqueIntID {
        @Override
        public String getName() {
            return "UniqueIntID";
        }

        @Override
        public boolean isInstance(Constant term) {
            return (term instanceof UniqueIntID);
        }
    },

    /**
     * A {@link UniqueStringID} argument.
     * A unique identifier that is explicitly a String.
     * Will generally perform slower than a UniqueIntID.
     */
    UniqueStringID {
        @Override
        public String getName() {
            return "UniqueStringID";
        }

        @Override
        public boolean isInstance(Constant term) {
            return (term instanceof UniqueStringID);
        }
    },

    /**
     * A special type of unique identifier to only be used by functional predicates in special situations.
     * This is to be used only when the exact type of unique id must be deferred until actual computation time.
     * STRONGLY prefer UniqueIntID or UniqueStringID over this.
     */
    DeferredFunctionalUniqueID {
        @Override
        public String getName() {
            return "DeferredFunctionalUniqueID";
        }

        @Override
        public boolean isInstance(Constant term) {
            // Any real unique identifier can match.
            return (term instanceof UniqueIntID) || (term instanceof UniqueStringID);
        }
    };

    /**
     * @return a human-friendly String identifier for this ArgumentType
     */
    public abstract String getName();

    /**
     * Returns whether a GroundTerm is of the type identified by this ArgumentType
     *
     * @param term  the term to check
     * @return TRUE if term is an instance of the corresponding type
     */
    public abstract boolean isInstance(Constant term);

    public static ConstantType getType(Constant term) {
        for (ConstantType type : ConstantType.values()) {
            if (type.isInstance(term)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Term is of unknown type : " + term);
    }

    /**
     * Convert a general string into the appropriate Constant.
     */
    public static Constant getConstant(String value, ConstantType type) {
        switch (type) {
            case Double:
                return new DoubleAttribute(java.lang.Double.parseDouble(value));
            case Integer:
                return new IntegerAttribute(java.lang.Integer.parseInt(value));
            case String:
                return new StringAttribute(value);
            case Long:
                return new LongAttribute(java.lang.Long.parseLong(value));
            case UniqueIntID:
                return new UniqueIntID(java.lang.Integer.parseInt(value));
            case UniqueStringID:
                return new UniqueStringID(value);
            default:
                throw new IllegalArgumentException("Unknown argument type: " + type);
        }
    }
}
