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

package io.apicurio.registry.streams.topology;

import com.google.common.collect.ImmutableMap;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.streams.StreamsProperties;
import io.apicurio.registry.streams.topology.processor.ContentIdProcessor;
import io.apicurio.registry.streams.topology.processor.GlobalIdProcessor;
import io.apicurio.registry.streams.topology.transformer.ArtifactContentTransformer;
import io.apicurio.registry.streams.topology.transformer.ArtifactWithoutContentTransformer;
import io.apicurio.registry.streams.topology.transformer.ContentTransformer;
import io.apicurio.registry.streams.topology.transformer.StorageTransformer;
import io.apicurio.registry.streams.utils.ArtifactKeySerde;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.kafka.ProtoSerde;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

import java.util.function.Supplier;


/**
 * Request --> Storage (topic / store) --> GlobalId (topic / store)
 *
 * @author Ales Justin
 */
public class StreamsTopologyProvider implements Supplier<Topology> {
    private final StreamsProperties properties;
    private final ForeachAction<? super Str.ArtifactKey, ? super Str.Data> dataDispatcher;
    private final ArtifactTypeUtilProviderFactory factory;

    public StreamsTopologyProvider(
            StreamsProperties properties,
            ForeachAction<? super Str.ArtifactKey, ? super Str.Data> dataDispatcher,
            ArtifactTypeUtilProviderFactory factory
    ) {
        this.properties = properties;
        this.dataDispatcher = dataDispatcher;
        this.factory = factory;
    }

    @Override
    public Topology get() {
        StreamsBuilder builder = new StreamsBuilder();

        // Simple defaults
        ImmutableMap<String, String> configuration = ImmutableMap.of(
                TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT,
                TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG, "0",
                TopicConfig.SEGMENT_BYTES_CONFIG, String.valueOf(64 * 1024 * 1024)
        );

        String storageStoreName = properties.getStorageStoreName();
        String contentStoreName = properties.getContentStoreName();
        String globalIdStoreName = properties.getGlobalIdStoreName();

        // Data structure holds all artifact information
        // Global rules are Data as well, with constant artifactId (GLOBAL_RULES variable)
        StoreBuilder<KeyValueStore<Str.ArtifactKey /* artifactId */, Str.Data>> storageStoreBuilder =
                Stores
                        .keyValueStoreBuilder(
                                Stores.inMemoryKeyValueStore(storageStoreName),
                                new ArtifactKeySerde(), ProtoSerde.parsedWith(Str.Data.parser())
                        )
                        .withCachingEnabled()
                        .withLoggingEnabled(configuration);

        StoreBuilder<KeyValueStore<Long /* contentId */, Str.ContentValue>> contentStoreBuilder =
                Stores
                        .keyValueStoreBuilder(
                                Stores.inMemoryKeyValueStore(contentStoreName),
                                Serdes.Long(), ProtoSerde.parsedWith(Str.ContentValue.parser())
                        )
                        .withCachingEnabled()
                        .withLoggingEnabled(configuration);

        StoreBuilder<KeyValueStore<Long /* globalId */, Str.TupleValue>> globalIdStoreBuilder =
                Stores
                        .keyValueStoreBuilder(
                                Stores.inMemoryKeyValueStore(globalIdStoreName),
                                Serdes.Long(), ProtoSerde.parsedWith(Str.TupleValue.parser())
                        )
                        .withCachingEnabled()
                        .withLoggingEnabled(configuration);

        builder.addStateStore(storageStoreBuilder);
        builder.addStateStore(contentStoreBuilder);
        builder.addStateStore(globalIdStoreBuilder);

        // Input topic -- storage topic
        // This is where we handle "http" requests
        // Key is artifactId -- which is also used for KeyValue store key
        KStream<Str.ArtifactKey, Str.StorageValue> storageRequest = builder.stream(
                properties.getStorageTopic(),
                Consumed.with(new ArtifactKeySerde(), ProtoSerde.parsedWith(Str.StorageValue.parser()))
        );

        // We transform artifact with content bytes to artifact with content value
        KStream<Str.ArtifactKey, Str.StorageValue> storageRequestTransformed = storageRequest
                .transform(() -> new ArtifactContentTransformer(properties, factory), contentStoreName);

        // We transform <artifactId, Data> into simple mapping <contentId, content>
        KStream<Long, Str.ContentValue> contentRequest = storageRequestTransformed
                .transform(() -> new ContentTransformer(properties), contentStoreName)
                .through(
                        properties.getContentTopic(),
                        Produced.with(Serdes.Long(), ProtoSerde.parsedWith(Str.ContentValue.parser()))
                );

        //We remove content value from artifact, just leaving the id and then transform
        // transform <artifactId, Data> into simple mapping <globalId, <artifactId, version>>
        KStream<Long, Str.TupleValue> globalRequest = storageRequestTransformed
                .transform(ArtifactWithoutContentTransformer::new)
                .transform(() -> new StorageTransformer(properties, dataDispatcher), storageStoreName
        ).through(
                properties.getGlobalIdTopic(),
                Produced.with(Serdes.Long(), ProtoSerde.parsedWith(Str.TupleValue.parser()))
        );

        //Just handle contentId mapping
        contentRequest.process(() -> new ContentIdProcessor(contentStoreName), contentStoreName);

        // Just handle globalId mapping -- put or delete
        globalRequest.process(() -> new GlobalIdProcessor(globalIdStoreName), globalIdStoreName);

        return builder.build(properties.getProperties());
    }
}
