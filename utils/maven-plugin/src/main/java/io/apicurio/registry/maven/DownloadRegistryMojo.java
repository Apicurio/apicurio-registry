/*
 * Copyright 2018 Confluent Inc. (adapted from their Mojo)
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

package io.apicurio.registry.maven;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactExtensionType;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Download artifacts.
 *
 * @author Ales Justin
 */
@Mojo(name = "download")
public class DownloadRegistryMojo extends AbstractRegistryMojo {

    /**
     * Set of artifact ids to download.
     */
    @Parameter(required = true)
    Set<String> ids = new LinkedHashSet<>();

    /**
     * Map of <artifact-id, artifact-version>.
     * If such mapping exists, particular version will be downloaded,
     * instead of default latest one.
     */
    @Parameter
    Map<String, Integer> versions = new LinkedHashMap<>();

    /**
     * Overwrite artifact file extension to use when writing the artifact to the filesystem.
     */
    @Parameter
    String artifactExtension;

    /**
     * Map of <artifact-id, artifact extension>,
     * aka mapping of explicit artifact extension,
     * if no such extension exists, default one is used.
     */
    @Parameter
    Map<String, String> artifactExtensions = new LinkedHashMap<>();

    /**
     * Output directory, where artifacts get written.
     */
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
            // Explicit file extension is not defined, getting it from the metadata
            if (ext == null || ext.equals("")) {
                try {
                    ArtifactMetaData artifactMetaData = getClient().getArtifactMetaData(id);
                    ext = ".".concat(ArtifactExtensionType.fromArtifactType(artifactMetaData.getType()).toString());
                } catch (Exception ex) {
                    throw new MojoExecutionException(
                            String.format("Exception thrown while getting artifact [%s] metadata", id),
                            ex
                    );
                }
            }
            String fileName = String.format("%s%s", id, ext);
            File outputFile = new File(outputDirectory, fileName);

            getLog().info(String.format("Downloading artifact for id [%s] to %s.", id, outputFile));

            Integer version = versions.get(id);
            try (InputStream stream = (version != null) ?
                    getClient().getArtifactVersion(id, version) :
                    getClient().getLatestArtifact(id)) {
                if (replaceExisting) {
                    Files.copy(stream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.copy(stream, outputFile.toPath());
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
