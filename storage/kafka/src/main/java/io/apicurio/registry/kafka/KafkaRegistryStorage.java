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

import io.apicurio.registry.common.proto.Cmmn;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.kafka.snapshot.StorageSnapshot;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
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
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.ProtoUtil;
import io.apicurio.registry.utils.kafka.ProducerActions;
import io.apicurio.registry.utils.kafka.Submitter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.utils.ConcurrentUtil.get;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
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
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
public class KafkaRegistryStorage extends SimpleMapRegistryStorage implements KafkaRegistryStorageHandle {

    private static final Logger log = LoggerFactory.getLogger(KafkaRegistryStorage.class);

    @ConfigProperty(name = "registry.kafka.snapshot.requests", defaultValue = "1000")
    long snapshotRequests;

    @ConfigProperty(name = "registry.kafka.snapshot.period.minutes", defaultValue = "1200") // 2 days
    long snapshotPeriod; // the time in minutes from the last time we did snapshot

    @ConfigProperty(name = "registry.kafka.snapshot.topic", defaultValue = "snapshot-topic")
    String snapshotTopic;

    @ConfigProperty(name = "registry.kafka.schedule.period.minutes", defaultValue = "1")
    long schedulePeriod; // schedule check period in minutes

    @ConfigProperty(name = "registry.kafka.storage.topic", defaultValue = "storage-topic")
    String storageTopic;

    @Inject
    ProducerActions<Cmmn.UUID, Str.StorageValue> storageProducer;

    @Inject
    ProducerActions<Long, StorageSnapshot> snapshotProducer;

    private volatile long offset = 0;

    private final Submitter submitter = new Submitter(this::submit);
    private final Map<UUID, TimedFuture<Object>> outstandingRequests = new ConcurrentHashMap<>();

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
        return storageTopic;
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
        Iterator<Map.Entry<UUID, TimedFuture<Object>>> iter = outstandingRequests.entrySet().iterator();
        while (iter.hasNext()) {
            TimedFuture tf = iter.next().getValue();
            // remove if older then the period we check
            if (now - tf.getTimestamp() > TimeUnit.MINUTES.toMillis(schedulePeriod)) {
                iter.remove();
            }
        }

        // force snapshot by sending a SNAPSHOT type msg
        // we should be fine, as there shouldn't be lots of such msgs
        // plus, we will only apply msg after this snapshot
        // if it fails, we'll just re-try with snapshot, so no harm
        if (lastSnapshotTime > 0 && now - lastSnapshotTime > TimeUnit.MINUTES.toMillis(snapshotPeriod)) {
            log.info("Forced snapshot: " + get(submitter.submitSnapshot(now)));
        }
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> submit(Str.StorageValue value) {
        UUID reqId = UUID.randomUUID();
        TimedFuture<Object> tf = new TimedFuture<>();
        outstandingRequests.put(reqId, tf);
        return send(reqId, value)
            .whenComplete((r, x) -> {
                if (x != null) outstandingRequests.remove(reqId);
            })
            .thenCompose(r -> (CompletableFuture<T>) tf);
    }

    private CompletableFuture<?> send(UUID reqId, Str.StorageValue value) {
        ProducerRecord<Cmmn.UUID, Str.StorageValue> record = new ProducerRecord<>(
            storageTopic,
            ProtoUtil.convert(reqId),
            value
        );
        return storageProducer.apply(record);
    }

    public void consumeStorageValue(ConsumerRecord<Cmmn.UUID, Str.StorageValue> record) {
        this.offset = record.offset();

        boolean isProducer = false;
        TimedFuture<Object> tf = outstandingRequests.remove(ProtoUtil.convert(record.key()));
        if (tf == null) {
            tf = new TimedFuture<>(); // it's a non-producer instance or handle it anyway (should not happen !?)
        } else {
            isProducer = true; // since we found the CF, this node produced the msg
        }

        Str.StorageValue rv = record.value();
        Str.ActionType type = rv.getType();

        String artifactId = ProtoUtil.getNullable(rv.getArtifactId(), s -> !s.isEmpty(), Function.identity());
        long version = rv.getVersion();
        Str.ValueType vt = rv.getVt();
        boolean forcedSnapshot = false;
        long timestamp = System.currentTimeMillis();

        try {
            switch (vt) {
                case ARTIFACT: {
                    consumeArtifact(tf, rv, type, artifactId, version);
                    break;
                }
                case METADATA: {
                    consumeMetaData(tf, rv, type, artifactId, version);
                    break;
                }
                case RULE: {
                    consumeRule(tf, rv, type, artifactId);
                    break;
                }
                case SNAPSHOT: {
                    Str.SnapshotValue sv = rv.getSnapshot();
                    // best try / effort ?
                    // if we fail, it will be inconsistent -- hence smart period value wrt retention ...
                    // but if we only set it in "whenComplete"
                    // then only producer node can have the actual value
                    lastSnapshotTime = sv.getTimestamp();
                    timestamp = lastSnapshotTime; // override timestamp
                    forcedSnapshot = true;
                    break;
                }
                case STATE: {
                    Str.ArtifactState state = rv.getState();
                    consumeState(tf, artifactId, version, state);
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
                    TimedFuture<Object> tmpTF = tf;
                    rcf.whenComplete((r, t) -> {
                        if (t != null) {
                            tmpTF.completeExceptionally(t);
                        } else {
                            tmpTF.complete(r);
                        }
                    });
                }
            }

        } catch (RegistryException e) {
            tf.completeExceptionally(e);
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

    private void consumeRule(CompletableFuture<Object> cf, Str.StorageValue rv, Str.ActionType type, String artifactId) {
        Str.RuleValue rule = rv.getRule();
        Str.RuleType rtv = rule.getType();
        RuleType ruleType = (rtv != null && rtv != Str.RuleType.__NONE) ? RuleType.valueOf(rtv.name()) : null;
        RuleConfigurationDto rcd = new RuleConfigurationDto(rule.getConfiguration());
        if (type == Str.ActionType.CREATE) {
            if (artifactId != null) {
                super.createArtifactRule(artifactId, ruleType, rcd);
            } else {
                super.createGlobalRule(ruleType, rcd);
            }
        } else if (type == Str.ActionType.UPDATE) {
            if (artifactId != null) {
                super.updateArtifactRule(artifactId, ruleType, rcd);
            } else {
                super.updateGlobalRule(ruleType, rcd);
            }
        } else if (type == Str.ActionType.DELETE) {
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

    private void consumeMetaData(CompletableFuture<Object> cf, Str.StorageValue rv, Str.ActionType type, String artifactId, long version) {
        Str.MetaDataValue metaData = rv.getMetadata();
        if (type == Str.ActionType.UPDATE) {
            EditableArtifactMetaDataDto emd = new EditableArtifactMetaDataDto(metaData.getName(), metaData.getDescription());
            if (version >= 0) {
                super.updateArtifactVersionMetaData(artifactId, version, emd);
            } else {
                super.updateArtifactMetaData(artifactId, emd);
            }
        } else if (type == Str.ActionType.DELETE) {
            super.deleteArtifactVersionMetaData(artifactId, version);
        }
        cf.complete(Void.class);
    }

    private void consumeArtifact(CompletableFuture<Object> cf, Str.StorageValue rv, Str.ActionType type, String artifactId, long version) {
        Str.ArtifactValue artifact = rv.getArtifact();
        if (type == Str.ActionType.CREATE || type == Str.ActionType.UPDATE) {
            byte[] content = artifact.getContent().toByteArray();
            cf.complete(createOrUpdateArtifact(
                artifactId,
                ArtifactType.values()[artifact.getArtifactType()],
                ContentHandle.create(content),
                Str.ActionType.CREATE == type,
                offset));
        } else if (type == Str.ActionType.DELETE) {
            if (version >= 0) {
                super.deleteArtifactVersion(artifactId, version);
                cf.complete(Void.class); // just set something
            } else {
                cf.complete(super.deleteArtifact(artifactId));
            }
        }
    }

    private void consumeState(CompletableFuture<Object> cf, String artifactId, long version, Str.ArtifactState state) {
        super.updateArtifactState(artifactId, ArtifactState.valueOf(state.name()), version > 0 ? (int) version : null);
        cf.complete(Void.class);
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) {
        get(submitter.submitState(artifactId, -1L, state));
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        get(submitter.submitState(artifactId, version.longValue(), state));
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return submitter.submitArtifact(Str.ActionType.CREATE, artifactId, 0, artifactType, content.bytes());
    }

    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return get(submitter.submitArtifact(Str.ActionType.DELETE, artifactId, -1, null, null));
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        return submitter.submitArtifact(Str.ActionType.UPDATE, artifactId, 0, artifactType, content.bytes());
    }

    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        get(submitter.submitArtifact(Str.ActionType.DELETE, artifactId, version, null, null));
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        get(submitter.submitMetadata(Str.ActionType.UPDATE, artifactId, -1, metaData.getName(), metaData.getDescription()));
    }

    @Override
    public void createArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        get(submitter.submitRule(Str.ActionType.CREATE, artifactId, rule, config.getConfiguration()));
    }

    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        get(submitter.submitRule(Str.ActionType.DELETE, artifactId, null, null));
    }

    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        get(submitter.submitRule(Str.ActionType.UPDATE, artifactId, rule, config.getConfiguration()));
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        get(submitter.submitRule(Str.ActionType.DELETE, artifactId, rule, null));
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        get(submitter.submitMetadata(Str.ActionType.UPDATE, artifactId, version, metaData.getName(), metaData.getDescription()));
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        get(submitter.submitMetadata(Str.ActionType.DELETE, artifactId, version, null, null));
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        get(submitter.submitRule(Str.ActionType.CREATE, null, rule, config.getConfiguration()));
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        get(submitter.submitRule(Str.ActionType.DELETE, null, null, null));
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        get(submitter.submitRule(Str.ActionType.UPDATE, null, rule, config.getConfiguration()));
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        get(submitter.submitRule(Str.ActionType.DELETE, null, rule, null));
    }

    // ----

    private static class TimedFuture<T> extends CompletableFuture<T> {
        private final long timestamp;

        public TimedFuture() {
            timestamp = System.currentTimeMillis();
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
