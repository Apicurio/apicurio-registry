/*
 * Copyright 2018 Confluent Inc.
 * Copyright 2018 Confluent Inc.
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

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.WebApplicationException;

/**
 * @author Ales Justin
 */
@Mojo(name = "register")
public class RegisterRegistryMojo extends ContentRegistryMojo {

    Map<String, Integer> artifactVersions;

    public ArtifactMetaData register(String artifactId, ArtifactType artifactType, byte[] content) {
        try {
            CompletionStage<ArtifactMetaData> cs = getClient().updateArtifact(artifactId, artifactType, new ByteArrayInputStream(content));
            return unwrap(cs);
        } catch (WebApplicationException e) {
            if (isNotFound(e.getResponse())) {
                CompletionStage<ArtifactMetaData> cs = getClient().createArtifact(artifactType, artifactId, new ByteArrayInputStream(content));
                return unwrap(cs);
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

        Map<String, byte[]> artifacts = loadArtifacts(ids);
        artifactVersions = new LinkedHashMap<>();

        int errors = 0;
        for (Map.Entry<String, byte[]> kvp : artifacts.entrySet()) {
            try {
                ArtifactType at = artifactTypes.getOrDefault(kvp.getKey(), artifactType);

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
