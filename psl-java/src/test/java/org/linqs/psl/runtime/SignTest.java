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
package org.linqs.psl.runtime;

import org.apache.logging.log4j.core.util.IOUtils;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.Runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class SignTest extends RuntimeTest {
    @Test
    public void testBase() {
        try {
            Process pslpython = Runtime.getRuntime().exec("python3 -c \"import pslpython\"");
            pslpython.waitFor();
            if (pslpython.exitValue() != 0) {
                System.out.println("PSL Python package not installed, skipping test.");
                return;
            }
            System.out.println("PSL Python package installed, running test.");

        } catch (Exception e) {
            System.out.println("PSL Python package not installed, skipping test.");
            return;
        }

        String path = Paths.get(resourceDir, "sign", "deep.json").toString();
        run(path);
    }
}
