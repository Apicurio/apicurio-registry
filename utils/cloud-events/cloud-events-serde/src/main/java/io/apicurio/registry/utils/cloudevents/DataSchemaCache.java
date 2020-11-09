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
package io.apicurio.registry.utils.cloudevents;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.apicurio.registry.client.RegistryRestClient;
import io.cloudevents.CloudEvent;

/**
 * @author Fabian Martinez
 */
public abstract class DataSchemaCache<T> {
    private final RegistryRestClient client;
    private final Map<Long, DataSchemaEntry<T>> schemas = new ConcurrentHashMap<>();
    private final Map<String, Long> globalIdsCache = new ConcurrentHashMap<>();

    public DataSchemaCache(RegistryRestClient client) {
        this.client = Objects.requireNonNull(client);
    }

    protected abstract T toSchema(InputStream response);

    //TODO investigate to do this with only one request to registry api
    public DataSchemaEntry<T> getSchema(CloudEvent cloudEvent) {
        Long globalId = lookupGlobalId(cloudEvent);
        return schemas.computeIfAbsent(globalId, key -> {
            InputStream artifactResponse;
            try {
                artifactResponse = client.getArtifactByGlobalId(key);
            } catch (Exception e) {
                throw new IllegalStateException(
                    String.format(
                        "Error retrieving schema: %s",
                        key
                    ),
                    e
                );
            }
            T schema = toSchema(artifactResponse);
            DataSchemaEntry<T> entry = new DataSchemaEntry<>();
            entry.setDataSchema("apicurio-global-id-"+globalId);
            entry.setSchema(schema);
            return entry;
        });
    }

    public void clear() {
        schemas.clear();
    }

    private Long lookupGlobalId(CloudEvent event) {
        if (event.getDataSchema() != null) {
            return globalIdsCache.computeIfAbsent(event.getDataSchema().toString(), key -> getGlobalIdByDataSchema(key));
        } else {
            String artifactId = event.getType();
            return globalIdsCache.computeIfAbsent(artifactId, key -> client.getArtifactMetaData(artifactId).getGlobalId());
        }
    }

    private Long getGlobalIdByDataSchema(String dataschema) {
        if (dataschema.startsWith("/schemagroups")) {
            //TODO
            // /schemagroups/{group-id}/schemas/{schema-id}/versions/{version-number}
        } else if (dataschema.startsWith("/apicurio")) {
            String[] apicurioArtifactTokens = Stream.of(dataschema.split("/"))
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList()).toArray(new String[0]);
            String artifactId = null;
            if (apicurioArtifactTokens.length > 1) {
                artifactId = apicurioArtifactTokens[1];
            }
            String version = null;
            if (apicurioArtifactTokens.length > 2) {
                version = apicurioArtifactTokens[2];
            }
            if (artifactId == null) {
                throw new IllegalStateException("Bad apicurio dataschema URI");
            }
            if (version == null) {
                //this case should not be cached
                return client.getArtifactMetaData(artifactId).getGlobalId();
            } else {
                if (version.length() > 1 && version.toLowerCase().startsWith("v")) {
                    version = version.substring(1);
                }
                Integer artifactVersion = Integer.parseInt(version);
                try {
                    return client.getArtifactVersionMetaData(artifactId, artifactVersion).getGlobalId();
                } catch (Exception e) {
                    throw new IllegalStateException("Artifact not found", e);
                }
            }
        } else if (dataschema.startsWith("apicurio-global-id-")) {
            String apicurioGlobalId = dataschema.substring("apicurio-global-id-".length());
            return Long.parseLong(apicurioGlobalId);
        }
        throw new IllegalStateException("Unable to find schema "+dataschema);
    }

}
