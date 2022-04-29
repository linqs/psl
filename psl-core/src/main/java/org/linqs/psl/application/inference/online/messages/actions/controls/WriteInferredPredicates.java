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
package org.linqs.psl.application.inference.online.messages.actions.controls;

import org.linqs.psl.application.inference.online.messages.OnlineMessage;

/**
 * Write inferred predicates at a location on the server.
 * If no path is specified then inferred predicates are written to stdout.
 * String format: WriteInferredPredicates [path]
 */
public class WriteInferredPredicates extends OnlineMessage {
    private String outputDirectoryPath;

    public WriteInferredPredicates(String outputDirectoryPath) {
        super();
        this.outputDirectoryPath = outputDirectoryPath;
    }

    public String getOutputDirectoryPath() {
        return outputDirectoryPath;
    }

    @Override
    public String toString() {
        if (outputDirectoryPath == null) {
            return String.format("WRITEINFERREDPREDICATES");
        } else {
            return String.format("WRITEINFERREDPREDICATES\t%s", outputDirectoryPath);
        }
    }
}
