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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.InvalidArtifactStateException;
import io.apicurio.registry.storage.InvalidPropertiesException;
import io.apicurio.registry.storage.impl.MetaDataKeys;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.streams.StreamsProperties;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class StorageTransformer implements Transformer<Str.ArtifactKey, Str.StorageValue, KeyValue<Long, Str.TupleValue>> {
    private static final Logger log = LoggerFactory.getLogger(StorageTransformer.class);

    private final StreamsProperties properties;
    private final ForeachAction<? super Str.ArtifactKey, ? super Str.Data> dispatcher;

    private ProcessorContext context;
    private KeyValueStore<Str.ArtifactKey, Str.Data> dataStore;

    public StorageTransformer(
            StreamsProperties properties,
            ForeachAction<? super Str.ArtifactKey, ? super Str.Data> dispatcher
    ) {
        this.properties = properties;
        this.dispatcher = dispatcher;
    }

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
        //noinspection unchecked
        dataStore = (KeyValueStore<Str.ArtifactKey, Str.Data>) context.getStateStore(properties.getStorageStoreName());
    }

    @Override
    public KeyValue<Long, Str.TupleValue> transform(Str.ArtifactKey artifactKey, Str.StorageValue value) {
        Str.Data data = dataStore.get(artifactKey);
        if (data == null) {
            // initial value can be "empty" default
            data = Str.Data.getDefaultInstance();
        }

        long offset = context.offset();
        // should be unique enough
        long globalId = properties.toGlobalId(offset, context.partition());

        data = apply(artifactKey, value, data, globalId, offset);
        if (data != null) {
            dataStore.put(artifactKey, data);
            // dispatch
            dispatcher.apply(artifactKey, data);
        } else {
            dataStore.delete(artifactKey);
        }

        Str.ActionType action = value.getType();
        switch (action) {
            case CREATE:
            case UPDATE:
                return new KeyValue<>(globalId, Str.TupleValue.newBuilder()
                        .setKey(artifactKey)
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
    private Str.Data apply(Str.ArtifactKey key, Str.StorageValue value, Str.Data aggregate, long globalId, long offset) {
        Str.ActionType type = value.getType();
        long version = value.getVersion();

        Str.ValueType vt = value.getVt();
        switch (vt) {
            case ARTIFACT_DATA:
                return consumeArtifact(aggregate, value, type, key, version, globalId, offset);
            case METADATA:
                return consumeMetaData(aggregate, value, type, key, version, offset);
            case RULE:
                return consumeRule(aggregate, value, type, offset);
            case STATE:
                return consumeState(aggregate, value, key, version, offset);
            case LOGCONFIG:
                return consumeLogConfig(aggregate, value, type, offset);
            case GROUP:
                return consumeGroupMetaData(aggregate, value, type, offset);
            default:
                throw new IllegalArgumentException("Cannot handle value type: " + vt);
        }
    }

    private Str.Data consumeGroupMetaData(Str.Data data, Str.StorageValue rv, Str.ActionType type, long offset) {
        Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
        Str.GroupMetaDataValue groupMeta = rv.getGroup();
        if (type == Str.ActionType.CREATE || type == Str.ActionType.UPDATE) {
            int i;
            Str.GroupMetaDataValue found = null;
            for (i = 0; i < builder.getGroupsCount(); i++) {
                if (builder.getGroups(i).getGroupId().equals(groupMeta.getGroupId())) {
                    found = builder.getGroups(i);
                    break;
                }
            }
            if (found == null) {
                builder.addGroups(Str.GroupMetaDataValue.newBuilder(groupMeta).build());
            } else {
                Str.GroupMetaDataValue.Builder newMeta = Str.GroupMetaDataValue.newBuilder(groupMeta);
                newMeta.setCreatedBy(found.getCreatedBy());
                newMeta.setCreatedOn(found.getCreatedOn());
                builder.addGroups(i, newMeta.build());
            }
        } else if (type == Str.ActionType.DELETE) {
            int index = -1;
            for (int i = 0; i < builder.getGroupsCount(); i++) {
                Str.GroupMetaDataValue g = builder.getGroups(i);
                if (g.getGroupId().equals(groupMeta.getGroupId())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                builder.removeGroups(index);
            }
        }
        return builder.build();
    }

    private Str.Data consumeLogConfig(Str.Data data, Str.StorageValue rv, Str.ActionType type, long offset) {
        Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
        Str.LogConfigValue logconfig = rv.getLogConfig();
        if (type == Str.ActionType.CREATE) {
            int i;
            Str.LogConfigValue found = null;
            for (i = 0; i < builder.getLogConfigsCount(); i++) {
                if (builder.getLogConfigs(i).getLogger().equals(logconfig.getLogger())) {
                    found = builder.getLogConfigs(i);
                    break;
                }
            }
            if (found == null) {
                builder.addLogConfigs(Str.LogConfigValue.newBuilder().setLogger(logconfig.getLogger()).setLogLevel(logconfig.getLogLevel()));
            } else {
                builder.setLogConfigs(i, Str.LogConfigValue.newBuilder().setLogger(logconfig.getLogger()).setLogLevel(logconfig.getLogLevel()));
            }
        } else if (type == Str.ActionType.DELETE) {
            int index = -1;
            for (int i = 0; i < builder.getLogConfigsCount(); i++) {
                Str.LogConfigValue lcv = builder.getLogConfigs(i);
                if (lcv.getLogger().equals(logconfig.getLogger())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                builder.removeLogConfigs(index);
            }
        }
        return builder.build();
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

    private Str.Data consumeState(Str.Data data, Str.StorageValue rv, Str.ArtifactKey key, long version, long offset) {
        Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
        if (version > builder.getArtifactsCount()) {
            log.warn("Version not found: {} [{}]", version, key);
        } else {
            int index = (int) (version >= 0 ? version : data.getArtifactsCount()) - 1;

            Str.ArtifactData artifact = data.getArtifacts(index);
            ArtifactState currentState = ArtifactStateExt.getState(artifact.getMetadataMap());
            ArtifactState newState = ArtifactState.valueOf(rv.getState().name());

            Str.ArtifactData.Builder ab = Str.ArtifactData.newBuilder(artifact);

            if (ArtifactStateExt.canTransition(currentState, newState) == false) {
                log.error(InvalidArtifactStateException.errorMsg(currentState, newState));
            } else {
                ab.putMetadata(MetaDataKeys.STATE, newState.name());
            }

            builder.setArtifacts(index, ab.build());
        }
        return builder.build();
    }

    private Str.Data consumeMetaData(Str.Data data, Str.StorageValue rv, Str.ActionType type, Str.ArtifactKey key, long version, long offset) {
        Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
        Str.MetaDataValue metaData = rv.getMetadata();

        int count = builder.getArtifactsCount();
        if (version > count) {
            log.warn("Version not found: {} [{}]", version, key);
        } else {
            Str.ArtifactData av = null;

            int index;
            if (version > 0) {
                index = (int) (version - 1);
                av = builder.getArtifacts(index);
                ArtifactState state = ArtifactStateExt.getState(av.getMetadataMap());
                if (ArtifactStateExt.ACTIVE_STATES.contains(state) == false) {
                    log.warn(String.format("Not an active artifact, cannot modify metadata: %s [%s]", key, version));
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
                Str.ArtifactData.Builder avb = Str.ArtifactData.newBuilder(av);

                if (type == Str.ActionType.UPDATE) {
                    avb.putMetadata(MetaDataKeys.NAME, metaData.getName());
                    avb.putMetadata(MetaDataKeys.DESCRIPTION, metaData.getDescription());
                    avb.putMetadata(MetaDataKeys.LABELS, metaData.getLabels());
                    try {
                        avb.putMetadata(MetaDataKeys.PROPERTIES, new ObjectMapper().writeValueAsString(metaData.getPropertiesMap()));
                    } catch (JsonProcessingException e) {
                        throw new InvalidPropertiesException(MetaDataKeys.PROPERTIES + " could not be processed for artifactStore.", e);
                    }
                } else if (type == Str.ActionType.DELETE) {
                    avb.removeMetadata(MetaDataKeys.NAME);
                    avb.removeMetadata(MetaDataKeys.DESCRIPTION);
                    avb.removeMetadata(MetaDataKeys.LABELS);
                    avb.removeMetadata(MetaDataKeys.PROPERTIES);
                }
                builder.setArtifacts(index, avb.build()); // override with new value
            }
        }
        return builder.build();
    }

    private Str.Data consumeArtifact(Str.Data data, Str.StorageValue rv, Str.ActionType type, Str.ArtifactKey key, long version, long globalId, long offset) {
        Str.Data.Builder builder = Str.Data.newBuilder(data).setLastProcessedOffset(offset);
        Str.ArtifactData artifact = rv.getArtifactData();
        if (type == Str.ActionType.CREATE || type == Str.ActionType.UPDATE) {
            createOrUpdateArtifact(builder, key, globalId, artifact, type == Str.ActionType.CREATE);
        } else if (type == Str.ActionType.DELETE) {
            if (version >= 0) {
                if (version > builder.getArtifactsCount()) {
                    log.warn("Version not found: {} [{}]", version, key);
                } else {
                    // set default as deleted
                    builder.setArtifacts((int) (version - 1), Str.ArtifactData.getDefaultInstance());
                }
            } else {
                return null; // this will remove artifacts from the store
            }
        }
        return builder.build();
    }

    private void createOrUpdateArtifact(Str.Data.Builder builder, Str.ArtifactKey key, long globalId, Str.ArtifactData artifact, boolean create) {
        builder.setKey(key);

        int count = builder.getArtifactsCount();
        if (create && count > 0) {
            log.warn("Artifact already exists: {}", key);
            return;
        }
        if (!create && count == 0) {
            log.warn("Artifact not found: {}", key);
            return;
        }

        ArtifactType type = ArtifactType.values()[artifact.getArtifactType()];

        Str.ArtifactData.Builder avb = Str.ArtifactData.newBuilder(artifact);
        avb.setId(globalId);
        avb.setContentId(artifact.getContentId());

        // +1 on version
        int version = builder.getArtifactsCount() + 1;

        Map<String, String> contents = new HashMap<>();
        contents.put(MetaDataKeys.GROUP_ID, key.getGroupId());
        contents.put(MetaDataKeys.ARTIFACT_ID, key.getArtifactId());
        contents.put(MetaDataKeys.GLOBAL_ID, String.valueOf(globalId));
        contents.put(MetaDataKeys.VERSION, String.valueOf(version));
        contents.put(MetaDataKeys.TYPE, type.value());

        final String name = artifact.getMetadataOrDefault(MetaDataKeys.NAME, null);
        final String description = artifact.getMetadataOrDefault(MetaDataKeys.DESCRIPTION, null);

        if (name != null) {
            contents.put(MetaDataKeys.NAME, name);
        }
        if (description != null) {
            contents.put(MetaDataKeys.DESCRIPTION, description);
        }

        String currentTimeMillis = String.valueOf(System.currentTimeMillis());
        contents.put(MetaDataKeys.CREATED_ON, currentTimeMillis);
        contents.put(MetaDataKeys.MODIFIED_ON, currentTimeMillis);
        contents.put(MetaDataKeys.CREATED_BY, artifact.getMetadataOrDefault(MetaDataKeys.CREATED_BY, ""));
        // TODO -- modifiedBy

        contents.put(MetaDataKeys.STATE, ArtifactState.ENABLED.name());

        avb.putAllMetadata(contents);
        builder.addArtifacts(avb);
    }
}