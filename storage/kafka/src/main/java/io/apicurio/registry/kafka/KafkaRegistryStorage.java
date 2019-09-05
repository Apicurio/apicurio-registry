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
import io.apicurio.registry.kafka.utils.ProtoUtil;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.SimpleMapRegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class KafkaRegistryStorage extends SimpleMapRegistryStorage implements KafkaRegistryStorageHandle {

    @Inject
    Function<ProducerRecord<Reg.UUID, Reg.SchemaValue>, CompletableFuture<RecordMetadata>> schemaProducer;

    private volatile long offset = 0;

    private final Map<UUID, CompletableFuture<Object>> cfMap = new ConcurrentHashMap<>();

    @Override
    protected long nextGlobalId() {
        return offset;
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> submit(Reg.ActionType actionType, String artifactId, ArtifactType artifactType, long version, String content) {
        UUID reqId = UUID.randomUUID();
        CompletableFuture<Object> cf = new CompletableFuture<>();
        cfMap.put(reqId, cf);
        return send(reqId, actionType, artifactId, artifactType, version, content)
            .whenComplete((r, x) -> {
                if (x != null) cfMap.remove(reqId);
            })
            .thenCompose(r -> (CompletableFuture<T>) cf);
    }

    private CompletableFuture<?> send(UUID reqId, Reg.ActionType actionType, String artifactId, ArtifactType artifactType, long version, String content) {
        Reg.SchemaValue.Builder builder = Reg.SchemaValue.newBuilder()
                                                         .setType(actionType)
                                                         .setArtifactId(artifactId)
                                                         .setVersion(version);
        if (artifactType != null) {
            builder.setArtifactType(artifactType.ordinal());
        }
        if (content != null) {
            builder.setContent(ByteString.copyFrom(content, StandardCharsets.UTF_8));
        }
        ProducerRecord<Reg.UUID, Reg.SchemaValue> record = new ProducerRecord<>(
            KafkaRegistryConfiguration.SCHEMA_TOPIC,
            ProtoUtil.convert(reqId),
            builder.build()
        );
        return schemaProducer.apply(record);
    }

    public void consumeSchemaValue(ConsumerRecord<Reg.UUID, Reg.SchemaValue> record) {
        offset = record.offset();
        Reg.SchemaValue schemaValue = record.value();
        Reg.ActionType type = schemaValue.getType();
        CompletableFuture<Object> cf = cfMap.remove(ProtoUtil.convert(record.key()));
        if (cf == null) {
            cf = new CompletableFuture<>(); // handle the msg anyway
        }
        try {
            if (type == Reg.ActionType.CREATE || type == Reg.ActionType.UPDATE) {
                String content = schemaValue.getContent().toString(StandardCharsets.UTF_8);
                cf.complete(createOrUpdateArtifact(
                    schemaValue.getArtifactId(),
                    ArtifactType.values()[schemaValue.getArtifactType()],
                    content,
                    Reg.ActionType.CREATE == type,
                    offset));
            } else if (type == Reg.ActionType.DELETE) {
                long version = schemaValue.getVersion();
                if (version >= 0) {
                    super.deleteArtifactVersion(schemaValue.getArtifactId(), version);
                    cf.complete(Void.class); // just set something
                } else {
                    cf.complete(super.deleteArtifact(schemaValue.getArtifactId()));
                }
            }
        } catch (Throwable e) {
            cf.completeExceptionally(e);
        }
    }

    @Override
    public ArtifactMetaDataDto createArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return get(submit(Reg.ActionType.CREATE, artifactId, artifactType, 0, content));
    }

    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        //noinspection unchecked
        return get(submit(Reg.ActionType.DELETE, artifactId, null, -1, null));
    }

    @Override
    public ArtifactMetaDataDto updateArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactNotFoundException, RegistryStorageException {
        return get(submit(Reg.ActionType.UPDATE, artifactId, artifactType, 0, content));
    }

    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        get(submit(Reg.ActionType.DELETE, artifactId, null, version, null));
    }

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

}
