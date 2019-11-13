/*
 * Copyright 2018 Confluent Inc. (adapted from their Mojo)
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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
@Mojo(name = "download")
public class DownloadRegistryMojo extends AbstractRegistryMojo {

    @Parameter(required = true)
    List<String> ids = new ArrayList<>();

    @Parameter
    Map<String, Integer> versions = new LinkedHashMap<>();

    @Parameter(defaultValue = ".avsc")
    String artifactExtension;

    @Parameter
    Map<String, String> artifactExtensions = new LinkedHashMap<>();

    @Parameter(required = true)
    File outputDirectory;

    @Parameter
    boolean replaceExisting = true;

    @Override
    protected void executeInternal() throws MojoExecutionException {
        try {
            getLog().debug(String.format("Checking if '%s' exists and is not a directory.", outputDirectory));
            if (outputDirectory.exists() && !outputDirectory.isDirectory()) {
                throw new IllegalStateException("outputDirectory must be a directory");
            }
            getLog().debug(String.format("Checking if outputDirectory('%s') exists.", outputDirectory));
            if (!outputDirectory.isDirectory()) {
                getLog().debug(String.format("Creating outputDirectory('%s').", outputDirectory));
                if (!outputDirectory.mkdirs()) {
                    throw new IllegalStateException("Could not create output directory " + outputDirectory);
                }
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("Exception thrown while creating outputDirectory", ex);
        }

        for (String id : ids) {
            String ext = artifactExtensions.getOrDefault(id, artifactExtension);
            String fileName = String.format("%s%s", id, ext);
            File outputFile = new File(outputDirectory, fileName);

            getLog().info(String.format("Downloading artifact for id [%s] to %s.", id, outputFile));

            try {
                Integer version = versions.get(id);
                Response response = (version != null) ?
                                    getClient().getArtifactVersion(version, id) :
                                    getClient().getLatestArtifact(id);
                try (InputStream stream = response.readEntity(InputStream.class)) {
                    if (replaceExisting) {
                        Files.copy(stream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.copy(stream, outputFile.toPath());
                    }
                }
            } catch (Exception ex) {
                throw new MojoExecutionException(
                    String.format("Exception thrown while downloading artifact [%s] to %s", id, outputFile),
                    ex
                );
            }
        }
    }
}
