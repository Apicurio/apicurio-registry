/*
 * Copyright 2018 Confluent Inc.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
@Mojo(name = "download")
public class DownloadRegistryMojo extends AbstractRegistryMojo {

    @Parameter(defaultValue = ".avsc")
    String artifactExtension;
    @Parameter(required = true)
    List<String> ids = new ArrayList<>();
    @Parameter(required = true)
    File outputDirectory;

    Map<String, byte[]> downloadSchemas(Collection<String> ids) throws MojoExecutionException {
        Map<String, byte[]> results = new LinkedHashMap<>();

        for (String id : ids) {
            try {
                getLog().info(String.format("Downloading latest content for %s.", id));
                Response response = getClient().getLatestArtifact(id);
                results.put(id, response.readEntity(byte[].class));
            } catch (Exception ex) {
                throw new MojoExecutionException(
                    String.format("Exception thrown while downloading content for %s.", id),
                    ex
                );
            }
        }

        return results;
    }

    @Override
    protected void executeInternal() throws MojoExecutionException {
        try {
            getLog().debug(String.format("Checking if '%s' exists and is not a directory.", this.outputDirectory));
            if (outputDirectory.exists() && !outputDirectory.isDirectory()) {
                throw new IllegalStateException("outputDirectory must be a directory");
            }
            getLog().debug(String.format("Checking if outputDirectory('%s') exists.", this.outputDirectory));
            if (!outputDirectory.isDirectory()) {
                getLog().debug(String.format("Creating outputDirectory('%s').", this.outputDirectory));
                if (!outputDirectory.mkdirs()) {
                    throw new IllegalStateException("Could not create output directory " + this.outputDirectory);
                }
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("Exception thrown while creating outputDirectory", ex);
        }

        Map<String, byte[]> artifacts = downloadSchemas(ids);
        for (Map.Entry<String, byte[]> kvp : artifacts.entrySet()) {
            String fileName = String.format("%s%s", kvp.getKey(), artifactExtension);
            File outputFile = new File(this.outputDirectory, fileName);

            getLog().info(String.format("Writing artifact for id [%s] to %s.", kvp.getKey(), outputFile));

            try (OutputStream writer = new FileOutputStream(outputFile)) {
                writer.write(kvp.getValue());
            } catch (IOException ex) {
                throw new MojoExecutionException(
                    String.format("Exception thrown while writing subject('%s') schema to %s", kvp.getKey(),
                                  outputFile),
                    ex
                );
            }
        }
    }
}
