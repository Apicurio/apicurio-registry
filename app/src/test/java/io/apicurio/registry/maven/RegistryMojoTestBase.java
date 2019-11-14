/*
 * Copyright 2018 Confluent Inc. (adapted from their MojoTest)
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.maven;

import io.apicurio.registry.AbstractResourceTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Ales Justin
 */
public class RegistryMojoTestBase extends AbstractResourceTestBase {
    protected File tempDirectory;

    @BeforeEach
    public void createTempDirectory() throws IOException {
        this.tempDirectory = File.createTempFile(getClass().getSimpleName(), "tmp");
        this.tempDirectory.delete();
        this.tempDirectory.mkdirs();
    }

    @AfterEach
    public void cleanupTempDirectory() {
        for (File tempFile : this.tempDirectory.listFiles()) {
            tempFile.delete();
        }
        this.tempDirectory.delete();
    }

    protected void writeContent(File outputPath, byte[] content) throws IOException {
        try (OutputStream writer = new FileOutputStream(outputPath)) {
            writer.write(content);
        }
    }
}
