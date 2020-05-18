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

package io.apicurio.registry.utils.serde.strategy;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;

import java.net.HttpURLConnection;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public abstract class AbstractCrudIdStrategy<T> implements GlobalIdStrategy<T> {

    protected <R> R unwrap(CompletionStage<R> cs) {
        return ConcurrentUtil.result(cs);
    }

    protected boolean isNotFound(Response response) {
        return response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }

    protected abstract long initialLookup(RegistryService service, String artifactId, ArtifactType artifactType, T schema);

    protected void afterCreateArtifact(T schema, ArtifactMetaData amd) {
    }

    @Override
    public long findId(RegistryService service, String artifactId, ArtifactType artifactType, T schema) {
        try {
            return initialLookup(service, artifactId, artifactType, schema);
        } catch (WebApplicationException e) {
            if (isNotFound(e.getResponse())) {
                CompletionStage<ArtifactMetaData> cs = service.createArtifact(artifactType, artifactId, null, toStream(schema));
                ArtifactMetaData amd = unwrap(cs);
                afterCreateArtifact(schema, amd);
                return amd.getGlobalId();
            } else {
                throw new IllegalStateException(String.format(
                    "Error [%s] retrieving schema: %s",
                    e.getMessage(),
                    artifactId)
                );
            }
        }
    }
}
