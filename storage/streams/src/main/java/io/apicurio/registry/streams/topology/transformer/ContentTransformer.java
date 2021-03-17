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
import io.apicurio.registry.streams.StreamsProperties;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

public class ContentTransformer implements Transformer<Str.ArtifactKey, Str.StorageValue, KeyValue<Long, Str.ContentValue>> {

    private final StreamsProperties properties;

    private KeyValueStore<Long, Str.ContentValue> contentStore;

    public ContentTransformer(StreamsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void init(ProcessorContext context) {
        //noinspection unchecked
        contentStore = (KeyValueStore<Long, Str.ContentValue>) context.getStateStore(properties.getContentStoreName());
    }

    @Override
    public KeyValue<Long, Str.ContentValue> transform(Str.ArtifactKey key, Str.StorageValue value) {
        if (value.getVt() == Str.ValueType.ARTIFACT_CONTENT_VALUE) {
            final Str.ContentValue contentValue = value.getArtifactContentValue().getContent();

            final KeyValueIterator<Long, Str.ContentValue> keyValueIterator = contentStore.all();
            while (keyValueIterator.hasNext()) {
                KeyValue<Long, Str.ContentValue> keyValue = keyValueIterator.next();
                if (contentValue.getContentHash().equals(keyValue.value.getContentHash())) {
                    //skip, content already exists
                    return null;
                }
            }
            return new KeyValue<>(contentValue.getId(), contentValue);
        }
        //Just skip, not an artifact
        return null;
    }

    @Override
    public void close() {

    }
}
