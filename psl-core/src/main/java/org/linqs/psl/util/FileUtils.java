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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * Utilities for interfacing with files and directories.
 */
public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    // Static only.
    private FileUtils() {}

    public static BufferedReader getBufferedReader(String path) {
        return getBufferedReader(new File(path));
    }

    public static BufferedReader getBufferedReader(File file) {
        return new BufferedReader(getInputStreamReader(file));
    }

    public static BufferedReader getBufferedReader(InputStream stream) {
        return new BufferedReader(getInputStreamReader(stream));
    }

    public static BufferedWriter getBufferedWriter(String path) {
        return getBufferedWriter(new File(path));
    }

    public static BufferedWriter getBufferedWriter(File file) {
        try {
            return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static InputStreamReader getInputStreamReader(String path) {
        return getInputStreamReader(new File(path));
    }

    public static InputStreamReader getInputStreamReader(File file) {
        try {
            return getInputStreamReader(new FileInputStream(file));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static InputStreamReader getInputStreamReader(InputStream stream) {
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    /**
     * Check if a dirent exists.
     */
    public static boolean exists(String path) {
        return ((new File(path)).exists());
    }

    /**
     * Check if a dirent exists and is a file.
     */
    public static boolean isFile(String path) {
        return ((new File(path)).isFile());
    }

    /**
     * Check if a dirent exists and is a directory.
     */
    public static boolean isDir(String path) {
        return ((new File(path)).isDirectory());
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
        if (!file.exists() || !file.isDirectory()) {
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

    /**
     * Construct a path to the given file relative to the data file.
     * If the given path is absolute, then don't change it.
     */
    public static String makePath(String relativeDir, String basePath) {
        if (basePath == null) {
            return null;
        }

        if (Paths.get(basePath).isAbsolute()) {
            return basePath;
        }

        return Paths.get(relativeDir, basePath).toString();
    }
}
