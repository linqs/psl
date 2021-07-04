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
package org.linqs.psl.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utilities for interfacing with a filesystem.
 */
public class SystemUtils {
    private static final Logger log = LoggerFactory.getLogger(SystemUtils.class);

    // Static only.
    private SystemUtils() {}

    /**
     * Get a temp dir (path) that is unique to this user/host/prefix.
     * {systemTempDir}/{prefix}_{username}@{host}
     */
    public static String getTempDir(String prefix) {
        return Paths.get(String.format(
                "%s/%s_%s@%s", getSystemTempDir(), prefix, getUsername(), getHostname())).toString();
    }

    public static String getSystemTempDir() {
        return Paths.get(System.getProperty("java.io.tmpdir")).toString();
    }

    public static String getUsername() {
        return System.getProperty("user.name");
    }

    public static String getHostname() {
        String hostname = "unknown";

        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            // log.warn("Hostname can not be resolved, using '" + hostname + "'.");
        }

        return hostname;
    }

    /**
     * Recursively make directories (mkdir -p).
     */
    public static void mkdir(String path) {
        mkdir(new File(path));
    }

    public static void mkdir(File file) {
        if (file.mkdirs()) {
            return;
        }

        // A false return from mkdirs() can mean a directory already existed.
        if (file.exists() && file.isDirectory()) {
            throw new RuntimeException("Failed to mkdirs(\"" + file.getPath() + "\").");
        }
    }

    public static void delete(String path) {
        delete(new File(path));
    }

    /**
     * Delete a file or empty directory.
     */
    public static void delete(File file) {
        if (file.delete()) {
            return;
        }

        // A false return from delete() can mean the dirent never existed.
        if (file.exists()) {
            throw new RuntimeException("Failed to delete(\"" + file.getPath() + "\").");
        }
    }

    public static void recursiveDelete(String path) {
        recursiveDelete(new File(path));
    }

    public static void recursiveDelete(File target) {
        if (!target.exists()) {
            return;
        }

        if (!target.isDirectory()) {
            delete(target);
            return;
        }

        File[] dirents = target.listFiles();
        if (dirents != null) {
            for (File dirent : dirents) {
                recursiveDelete(dirent);
            }
        }

        delete(target);
    }

}
