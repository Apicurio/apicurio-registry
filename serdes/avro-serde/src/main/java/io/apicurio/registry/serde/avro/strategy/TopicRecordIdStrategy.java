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

package io.apicurio.registry.serde.avro.strategy;

import org.apache.avro.Schema;

import io.apicurio.registry.serde.strategy.ArtifactReference;

/**
 * @author Fabian Martinez
 */
public class TopicRecordIdStrategy extends RecordIdStrategy {

    /**
     * @see io.apicurio.registry.serde.avro.strategy.RecordIdStrategy#artifactReference(java.lang.String, boolean, org.apache.avro.Schema)
     */
    @Override
    public ArtifactReference artifactReference(String topic, boolean isKey, Schema schema) {
        ArtifactReference reference = super.artifactReference(topic, isKey, schema);
        return ArtifactReference.builder()
                .groupId(reference.getGroupId())
                .artifactId(topic + "-" + reference.getArtifactId())
                .version(reference.getVersion())
                .build();
    }

}
