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

package io.apicurio.registry.kafka;

import com.google.protobuf.ByteString;
import io.apicurio.registry.kafka.proto.Reg;
import io.apicurio.registry.kafka.snapshot.StorageSnapshot;
import io.apicurio.registry.kafka.utils.ProducerActions;
import io.apicurio.registry.kafka.utils.ProtoUtil;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.SimpleMapRegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class KafkaRegistryStorage extends SimpleMapRegistryStorage implements KafkaRegistryStorageHandle {

    private static final Logger log = LoggerFactory.getLogger(KafkaRegistryStorage.class);

    @ConfigProperty(name = "registry.kafka.snapshot.requests", defaultValue = "1000")
    long snapshotRequests;

    @ConfigProperty(name = "registry.kafka.snapshot.period", defaultValue = "1200") // 2 days
    long snapshotPeriod; // the time in minutes from the last time we did snapshot

    @ConfigProperty(name = "registry.kafka.snapshot.topic", defaultValue = "snapshot-topic")
    String snapshotTopic;

    @ConfigProperty(name = "registry.kafka.schedule.period", defaultValue = "1")
    long schedulePeriod; // schedule check period in minutes

    @ConfigProperty(name = "registry.kafka.registry.topic", defaultValue = "registry-topic")
    String registryTopic;

    @Inject
    ProducerActions<Reg.UUID, Reg.RegistryValue> registryProducer;

    @Inject
    ProducerActions<Long, StorageSnapshot> snapshotProducer;

    private volatile long offset = 0;

    private final Map<UUID, CF<Object>> cfMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService executor;
    private volatile long lastSnapshotTime;

    @Override
    protected long nextGlobalId() {
        return offset;
    }

    @Override
    protected void afterInit() {
        log.info("Autosnapshot on every {} requests, period: {}, scheduled check: {}",
                 snapshotRequests, snapshotPeriod, schedulePeriod);
    }

    @Override
    public String registryTopic() {
        return registryTopic;
    }

    @Override
    public String snapshotTopic() {
        return snapshotTopic;
    }

    @Override
    public void loadSnapshot(StorageSnapshot snapshot) {
        storage.putAll(snapshot.getStorage());
        global.putAll(snapshot.getGlobal());
        artifactRules.putAll(snapshot.getArtifactRules());
        globalRules.putAll(snapshot.getGlobalRules());
    }

    @Override
    public void start() {
        executor = new ScheduledThreadPoolExecutor(1);
        // random delay, so not all nodes start checking at the same time
        long delay = ThreadLocalRandom.current().nextLong(schedulePeriod * 60);
        executor.scheduleAtFixedRate(this::check, delay, schedulePeriod, TimeUnit.MINUTES);
    }

    private void check() {
        long now = System.currentTimeMillis();

        // first check for any stale CFs -- should not be many
        Iterator<Map.Entry<UUID, CF<Object>>> iter = cfMap.entrySet().iterator();
        while (iter.hasNext()) {
            CF cf = iter.next().getValue();
            // remove if older then the period we check
            if (now - cf.getTimestamp() > TimeUnit.MINUTES.toMillis(schedulePeriod)) {
                iter.remove();
            }
        }

        // force snapshot by sending a SNAPSHOT type msg
        // we should be fine, as there shouldn't be lots of such msgs
        // plus, we will only apply msg after this snapshot
        // if it fails, we'll just re-try with snapshot, so no harm
        if (now - lastSnapshotTime > TimeUnit.MINUTES.toMillis(snapshotPeriod)) {
            log.info("Forced snapshot: " + get(submitSnapshot(now)));
        }
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    private Reg.RegistryValue.Builder getRVBuilder(Reg.ValueType vt, Reg.ActionType actionType, String artifactId, long version) {
        Reg.RegistryValue.Builder builder = Reg.RegistryValue.newBuilder()
                                                             .setVt(vt)
                                                             .setType(actionType)
                                                             .setVersion(version);

        if (artifactId != null) {
            builder.setArtifactId(artifactId);
        }
        return builder;
    }

    private <T> CompletableFuture<T> submitArtifact(Reg.ActionType actionType, String artifactId, long version, ArtifactType artifactType, String content) {
        Reg.ArtifactValue.Builder builder = Reg.ArtifactValue.newBuilder();
        if (artifactType != null) {
            builder.setArtifactType(artifactType.ordinal());
        }
        if (content != null) {
            builder.setContent(ByteString.copyFrom(content, StandardCharsets.UTF_8));
        }

        Reg.RegistryValue.Builder rvb = getRVBuilder(Reg.ValueType.ARTIFACT, actionType, artifactId, version).setArtifact(builder);
        return submit(rvb.build());
    }

    private <T> CompletableFuture<T> submitMetadata(Reg.ActionType actionType, String artifactId, long version, String name, String description) {
        Reg.MetaDataValue.Builder builder = Reg.MetaDataValue.newBuilder();
        if (name != null) {
            builder.setName(name);
        }
        if (description != null) {
            builder.setDescription(description);
        }

        Reg.RegistryValue.Builder rvb = getRVBuilder(Reg.ValueType.METADATA, actionType, artifactId, version).setMetadata(builder);
        return submit(rvb.build());
    }

    private <T> CompletableFuture<T> submitRule(Reg.ActionType actionType, String artifactId, RuleType type, String configuration) {
        Reg.RuleValue.Builder builder = Reg.RuleValue.newBuilder();
        if (type != null) {
            builder.setType(Reg.RuleValue.Type.valueOf(type.name()));
        }
        if (configuration != null) {
            builder.setConfiguration(configuration);
        }

        Reg.RegistryValue.Builder rvb = getRVBuilder(Reg.ValueType.RULE, actionType, artifactId, -1).setRule(builder);
        return submit(rvb.build());
    }

    private <T> CompletableFuture<T> submitSnapshot(long timestamp) {
        Reg.SnapshotValue.Builder builder = Reg.SnapshotValue.newBuilder().setTimestamp(timestamp);
        Reg.RegistryValue.Builder rvb = getRVBuilder(Reg.ValueType.SNAPSHOT, Reg.ActionType.CREATE, null, -1).setSnapshot(builder);
        return submit(rvb.build());
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> submit(Reg.RegistryValue value) {
        UUID reqId = UUID.randomUUID();
        CF<Object> cf = new CF<>();
        cfMap.put(reqId, cf);
        return send(reqId, value)
            .whenComplete((r, x) -> {
                if (x != null) cfMap.remove(reqId);
            })
            .thenCompose(r -> (CompletableFuture<T>) cf);
    }

    private CompletableFuture<?> send(UUID reqId, Reg.RegistryValue value) {
        ProducerRecord<Reg.UUID, Reg.RegistryValue> record = new ProducerRecord<>(
            registryTopic,
            ProtoUtil.convert(reqId),
            value
        );
        return registryProducer.apply(record);
    }

    public void consumeRegistryValue(ConsumerRecord<Reg.UUID, Reg.RegistryValue> record) {
        this.offset = record.offset();

        boolean isProducer = false;
        CF<Object> cf = cfMap.remove(ProtoUtil.convert(record.key()));
        if (cf == null) {
            cf = new CF<>(); // it's a non-producer instance or handle it anyway (should not happen !?)
        } else {
            isProducer = true; // since we found the CF, this node produced the msg
        }

        Reg.RegistryValue rv = record.value();
        Reg.ActionType type = rv.getType();

        String artifactId = ProtoUtil.getNullable(rv.getArtifactId(), s -> !s.isEmpty(), Function.identity());
        long version = rv.getVersion();
        Reg.ValueType vt = rv.getVt();
        boolean forcedSnapshot = false;
        long timestamp = System.currentTimeMillis();

        try {
            switch (vt) {
                case ARTIFACT: {
                    consumeArtifact(cf, rv, type, artifactId, version);
                    break;
                }
                case METADATA: {
                    consumeMetaData(cf, rv, type, artifactId, version);
                    break;
                }
                case RULE: {
                    consumeRule(cf, rv, type, artifactId);
                    break;
                }
                case SNAPSHOT: {
                    Reg.SnapshotValue sv = rv.getSnapshot();
                    // best try / effort ?
                    // if we fail, it will be inconsistent -- hence smart period value wrt retention ...
                    // but if we only set it in "whenComplete"
                    // then only producer node can have the actual value
                    lastSnapshotTime = sv.getTimestamp();
                    timestamp = lastSnapshotTime; // override timestamp
                    forcedSnapshot = true;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("No such ValueType: " + vt);
                }
            }

            // only handle things on producer, so multiple nodes don't write same snapshots
            if (isProducer && (forcedSnapshot || (offset > 0 && (offset % snapshotRequests) == 0))) {
                CompletableFuture<RecordMetadata> rcf = makeSnapshot(timestamp);
                if (forcedSnapshot) {
                    cf.complete(get(rcf));
                }
            }

        } catch (RegistryException e) {
            cf.completeExceptionally(e);
        }
        // all other non-project / non-programmatic exceptions are unexpected, retry?
    }

    private CompletableFuture<RecordMetadata> makeSnapshot(long timestamp) {
        return snapshotProducer.apply(new ProducerRecord<>(
            snapshotTopic,
            timestamp,
            new StorageSnapshot(storage, global, artifactRules, globalRules, offset)
        )).whenComplete((recordMeta, exception) -> {
            if (exception != null) {
                log.warn("Exception dumping automatic snapshot: ", exception);
            } else {
                log.info("Dumped automatic snapshot to {} ({} bytes)", recordMeta, recordMeta.serializedValueSize());
            }
        });
    }

    private void consumeRule(CompletableFuture<Object> cf, Reg.RegistryValue rv, Reg.ActionType type, String artifactId) {
        Reg.RuleValue rule = rv.getRule();
        Reg.RuleValue.Type rtv = rule.getType();
        RuleType ruleType = (rtv != null && rtv != Reg.RuleValue.Type.__NONE) ? RuleType.valueOf(rtv.name()) : null;
        RuleConfigurationDto rcd = new RuleConfigurationDto(rule.getConfiguration());
        if (type == Reg.ActionType.CREATE) {
            if (artifactId != null) {
                super.createArtifactRule(artifactId, ruleType, rcd);
            } else {
                super.createGlobalRule(ruleType, rcd);
            }
        } else if (type == Reg.ActionType.UPDATE) {
            if (artifactId != null) {
                super.updateArtifactRule(artifactId, ruleType, rcd);
            } else {
                super.updateGlobalRule(ruleType, rcd);
            }
        } else if (type == Reg.ActionType.DELETE) {
            if (artifactId != null) {
                if (ruleType != null) {
                    super.deleteArtifactRule(artifactId, ruleType);
                } else {
                    super.deleteArtifactRules(artifactId);
                }
            } else {
                if (ruleType != null) {
                    super.deleteGlobalRule(ruleType);
                } else {
                    super.deleteGlobalRules();
                }
            }
        }
        cf.complete(Void.class);
    }

    private void consumeMetaData(CompletableFuture<Object> cf, Reg.RegistryValue rv, Reg.ActionType type, String artifactId, long version) {
        Reg.MetaDataValue metaData = rv.getMetadata();
        if (type == Reg.ActionType.UPDATE) {
            EditableArtifactMetaDataDto emd = new EditableArtifactMetaDataDto(metaData.getName(), metaData.getDescription());
            if (version >= 0) {
                super.updateArtifactVersionMetaData(artifactId, version, emd);
            } else {
                super.updateArtifactMetaData(artifactId, emd);
            }
        } else if (type == Reg.ActionType.DELETE) {
            super.deleteArtifactVersionMetaData(artifactId, version);
        }
        cf.complete(Void.class);
    }

    private void consumeArtifact(CompletableFuture<Object> cf, Reg.RegistryValue rv, Reg.ActionType type, String artifactId, long version) {
        Reg.ArtifactValue artifact = rv.getArtifact();
        if (type == Reg.ActionType.CREATE || type == Reg.ActionType.UPDATE) {
            String content = artifact.getContent().toString(StandardCharsets.UTF_8);
            cf.complete(createOrUpdateArtifact(
                artifactId,
                ArtifactType.values()[artifact.getArtifactType()],
                content,
                Reg.ActionType.CREATE == type,
                offset));
        } else if (type == Reg.ActionType.DELETE) {
            if (version >= 0) {
                super.deleteArtifactVersion(artifactId, version);
                cf.complete(Void.class); // just set something
            } else {
                cf.complete(super.deleteArtifact(artifactId));
            }
        }
    }

    @Override
    public ArtifactMetaDataDto createArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return get(submitArtifact(Reg.ActionType.CREATE, artifactId, 0, artifactType, content));
    }

    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return get(submitArtifact(Reg.ActionType.DELETE, artifactId, -1, null, null));
    }

    @Override
    public ArtifactMetaDataDto updateArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactNotFoundException, RegistryStorageException {
        return get(submitArtifact(Reg.ActionType.UPDATE, artifactId, 0, artifactType, content));
    }

    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        get(submitArtifact(Reg.ActionType.DELETE, artifactId, version, null, null));
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        get(submitMetadata(Reg.ActionType.UPDATE, artifactId, -1, metaData.getName(), metaData.getDescription()));
    }

    @Override
    public void createArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        get(submitRule(Reg.ActionType.CREATE, artifactId, rule, config.getConfiguration()));
    }

    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        get(submitRule(Reg.ActionType.DELETE, artifactId, null, null));
    }

    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        get(submitRule(Reg.ActionType.UPDATE, artifactId, rule, config.getConfiguration()));
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        get(submitRule(Reg.ActionType.DELETE, artifactId, rule, null));
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        get(submitMetadata(Reg.ActionType.UPDATE, artifactId, version, metaData.getName(), metaData.getDescription()));
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        get(submitMetadata(Reg.ActionType.DELETE, artifactId, version, null, null));
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        get(submitRule(Reg.ActionType.CREATE, null, rule, config.getConfiguration()));
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        get(submitRule(Reg.ActionType.DELETE, null, null, null));
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        get(submitRule(Reg.ActionType.UPDATE, null, rule, config.getConfiguration()));
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        get(submitRule(Reg.ActionType.DELETE, null, rule, null));
    }

    // ----

    private static <T> T get(CompletableFuture<T> cf) {
        boolean interrupted = false;
        while (true) {
            try {
                return cf.get();
            } catch (InterruptedException e) {
                interrupted = true;
            } catch (ExecutionException e) {
                Throwable t = e.getCause();
                if (t instanceof RuntimeException) throw (RuntimeException) t;
                if (t instanceof Error) throw (Error) t;
                throw new RuntimeException(t);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static class CF<T> extends CompletableFuture<T> {
        private final long timestamp;

        public CF() {
            timestamp = System.currentTimeMillis();
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
