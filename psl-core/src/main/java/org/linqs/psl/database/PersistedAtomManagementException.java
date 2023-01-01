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
package org.linqs.psl.database;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;

import java.util.Collection;

/**
 * A common type of exception that indicates a target (RandomVariableAtom) was requested
 * (typically during grounding) without being specifed (typically in a data file).
 * This class is the exception itself as well as controls on when to throw the exception.
 */
public class PersistedAtomManagementException extends RuntimeException {
    private static final Logger log = Logger.getLogger(PersistedAtomManagementException.class);

    private static boolean initialized = false;
    private static boolean throwOnIllegalAccess = false;
    private static boolean warnOnIllegalAccess = false;

    private Collection<GroundAtom> atoms;
    private Rule rule;

    private PersistedAtomManagementException(Collection<GroundAtom> atoms, Rule rule) {
        super(String.format(
                "Found one or more RandomVariableAtoms (target ground atom)" +
                " that were not explicitly specified in the targets." +
                " Offending atom(s): %s." +
                " This typically means that your specified target set is insufficient." +
                " This was encountered during the grounding of the rule: [%s].",
                StringUtils.join(", ", atoms), rule));

        init();

        this.atoms = atoms;
        this.rule = rule;
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        throwOnIllegalAccess = Options.PAM_THROW_ACCESS_EXCEPTION.getBoolean();
        warnOnIllegalAccess = !throwOnIllegalAccess;
    }

    public static void report(Collection<GroundAtom> atoms, Rule rule) {
        RuntimeException exception = new PersistedAtomManagementException(atoms, rule);

        if (throwOnIllegalAccess) {
            throw exception;
        }

        if (warnOnIllegalAccess) {
            warnOnIllegalAccess = false;
            log.warn(String.format("Found non-persisted RVAs (%s)." +
                    " If you do not understand the implications of this warning," +
                    " check your configuration and set '%s' to true." +
                    " This warning will only be logged once.",
                    StringUtils.join(", ", atoms), Options.PAM_THROW_ACCESS_EXCEPTION.name()));
        }
    }
}
