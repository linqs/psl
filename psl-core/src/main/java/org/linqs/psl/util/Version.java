/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.config.Config;

/**
 * Get versioning information about the project.
 */
public class Version {
    // Properties set by maven.
    public static final String VERSION_KEY = "project.version";
    public static final String VERSION_DEFAULT = "UNKNOWN";

    public static final String GIT_COMMIT_SHORT_KEY = "git.commit.id.abbrev";
    public static final String GIT_COMMIT_SHORT_DEFAULT = "xxxxxxx";

    public static final String GIT_DIRTY_KEY = "git.dirty";

    // Static only.
    private Version() {}

    public static String get() {
        return Config.getString(VERSION_KEY, VERSION_DEFAULT);
    }

    public static String getFull() {
        String version = Config.getString(VERSION_KEY, VERSION_DEFAULT);
        version += "-" + Config.getString(GIT_COMMIT_SHORT_KEY, GIT_COMMIT_SHORT_DEFAULT);

        if (Config.getBoolean(GIT_DIRTY_KEY, false)) {
            version += "-dirty";
        }

        return version;
    }
}
