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

package io.apicurio.registry.serde.fallback;

import java.util.Map;

import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.resolver.strategy.ArtifactReference;

/**
 * Interface for providing a fallback ArtifactReference when the SchemaResolver is not able to find an ArtifactReference in the kafka message
 *
 * @author Fabian Martinez
 */
public interface FallbackArtifactProvider {

    default void configure(Map<String, Object> configs, boolean isKey) {
    }

    /**
     * Returns an ArtifactReference that will be used as the fallback
     * to search in the registry for the artifact that will be used to deserialize the kafka message
     * @param topic
     * @param headers , can be null
     * @param data
     * @return
     */
    public ArtifactReference get(String topic, Headers headers, byte[] data);

}
