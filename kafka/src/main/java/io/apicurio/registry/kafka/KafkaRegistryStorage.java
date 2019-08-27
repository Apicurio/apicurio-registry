package io.apicurio.registry.kafka;

import com.google.protobuf.ByteString;
import io.apicurio.registry.kafka.proto.Reg;
import io.apicurio.registry.rest.beans.ArtifactType;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.impl.SimpleMapRegistryStorage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.nio.charset.StandardCharsets;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class KafkaRegistryStorage extends SimpleMapRegistryStorage {

    @Inject
    Function<ProducerRecord<String, Reg.SchemaValue>, CompletableFuture<RecordMetadata>> schemaProducer;

    private volatile long offset = 0;

    @Override
    protected long nextGlobalId() {
        return offset;
    }

    private long submit(Reg.ActionType actionType, String artifactId, ArtifactType artifactType, String content) {
        try {
            Reg.SchemaValue.Builder builder = Reg.SchemaValue.newBuilder()
                                                             .setType(actionType)
                                                             .setArtifactId(artifactId);
            if (artifactType != null) {
                builder.setArtifactType(artifactType.ordinal());
            }
            if (content != null) {
                builder.setContent(ByteString.copyFrom(content, StandardCharsets.UTF_8));
            }
            ProducerRecord<String, Reg.SchemaValue> record = new ProducerRecord<>(
                KafkaProducerConfiguration.SCHEMA_TOPIC,
                artifactId,
                builder.build()
            );
            return schemaProducer.apply(record).get().offset();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    public void consumeSchemaValue(ConsumerRecord<String, Reg.SchemaValue> record) {
        offset = record.offset();
        Reg.SchemaValue schemaValue = record.value();
        Reg.ActionType type = schemaValue.getType();
        if (type == Reg.ActionType.CREATE || type == Reg.ActionType.UPDATE) {
            String content = schemaValue.getContent().toString(StandardCharsets.UTF_8);
            createOrUpdateArtifact(
                record.key(),
                ArtifactType.values()[schemaValue.getArtifactType()],
                content,
                Reg.ActionType.CREATE == type,
                offset);
        } else if (type == Reg.ActionType.DELETE) {
            deleteArtifact(schemaValue.getArtifactId());
        }
    }

    // loop until we get currently produced msg
    private ArtifactMetaDataDto latest(long offset, String artifactId) {
        while (true) {
            ArtifactMetaDataDto dto = getArtifactMetaData(artifactId);
            if (dto.getGlobalId() == offset) {
                return dto;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public ArtifactMetaDataDto createArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        long offset = submit(Reg.ActionType.CREATE, artifactId, artifactType, content);
        return latest(offset, artifactId);
    }

    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        SortedSet<Long> ids = super.deleteArtifact(artifactId);
        submit(Reg.ActionType.DELETE, artifactId, null, null);
        return ids;
    }

    @Override
    public ArtifactMetaDataDto updateArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactNotFoundException, RegistryStorageException {
        long offset = submit(Reg.ActionType.UPDATE, artifactId, artifactType, content);
        return latest(offset, artifactId);
    }
}
