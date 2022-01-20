/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.serde.data;

import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

/**
 * Kafka specific implementation for the Record Metadata abstraction used by the SchemaResolver
 * @author Fabian Martinez
 */
public class KafkaSerdeMetadata implements Metadata {

    private String topic;
    private boolean isKey;
    private Headers headers;

    public KafkaSerdeMetadata(String topic, boolean isKey, Headers headers) {
        this.topic = topic;
        this.isKey = isKey;
        this.headers = headers;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Metadata#artifactReference()
     */
    @Override
    public ArtifactReference artifactReference() {
        return null;
    }

    /**
     * @return the topic
     */
    public String getTopic() {
        return topic;
    }
    /**
     * @return the isKey
     */
    public boolean isKey() {
        return isKey;
    }

    /**
     * @return the headers
     */
    public Headers getHeaders() {
        return headers;
    }

}
