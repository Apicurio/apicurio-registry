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
import io.apicurio.registry.utils.ConcurrentUtil;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.core.Response;

/**
 * Base class for Mojo's that handle content.
 *
 * @author Ales Justin
 */
public abstract class ContentRegistryMojo extends AbstractRegistryMojo {

    /**
     * Map of <artifactId, path-to-artifact-file>,
     * this artifacts then get used in the mojo.
     */
    @Parameter(required = true)
    Map<String, File> artifacts = new LinkedHashMap<>();

    /**
     * Default artifact type, if per-artifact artifact type is not set.
     */
    @Parameter
    ArtifactType artifactType;

    /**
     * Map of <artifactId, artifact type>,
     * aka map artifact ids with their artifact types.
     */
    @Parameter
    Map<String, ArtifactType> artifactTypes = new LinkedHashMap<>();

    /**
     * Get artifact type for artifact id.
     * If no explicit mapping is found, default artifact type is returned.
     *
     * @param key the artifact id
     * @return artifact type, can be null
     */
    protected ArtifactType getArtifactType(String key) {
        return artifactTypes.getOrDefault(key, artifactType);
    }

    protected Map<String, StreamHandle> prepareArtifacts() {
        Map<String, StreamHandle> results = new LinkedHashMap<>();
        for (Map.Entry<String, File> kvp : artifacts.entrySet()) {
            results.put(kvp.getKey(), () -> {
                getLog().debug(
                    String.format(
                        "Loading artifact for id [%s] from %s.",
                        kvp.getKey(),
                        kvp.getValue()
                    )
                );
                return new FileInputStream(kvp.getValue());
            });
        }
        return results;
    }

    protected <R> R unwrap(CompletionStage<R> cs) {
        return ConcurrentUtil.result(cs);
    }

    protected boolean isBadRequest(Response response) {
        return response.getStatus() == HttpURLConnection.HTTP_BAD_REQUEST;
    }

    protected boolean isNotFound(Response response) {
        return response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }

    protected void validate() {
        if (artifactType == null && artifactTypes.isEmpty()) {
            getLog().warn("Both - artifactType and artifactTypes - is not configured!");
        }
    }
}
