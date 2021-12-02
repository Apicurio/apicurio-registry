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

package io.apicurio.registry.serde.strategy;

/**
 * A {@link ArtifactResolverStrategy} is used by the Kafka serializer/deserializer to determine
 * the {@link ArtifactReference} under which the message schemas are located or should be registered
 * in the registry. The default is {@link TopicIdStrategy}.
 *
 * @author Fabian Martinez
 */
public interface ArtifactResolverStrategy<T> {

    /**
     * For a given topic and message, returns the {@link ArtifactReference} under which the message schemas are located or should be registered
     * in the registry.
     *
     * @param topic the Kafka topic name to which the message is being published.
     * @param isKey true when encoding a message key, false for a message value.
     * @param schema the schema of the message being serialized/deserialized, can be null if we don't know it beforehand
     * @return the {@link ArtifactReference} under which the message schemas are located or should be registered
     */
    ArtifactReference artifactReference(String topic, boolean isKey, T schema);

    /**
     * Whether or not to load and pass the parsed schema to the {@link ArtifactResolverStrategy#artifactReference(String, boolean, Object)} lookup method
     */
    default boolean loadSchema() {
        return true;
    }

}
