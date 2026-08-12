package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.time.Instant;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_CREATED;

public class ArtifactCreated extends OutboxEvent {
    private final ArtifactMetaDataDto data;

    private ArtifactCreated(String id, String aggregateId, ArtifactMetaDataDto data, Instant timestamp) {
        super(id, aggregateId, timestamp);
        this.data = data;
    }

    public static ArtifactCreated of(ArtifactMetaDataDto artifactMetaDataDto) {
        String id = UUID.randomUUID().toString();
        Instant timestamp = Instant.ofEpochMilli(artifactMetaDataDto.getCreatedOn());
        return new ArtifactCreated(id,
                artifactMetaDataDto.getGroupId() + "-" + artifactMetaDataDto.getArtifactId(),
                artifactMetaDataDto, timestamp);
    }

    @Override
    public String getType() {
        return ARTIFACT_CREATED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}