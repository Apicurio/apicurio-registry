/*
 * Copyright 2018 Confluent Inc. (adapted from their Mojo)
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.maven;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.WebApplicationException;

/**
 * Register artifacts against registry.
 *
 * @author Ales Justin
 */
@Mojo(name = "register")
public class RegisterRegistryMojo extends ContentRegistryMojo {

    /**
     * Artifact versions / results of registry.
     */
    Map<String, Integer> artifactVersions;

    public ArtifactMetaData register(String artifactId, ArtifactType artifactType, StreamHandle handle) throws IOException {
        try {
            try (InputStream stream = handle.stream()) {
                CompletionStage<ArtifactMetaData> cs = getClient().updateArtifact(artifactId, artifactType, stream);
                return unwrap(cs);
            }
        } catch (WebApplicationException e) {
            if (isNotFound(e.getResponse())) {
                try (InputStream stream = handle.stream()) {
                    CompletionStage<ArtifactMetaData> cs = getClient().createArtifact(artifactType, artifactId, null, stream);
                    return unwrap(cs);
                }
            } else {
                throw new IllegalStateException(String.format(
                    "Error [%s] retrieving artifact: %s",
                    e.getMessage(),
                    artifactId)
                );
            }
        }
    }

    @Override
    protected void executeInternal() throws MojoExecutionException {
        validate();

        artifactVersions = new LinkedHashMap<>();

        int errors = 0;
        for (Map.Entry<String, StreamHandle> kvp : prepareArtifacts().entrySet()) {
            try {
                ArtifactType at = getArtifactType(kvp.getKey());

                if (getLog().isDebugEnabled()) {
                    getLog().debug(String.format("Registering artifact [%s]: '%s'", at, kvp.getKey()));
                }

                ArtifactMetaData amd = register(kvp.getKey(), at, kvp.getValue());
                getLog().info(
                    String.format(
                        "Registered artifact [%s] with global id %s, version %s",
                        kvp.getKey(),
                        amd.getGlobalId(),
                        amd.getVersion()
                    ));
                artifactVersions.put(kvp.getKey(), amd.getVersion());
            } catch (Exception e) {
                errors++;
                getLog().error(
                    String.format("Exception while registering id [%s]", kvp.getKey()),
                    e
                );
            }
        }

        if (errors > 0) {
            throw new MojoExecutionException("Errors while registering artifacts ...");
        }
    }
}
