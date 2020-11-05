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

import java.net.HttpURLConnection;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.IfExistsType;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author Ales Justin
 */
public abstract class AbstractCrudIdStrategy<T> implements GlobalIdStrategy<T> {

    protected boolean isNotFound(Response response) {
        return response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }

    protected abstract long initialLookup(RegistryRestClient service, String artifactId, ArtifactType artifactType, T schema);

    protected void afterCreateArtifact(T schema, ArtifactMetaData amd) {
    }

    @Override
    public long findId(RegistryRestClient client, String artifactId, ArtifactType artifactType, T schema) {
        try {
            return initialLookup(client, artifactId, artifactType, schema);
        } catch (WebApplicationException e) {
            if (isNotFound(e.getResponse())) {
                // TODO add an option to search by strict content?
                ArtifactMetaData amd = client.createArtifact(artifactId, artifactType, toStream(schema), IfExistsType.RETURN_OR_UPDATE, true);
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
