package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_CREATED;

public class ArtifactVersionCreated extends OutboxEvent {
    private final ArtifactVersionMetaDataDto data;

    private ArtifactVersionCreated(String id, String aggregateId, ArtifactVersionMetaDataDto data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static ArtifactVersionCreated of(ArtifactVersionMetaDataDto versionMetaDataDto) {
        String id = UUID.randomUUID().toString();
        return new ArtifactVersionCreated(id, versionMetaDataDto.getGroupId() + "-"
                + versionMetaDataDto.getArtifactId() + "-" + versionMetaDataDto.getVersion(),
                versionMetaDataDto);
    }

    @Override
    public String getType() {
        return ARTIFACT_VERSION_CREATED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}