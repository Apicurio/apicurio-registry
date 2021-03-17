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

package io.apicurio.registry.streams.topology.transformer;

import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.storage.proto.Str.ArtifactKey;
import io.apicurio.registry.storage.proto.Str.StorageValue;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

public class ArtifactWithoutContentTransformer implements Transformer<ArtifactKey, StorageValue, KeyValue<Str.ArtifactKey, Str.StorageValue>> {

    @Override
    public void init(ProcessorContext context) {
    }

    @Override
    public KeyValue<Str.ArtifactKey, Str.StorageValue> transform(ArtifactKey key, StorageValue value) {

        Str.ActionType action = value.getType();
        switch (action) {
            case CREATE:
            case UPDATE:
            case DELETE:
                if (value.getVt() != Str.ValueType.ARTIFACT_CONTENT_VALUE) {
                    //just return, not an artifact
                    return new KeyValue<>(key, value);
                } else {
                    return new KeyValue<>(key,  transformValue(key, value));
                }
            default:
                return null; // just skip?
        }    }

    private StorageValue transformValue(ArtifactKey key, StorageValue value) {

        final Str.ArtifactContentValue artifactContentValue = value.getArtifactContentValue();

        final Str.ArtifactData artifactData = Str.ArtifactData.newBuilder()
                .putAllMetadata(artifactContentValue.getMetadataMap())
                .setArtifactType(artifactContentValue.getArtifactType())
                .setContentId(artifactContentValue.getContent().getId())
                .setId(artifactContentValue.getId())
                .build();

        final Str.StorageValue storageValue = Str.StorageValue.newBuilder()
                .setType(value.getType())
                .setKey(key)
                .setArtifactData(artifactData)
                .setVt(Str.ValueType.ARTIFACT_DATA)
                .setVersion(value.getVersion())
                .build();

        return storageValue;
    }

    @Override
    public void close() {

    }
}
