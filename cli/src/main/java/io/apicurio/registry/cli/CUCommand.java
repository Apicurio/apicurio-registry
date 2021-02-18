/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.cli;

import io.apicurio.registry.utils.IoUtil;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * @author Ales Justin
 */
public abstract class CUCommand extends ArtifactCommand {
    @CommandLine.ArgGroup(multiplicity = "1")
    Exclusive exclusive;

    static class Exclusive {
        @CommandLine.Option(names = {"-f", "--file"}, description = "Artifact file")
        File file;

        @CommandLine.Option(names = {"-c", "--content"}, description = "Artifact content")
        String content;
    }

    protected abstract Object run(InputStream data) throws IOException;

    @Override
    public void run() {
        try {
            InputStream data;
            if (exclusive.file != null) {
                data = new FileInputStream(exclusive.file);
            } else if (exclusive.content != null && exclusive.content.length() > 0) {
                data = IoUtil.toStream(exclusive.content);
            } else {
                throw new IllegalArgumentException("Missing content!");
            }
            String content = IoUtil.toString(data);
            log.fine("Content: " + content);
            data = IoUtil.toStream(content);

            Object result = run(data);
            println(String.format("Response [%s]: " + result, spec.name()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
