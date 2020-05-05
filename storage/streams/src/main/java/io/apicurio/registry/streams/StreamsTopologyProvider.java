package io.apicurio.registry.streams;

import com.google.common.collect.ImmutableMap;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.InvalidArtifactStateException;
import io.apicurio.registry.storage.MetaDataKeys;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.kafka.ProtoSerde;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.utils.StringUtil.isEmpty;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Request --> Storage (topic / store) --> GlobalId (topic / store)
 *
 * @author Ales Justin
 */
public class StreamsTopologyProvider implements Supplier<Topology> {
    private final StreamsProperties properties;
    private final ForeachAction<? super String, ? super Str.Data> dataDispatcher;
    private final ArtifactTypeUtilProviderFactory factory;

    public StreamsTopologyProvider(
        StreamsProperties properties,
        ForeachAction<? super String, ? super Str.Data> dataDispatcher,
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

        // Input topic -- storage topic
        // This is where we handle "http" requests
        // Key is artifactId -- which is also used for KeyValue store key
        KStream<String, Str.StorageValue> storageRequest = builder.stream(
            properties.getStorageTopic(),
            Consumed.with(Serdes.String(), ProtoSerde.parsedWith(Str.StorageValue.parser()))
        );

        // Data structure holds all artifact information
        // Global rules are Data as well, with constant artifactId (GLOBAL_RULES variable)
        String storageStoreName = properties.getStorageStoreName();
        StoreBuilder<KeyValueStore<String /* artifactId */, Str.Data>> storageStoreBuilder =
            Stores
                .keyValueStoreBuilder(
                    Stores.inMemoryKeyValueStore(storageStoreName),
                    Serdes.String(), ProtoSerde.parsedWith(Str.Data.parser())
                )
                .withCachingEnabled()
                .withLoggingEnabled(configuration);

        builder.addStateStore(storageStoreBuilder);

        // We transform <artifactId, Data> into simple mapping <globalId, <artifactId, version>>
        KStream<Long, Str.TupleValue> globalRequest =
            storageRequest.transform(
                () -> new StorageTransformer(properties, dataDispatcher, factory),
                storageStoreName
            ).through(
                properties.getGlobalIdTopic(),
                Produced.with(Serdes.Long(), ProtoSerde.parsedWith(Str.TupleValue.parser()))
            );

        String globalIdStoreName = properties.getGlobalIdStoreName();
        StoreBuilder<KeyValueStore<Long /* globalId */, Str.TupleValue>> globalIdStoreBuilder =
            Stores
                .keyValueStoreBuilder(
                    Stores.inMemoryKeyValueStore(globalIdStoreName),
                    Serdes.Long(), ProtoSerde.parsedWith(Str.TupleValue.parser())
                )
                .withCachingEnabled()
                .withLoggingEnabled(configuration);

        builder.addStateStore(globalIdStoreBuilder);

        // Just handle globalId mapping -- put or delete
        globalRequest.process(() -> new GlobalIdProcessor(globalIdStoreName), globalIdStoreName);

        return builder.build(properties.getProperties());
    }

    private static class GlobalIdProcessor extends AbstractProcessor<Long, Str.TupleValue> {
        private final String storeName;
        private KeyValueStore<Long, Str.TupleValue> store;

        public GlobalIdProcessor(String storeName) {
            this.storeName = storeName;
        }

        @Override
        public void init(ProcessorContext context) {
            super.init(context);
            //noinspection unchecked
            store = (KeyValueStore<Long, Str.TupleValue>) context.getStateStore(storeName);
        }

        @Override
        public void process(Long key, Str.TupleValue value) {
            if (value == null) {
                store.delete(key);
            } else {
                store.put(key, value);
            }
        }
    }

    private static class StorageTransformer implements Transformer<String, Str.StorageValue, KeyValue<Long, Str.TupleValue>> {
        private static final Logger log = LoggerFactory.getLogger(StorageTransformer.class);

        private final StreamsProperties properties;
        private final ForeachAction<? super String, ? super Str.Data> dispatcher;
        private final ArtifactTypeUtilProviderFactory factory;

        private ProcessorContext context;
        private KeyValueStore<String, Str.Data> store;

        public StorageTransformer(
            StreamsProperties properties,
            ForeachAction<? super String, ? super Str.Data> dispatcher,
            ArtifactTypeUtilProviderFactory factory
        ) {
            this.properties = properties;
            this.dispatcher = dispatcher;
            this.factory = factory;
        }

        @Override
        public void init(ProcessorContext context) {
            this.context = context;
            //noinspection unchecked
            store = (KeyValueStore<String, Str.Data>) context.getStateStore(properties.getStorageStoreName());
        }

        @Override
        public KeyValue<Long, Str.TupleValue> transform(String artifactId, Str.StorageValue value) {
            Str.Data data = store.get(artifactId);
            if (data == null) {
                // initial value can be "empty" default
                data = Str.Data.getDefaultInstance();
            }

            long offset = context.offset();
            // should be unique enough
            long globalId = properties.toGlobalId(offset, context.partition());

            data = apply(artifactId, value, data, globalId, offset);
            if (data != null) {
                store.put(artifactId, data);
                // dispatch
                dispatcher.apply(artifactId, data);
            } else {
                store.delete(artifactId);
            }

            Str.ActionType action = value.getType();
            switch (action) {
                case CREATE:
                case UPDATE:
                    return new KeyValue<>(globalId, Str.TupleValue.newBuilder()
                                                                  .setArtifactId(artifactId)
                                                                  .setVersion(data.getArtifactsCount())
                                                                  .build());
                case DELETE:
                    return new KeyValue<>(globalId, null); // null value means delete entry
                default:
                    return null; // just skip?
            }
        }

        @Override
        public void close() {
        }

        // handle StorageValue to build appropriate Data representation
        private Str.Data apply(String artifactId, Str.StorageValue value, Str.Data aggregate, long globalId, long offset) {
            Str.ActionType type = value.getType();
            long version = value.getVersion();

            Str.ValueType vt = value.getVt();
            switch (vt) {
                case ARTIFACT:
                    return consumeArtifact(aggregate, value, type, artifactId, version, globalId, offset);
                case METADATA:
                    return consumeMetaData(aggregate, value, type, artifactId, version, offset);
                case RULE:
                    return consumeRule(aggregate, value, type, offset);
                case STATE:
                    return consumeState(aggregate, value, artifactId, version, offset);
                default:
                    throw new IllegalArgumentException("Cannot handle value type: " + vt);
            }
        }

        private Str.Data consumeRule(Str.Data data, Str.StorageValue rv, Str.ActionType type, long offset) {
            Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
            Str.RuleValue rule = rv.getRule();
            Str.RuleType rtv = rule.getType();
            RuleType ruleType = (rtv != null && rtv != Str.RuleType.__NONE) ? RuleType.valueOf(rtv.name()) : null;
            if (type == Str.ActionType.CREATE) {
                boolean found = builder.getRulesList().stream().anyMatch(r -> r.getType() == rtv);
                if (found) {
                    log.warn("Rule already exists: {}", ruleType);
                } else {
                    Str.RuleValue ruleValue = Str.RuleValue.newBuilder().setType(rtv).setConfiguration(rule.getConfiguration()).build();
                    builder.addRules(ruleValue);
                }
            } else if (type == Str.ActionType.UPDATE) {
                updateRule(rule, rtv, ruleType, builder);
            } else if (type == Str.ActionType.DELETE) {
                if (ruleType != null) {
                    int index = -1;
                    for (int i = 0; i < builder.getRulesCount(); i++) {
                        Str.RuleValue irv = builder.getRules(i);
                        if (irv.getType() == rtv) {
                            index = i;
                            break;
                        }
                    }
                    if (index >= 0) {
                        builder.removeRules(index);
                    }
                } else {
                    builder.clearRules();
                }
            }
            return builder.build();
        }

        private void updateRule(Str.RuleValue rule, Str.RuleType rtv, RuleType ruleType, Str.Data.Builder builder) {
            int i;
            Str.RuleValue found = null;
            for (i = 0; i < builder.getRulesCount(); i++) {
                if (builder.getRules(i).getType() == rtv) {
                    found = builder.getRules(i);
                    break;
                }
            }
            if (found == null) {
                log.warn("Rule not found: {}", ruleType);
                return;
            }
            builder.setRules(i, Str.RuleValue.newBuilder().setType(rtv).setConfiguration(rule.getConfiguration()).build());
        }

        private Str.Data consumeState(Str.Data data, Str.StorageValue rv, String artifactId, long version, long offset) {
            Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
            if (version > builder.getArtifactsCount()) {
                log.warn("Version not found: {} [{}]", version, artifactId);
            } else {
                int index = (int)(version >= 0 ? version : data.getArtifactsCount()) - 1;

                Str.ArtifactValue artifact = data.getArtifacts(index);
                ArtifactState currentState = ArtifactStateExt.getState(artifact.getMetadataMap());
                ArtifactState newState = ArtifactState.valueOf(rv.getState().name());

                Str.ArtifactValue.Builder ab = Str.ArtifactValue.newBuilder(artifact);

                if (ArtifactStateExt.canTransition(currentState, newState) == false) {
                    log.error(InvalidArtifactStateException.errorMsg(currentState, newState));
                } else {
                    ab.putMetadata(MetaDataKeys.STATE, newState.name());
                }

                builder.setArtifacts(index, ab.build());
            }
            return builder.build();
        }

        private Str.Data consumeMetaData(Str.Data data, Str.StorageValue rv, Str.ActionType type, String artifactId, long version, long offset) {
            Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
            Str.MetaDataValue metaData = rv.getMetadata();

            int count = builder.getArtifactsCount();
            if (version > count) {
                log.warn("Version not found: {} [{}]", version, artifactId);
            } else {
                Str.ArtifactValue av = null;

                int index;
                if (version > 0) {
                    index = (int)(version - 1);
                    av = builder.getArtifacts(index);
                    ArtifactState state = ArtifactStateExt.getState(av.getMetadataMap());
                    if (ArtifactStateExt.ACTIVE_STATES.contains(state) == false) {
                        log.warn(String.format("Not an active artifact, cannot modify metadata: %s [%s]", artifactId, version));
                        av = null;
                    }
                } else {
                    for (index = count - 1; index >= 0; index--) {
                        av = builder.getArtifacts(index);
                        ArtifactState state = ArtifactStateExt.getState(av.getMetadataMap());
                        if (ArtifactStateExt.ACTIVE_STATES.contains(state)) {
                            break;
                        }
                    }
                    if (index < 0) {
                        av = null; // not found
                    }
                }

                if (av != null) {
                    Str.ArtifactValue.Builder avb = Str.ArtifactValue.newBuilder(av);

                    if (type == Str.ActionType.UPDATE) {
                        avb.putMetadata(MetaDataKeys.NAME, metaData.getName());
                        avb.putMetadata(MetaDataKeys.DESCRIPTION, metaData.getDescription());
                    } else if (type == Str.ActionType.DELETE) {
                        avb.removeMetadata(MetaDataKeys.NAME);
                        avb.removeMetadata(MetaDataKeys.DESCRIPTION);
                    }
                    builder.setArtifacts(index, avb.build()); // override with new value
                }
            }
            return builder.build();
        }

        private Str.Data consumeArtifact(Str.Data data, Str.StorageValue rv, Str.ActionType type, String artifactId, long version, long globalId, long offset) {
            Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
            Str.ArtifactValue artifact = rv.getArtifact();
            if (type == Str.ActionType.CREATE || type == Str.ActionType.UPDATE) {
                createOrUpdateArtifact(builder, artifactId, globalId, artifact, type == Str.ActionType.CREATE);
            } else if (type == Str.ActionType.DELETE) {
                if (version >= 0) {
                    if (version > builder.getArtifactsCount()) {
                        log.warn("Version not found: {} [{}]", version, artifactId);
                    } else {
                        // set default as deleted
                        builder.setArtifacts((int) (version - 1), Str.ArtifactValue.getDefaultInstance());
                    }
                } else {
                    return null; // this will remove artifacts from the store
                }
            }
            return builder.build();
        }

        private void createOrUpdateArtifact(Str.Data.Builder builder, String artifactId, long globalId, Str.ArtifactValue artifact, boolean create) {
            builder.setArtifactId(artifactId);

            int count = builder.getArtifactsCount();
            if (create && count > 0) {
                log.warn("Artifact already exists: {}", artifactId);
                return;
            }
            if (!create && count == 0) {
                log.warn("Artifact not found: {}", artifactId);
                return;
            }

            Str.ArtifactValue.Builder avb = Str.ArtifactValue.newBuilder(artifact);
            avb.setId(globalId);

            // +1 on version
            int version = builder.getArtifactsCount() + 1;

            ArtifactType type = ArtifactType.values()[artifact.getArtifactType()];

            Map<String, String> contents = new HashMap<>();
            contents.put(MetaDataKeys.ARTIFACT_ID, artifactId);
            contents.put(MetaDataKeys.GLOBAL_ID, String.valueOf(globalId));
            contents.put(MetaDataKeys.VERSION, String.valueOf(version));
            contents.put(MetaDataKeys.TYPE, type.value());

            // TODO not yet properly handling createdOn vs. modifiedOn for multiple versions
            String currentTimeMillis = String.valueOf(System.currentTimeMillis());
            contents.put(MetaDataKeys.CREATED_ON, currentTimeMillis);
            contents.put(MetaDataKeys.MODIFIED_ON, currentTimeMillis);

            //        contents.put(MetaDataKeys.NAME, null);
            //        contents.put(MetaDataKeys.DESCRIPTION, null);
            // TODO -- createdBy, modifiedBy
            contents.put(MetaDataKeys.STATE, ArtifactState.ENABLED.name());

            if (!create) {
                Str.ArtifactValue previous = builder.getArtifacts(count - 1); // last one
                Map<String, String> prevContents = previous.getMetadataMap();
                if (prevContents != null) {
                    checkNull(artifactId, version, contents, MetaDataKeys.CREATED_ON, prevContents.get(MetaDataKeys.CREATED_ON));
                    if (prevContents.containsKey(MetaDataKeys.NAME)) {
                        checkNull(artifactId, version, contents, MetaDataKeys.NAME, prevContents.get(MetaDataKeys.NAME));
                    }
                    if (prevContents.containsKey(MetaDataKeys.DESCRIPTION)) {
                        checkNull(artifactId, version, contents, MetaDataKeys.DESCRIPTION, prevContents.get(MetaDataKeys.DESCRIPTION));
                    }
                }
            }

            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(type);
            ContentExtractor extractor = provider.getContentExtractor();
            EditableMetaData emd = extractor.extract(ContentHandle.create(artifact.getContent().toByteArray()));
            if (extractor.isExtracted(emd)) {
                if (!isEmpty(emd.getName())) {
                    checkNull(artifactId, version, contents, MetaDataKeys.NAME, emd.getName());
                }
                if (!isEmpty(emd.getDescription())) {
                    checkNull(artifactId, version, contents, MetaDataKeys.DESCRIPTION, emd.getDescription());
                }
            }

            avb.putAllMetadata(contents);

            builder.addArtifacts(avb);
        }

        private static void checkNull(String artifactId, int version, Map<String, String> contents, String key, String value) {
            if (key != null && value != null) {
                contents.put(key, value);
            } else {
                log.warn("Metadata - null key {} or value {} - [{} ({})]", key, value, artifactId, version);
            }
        }

    }
}
