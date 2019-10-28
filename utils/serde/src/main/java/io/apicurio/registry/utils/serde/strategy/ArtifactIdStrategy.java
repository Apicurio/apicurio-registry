/*
 * Copyright 2019 Red Hat
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

/**
 * A {@link ArtifactIdStrategy} is used by the Kafka serializer/deserializer to determine
 * the artifact id under which the message schemas should be registered
 * in the registry. The default is {@link TopicIdStrategy}.
 *
 * @author Ales Justin
 */
public interface ArtifactIdStrategy<T> {
    /**
     * For a given topic and message, returns the artifact id under which the
     * schema should be registered in the registry.
     *
     * @param topic the Kafka topic name to which the message is being published.
     * @param isKey true when encoding a message key, false for a message value.
     * @param schema the schema of the message being serialized/deserialized
     * @return the artifact id under which the schema should be registered.
     */
    String artifactId(String topic, boolean isKey, T schema);
}
