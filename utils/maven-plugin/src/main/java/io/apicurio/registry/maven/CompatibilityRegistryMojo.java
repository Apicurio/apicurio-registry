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

import io.apicurio.registry.types.ArtifactType;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.WebApplicationException;

/**
 * @author Ales Justin
 */
@Mojo(name = "compatibility")
public class CompatibilityRegistryMojo extends ContentRegistryMojo {

    Map<String, Boolean> compatibility;

    @Override
    protected void executeInternal() throws MojoExecutionException {
        validate();

        Map<String, StreamHandle> artifacts = loadArtifacts(ids);
        compatibility = new LinkedHashMap<>();

        int errors = 0;
        for (Map.Entry<String, StreamHandle> kvp : artifacts.entrySet()) {
            try {
                ArtifactType at = getArtifactType(kvp.getKey());

                if (getLog().isDebugEnabled()) {
                    getLog().debug(String.format("Testing artifact [%s]: '%s'", at, kvp.getKey()));
                }

                try (InputStream stream = kvp.getValue().stream()) {
                    getClient().testCompatibility(kvp.getKey(), at, stream);
                }
                getLog().info(String.format("Artifact '%s' is compatible.", kvp.getKey()));
                compatibility.put(kvp.getKey(), Boolean.TRUE);
            } catch (WebApplicationException e) {
                if (isBadRequest(e.getResponse())) {
                    compatibility.put(kvp.getKey(), Boolean.FALSE);
                } else {
                    errors++;
                    getLog().error(
                        String.format("Exception while testing artifact [%s]", kvp.getKey()),
                        e
                    );
                }
            } catch (Exception e) {
                errors++;
                getLog().error(
                    String.format("Exception while testing artifact [%s]", kvp.getKey()),
                    e
                );
            }
        }

        if (errors > 0) {
            throw new MojoExecutionException("Errors while testing artifacts ...");
        }
    }
}
