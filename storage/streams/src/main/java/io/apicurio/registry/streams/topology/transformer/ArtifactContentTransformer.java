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

import com.google.protobuf.ByteString;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.streams.StreamsProperties;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtifactContentTransformer implements Transformer<Str.ArtifactKey, Str.StorageValue, KeyValue<Str.ArtifactKey, Str.StorageValue>> {
    public static final Logger log = LoggerFactory.getLogger(ArtifactContentTransformer.class);

    private final StreamsProperties properties;

    private ProcessorContext context;
    private KeyValueStore<Long, Str.ContentValue> contentStore;
    private ArtifactTypeUtilProviderFactory factory;

    public ArtifactContentTransformer(
            StreamsProperties properties,
            ArtifactTypeUtilProviderFactory factory
    ) {
        this.properties = properties;
        this.factory = factory;
    }

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
        //noinspection unchecked
        contentStore = (KeyValueStore<Long, Str.ContentValue>) context.getStateStore(properties.getContentStoreName());
    }

    @Override
    public KeyValue<Str.ArtifactKey, Str.StorageValue> transform(Str.ArtifactKey artifactKey, Str.StorageValue value) {

        long offset = context.offset();
        // should be unique enough
        long globalId = properties.toGlobalId(offset, context.partition());

        Str.ActionType action = value.getType();
        switch (action) {
            case CREATE:
            case UPDATE:
                if (value.getVt() == Str.ValueType.ARTIFACT) {
                    return handleContent(artifactKey, value, globalId);
                } else {
                    return new KeyValue<>(artifactKey, value);
                }
            case DELETE:
                if (value.getVt() == Str.ValueType.ARTIFACT) {
                    value = Str.StorageValue.newBuilder(value)
                            .setVt(Str.ValueType.ARTIFACT_CONTENT_VALUE)
                            .build();
                }
                return new KeyValue<>(artifactKey, value);
            default:
                return null; // just skip?
        }
    }

    @Override
    public void close() {
    }

    private KeyValue<Str.ArtifactKey, Str.StorageValue> handleContent(Str.ArtifactKey key, Str.StorageValue value, long globalId) {

        final Str.ArtifactValue artifactValue = value.getArtifact();
        final ArtifactType type = ArtifactType.values()[artifactValue.getArtifactType()];

        final Str.ContentValue contentValue = insertContentOrReturnId(artifactValue.getContent().toByteArray(), type, globalId);

        final Str.ArtifactContentValue artifactContentValue = Str.ArtifactContentValue.newBuilder()
                .setContent(contentValue)
                .setArtifactType(artifactValue.getArtifactType())
                .putAllMetadata(artifactValue.getMetadataMap())
                .build();

        final Str.StorageValue storageValue = Str.StorageValue.newBuilder()
                .setType(value.getType())
                .setKey(key)
                .setArtifactContentValue(artifactContentValue)
                .setVt(Str.ValueType.ARTIFACT_CONTENT_VALUE)
                .build();

        return new KeyValue<>(key, storageValue);
    }

    private Str.ContentValue insertContentOrReturnId(byte[] content, ArtifactType artifactType, long globalId) {

        final String candidateHash = DigestUtils.sha256Hex(content);

        final KeyValueIterator<Long, Str.ContentValue> keyValueIterator = contentStore.all();
        while (keyValueIterator.hasNext()) {
            KeyValue<Long, Str.ContentValue> keyValue = keyValueIterator.next();
            if (candidateHash.equals(keyValue.value.getContentHash())) {
                return keyValue.value;
            }
        }
        return createContent(globalId, artifactType, content, candidateHash);
    }

    private Str.ContentValue createContent(long globalId, ArtifactType artifactType, byte[] content, String contentHash) {

        final ContentHandle canonicalContent = canonicalizeContent(artifactType, ContentHandle.create(content));
        final byte[] canonicalContentBytes = canonicalContent.bytes();
        final String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

        return Str.ContentValue.newBuilder()
                .setContent(ByteString.copyFrom(content))
                .setContentHash(contentHash)
                .setCanonicalHash(canonicalContentHash)
                .setId(globalId)
                .build();
    }

    /**
     * Canonicalize the given content, returns the content unchanged in the case of an error.
     *
     * @param artifactType
     * @param content
     */
    private ContentHandle canonicalizeContent(ArtifactType artifactType, ContentHandle content) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            return canonicalizer.canonicalize(content);
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType.name());
            return content;
        }
    }
}
