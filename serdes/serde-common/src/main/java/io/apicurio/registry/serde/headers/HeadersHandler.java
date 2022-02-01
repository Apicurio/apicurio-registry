/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.serde.headers;

import java.util.Map;

import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.resolver.strategy.ArtifactReference;


/**
 * Common interface for headers handling when serializing/deserializing kafka records that have {@link Headers}
 *
 * @author Fabian Martinez
 */
public interface HeadersHandler {

    default void configure(Map<String, Object> configs, boolean isKey) {
    }

    public void writeHeaders(Headers headers, ArtifactReference reference);

    /**
     * Reads the kafka message headers and returns an ArtifactReference that can contain or not information to identify an Artifact in the registry.
     * @param headers
     * @return ArtifactReference
     */
    public ArtifactReference readHeaders(Headers headers);

}
